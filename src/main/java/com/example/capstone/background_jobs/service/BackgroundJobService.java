package com.example.capstone.background_jobs.service;

import com.example.capstone.background_jobs.dto.CreateTicketRequestEvent;
import com.example.capstone.background_jobs.dto.NewScanRunbookEvent;
import com.example.capstone.background_jobs.dto.TransitionTicketRequestEvent;
import com.example.capstone.background_jobs.dto.UpdateAlertEvent;
import com.example.capstone.background_jobs.github.GithubApiClient;
import com.example.capstone.background_jobs.model.*;
import com.example.capstone.background_jobs.producer.AcknowledgementProducer;
import com.example.capstone.background_jobs.repository.RunbookConfigRepository;
import com.example.capstone.background_jobs.repository.RunbookRepository;
import com.example.capstone.background_jobs.repository.TenantRepository;
import com.example.capstone.background_jobs.repository.TenantTicketRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Base64;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class BackgroundJobService {

    private final TenantRepository tenantRepository;
    private final GithubApiClient githubApiClient;
    private final ElasticsearchClientService esClientService;
    private final AcknowledgementProducer ackProducer;
    private final TenantTicketRepository tenantTicketRepository;
    private final RunbookRepository runbookRepository;
    private final RunbookConfigRepository runbookConfigRepository;

    public BackgroundJobService(TenantRepository tenantRepository,
                                GithubApiClient githubApiClient,
                                ElasticsearchClientService esClientService,
                                AcknowledgementProducer ackProducer,
                                TenantTicketRepository tenantTicketRepository,
                                RunbookRepository runbookRepository,
                                RunbookConfigRepository runbookConfigRepository) {
        this.tenantRepository = tenantRepository;
        this.githubApiClient = githubApiClient;
        this.esClientService = esClientService;
        this.ackProducer = ackProducer;
        this.tenantTicketRepository = tenantTicketRepository;
        this.runbookRepository = runbookRepository;
        this.runbookConfigRepository = runbookConfigRepository;
    }

    public void handleUpdateFinding(UpdateAlertEvent event) {
        String jobId = event.getEventId();
        UpdateEvent payload = event.getPayload();

        try {
            Long tenantId = Long.valueOf(payload.getTenantId());
            TenantEntity tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new IllegalStateException("No tenant found for id=" + tenantId));

            // toolType is a string. Compare uppercase or switch statement
            String toolType = payload.getToolType().toUpperCase();

            String mappedDismissReason = mapDismissReason(toolType, payload.getReason(), payload.getNewState());

            boolean success;
            switch (toolType) {
                case "CODE_SCANNING":
                    success = githubApiClient.updateCodeScanningAlertState(
                            tenant.getPersonalAccessToken(),
                            tenant.getOwner(),
                            tenant.getRepo(),
                            String.valueOf(payload.getAlertNumber()),
                            payload.getNewState(),
                            mappedDismissReason
                    );
                    break;
                case "DEPENDABOT":
                    success = githubApiClient.updateDependabotAlertState(
                            tenant.getPersonalAccessToken(),
                            tenant.getOwner(),
                            tenant.getRepo(),
                            String.valueOf(payload.getAlertNumber()),
                            payload.getNewState(),
                            mappedDismissReason
                    );
                    break;
                case "SECRET_SCANNING":
                    success = githubApiClient.updateSecretScanningAlertState(
                            tenant.getPersonalAccessToken(),
                            tenant.getOwner(),
                            tenant.getRepo(),
                            String.valueOf(payload.getAlertNumber()),
                            payload.getNewState(),
                            mappedDismissReason
                    );
                    break;
                default:
                    success = false;
                    System.out.println("[background-jobs] Unknown toolType => " + toolType);
            }

            // If success => update ES
            if (success) {
                String rawState = payload.getNewState();
//                String dismissReason = payload.getReason();   // e.g. "false_positive"

                AlertStateBg mappedState = AlertStateBg.fromRaw(rawState, toolType, mappedDismissReason);

                esClientService.updateFindingInEs(
                        tenant.getEsIndex(),
                        payload.getAlertNumber(),
                        mappedState.name()  // e.g. "SUPPRESSED" or "FALSE_POSITIVE"
                );

                List<String> doneStates = Arrays.asList("FALSE_POSITIVE", "SUPPRESSED", "FIXED", "CONFIRM");
                if (doneStates.contains(mappedState.name())) {
                    // First, find the doc’s ID in ES so we know which row in tenant_ticket to use
                    Optional<String> findingIdOpt = esClientService.findFindingIdByAlertNumber(
                            tenant.getEsIndex(), payload.getAlertNumber(), toolType
                    );

                    if (findingIdOpt.isPresent()) {
                        String findingId = findingIdOpt.get();
                        Optional<TenantTicketEntity> tteOpt = tenantTicketRepository.findByFindingId(findingId);
                        if (tteOpt.isPresent()) {
                            TenantTicketEntity tte = tteOpt.get();

                            // Now build the event and call handleTransitionTicket
                            TransitionTicketRequestPayload ttPayload = new TransitionTicketRequestPayload();
                            ttPayload.setTicketId(tte.getTicketId());
                            ttPayload.setTenantId(tte.getTenantId().longValue());

                            TransitionTicketRequestEvent transitionEvent = new TransitionTicketRequestEvent(
                                    ttPayload,
                                    "transition_" + UUID.randomUUID(), // eventId
                                    "someDestinationTopic"             // optional
                            );

                            // Then do the transition
                            handleTransitionTicket(transitionEvent);
                        } else {
                            System.out.println("No tenant_ticket row found for findingId=" + findingId);
                        }
                    }
                }
            }

            // Acknowledge
            ackProducer.sendUpdateAck(jobId, success);

        } catch (Exception e) {
            e.printStackTrace();
            ackProducer.sendUpdateAck(jobId, false);
        }
    }

    private String mapDismissReason(String toolType, String requestedReason, String newState) {
        if (newState == null) return null;

        // If newState is neither "DISMISS" nor "RESOLVE", no reason needed:
        String upperState = newState.toUpperCase();
        if (!"DISMISS".equals(upperState) && !"RESOLVE".equals(upperState)) {
            return null;
        }

        // If user didn't provide a reason, default to something
        if (requestedReason == null || requestedReason.isBlank()) {
            requestedReason = "other";
        }

        switch (toolType) {
            case "DEPENDABOT":
                // Valid reasons per GH docs: fix_started, inaccurate, no_bandwidth,
                // not_used, tolerable_risk
                return switch (requestedReason.toLowerCase()) {
                    case "fix_started"     -> "fix_started";
                    case "inaccurate"      -> "inaccurate";
                    case "no_bandwidth"    -> "no_bandwidth";
                    case "not_used"        -> "not_used";
                    case "tolerable_risk"  -> "tolerable_risk";
                    default -> "not_used";  // fallback
                };

            case "CODE_SCANNING":
                // GitHub doc suggests exactly: "false positive", "won't fix", "used in tests"
                return switch (requestedReason.toLowerCase()) {
                    case "false positive", "false_positive" -> "false positive";
                    case "won't fix", "wont_fix"            -> "won't fix";
                    case "used in tests", "used_in_tests"   -> "used in tests";
                    default -> "won't fix"; // fallback
                };

            case "SECRET_SCANNING":
                // For SECRET_SCANNING, the "dismissed_reason" is actually "resolution" param
                // valid: "false_positive", "wont_fix", "revoked", "used_in_tests", etc.
                return switch (requestedReason.toLowerCase()) {
                    case "false_positive" -> "false_positive";
                    case "wont_fix", "won't_fix" -> "wont_fix";
                    case "revoked"        -> "revoked";
                    case "used_in_tests"  -> "used_in_tests";
                    default -> "wont_fix";
                };

            default:
                // fallback if unknown tool
                return "false_positive";
        }
    }

    public String handleCreateTicket(CreateTicketRequestEvent event) {

        String jobId = event.getEventId();

        CreateTicketRequestPayload payload = event.getPayload();

        Long tenantId = Long.valueOf(payload.getTenantId());
        String findingId = payload.getFindingId();
        String summary = payload.getSummary();
        String description = payload.getDescription();

        TenantEntity tenant = findTenantById(tenantId);

        // 2. Construct the Jira REST API URL
        // projectName typically something like "ishaanchadha.atlassian.net"
        String jiraUrl = "https://" + tenant.getProjectName() + "/rest/api/2/issue";

        // 3. Build the request body
        Map<String, Object> fields = new HashMap<>();
        Map<String, String> projectMap = new HashMap<>();
        projectMap.put("key", tenant.getProjectKey());

        Map<String, String> issueTypeMap = new HashMap<>();
        issueTypeMap.put("name", "Bug");

        fields.put("project", projectMap);
        fields.put("summary", summary);
        fields.put("description", description);
        fields.put("issuetype", issueTypeMap);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("fields", fields);

        // 4. Make the POST request
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = getAuthHeaders(tenant.getUsername(), tenant.getApiToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response =
                restTemplate.exchange(jiraUrl, HttpMethod.POST, entity, Map.class);

        // 5. Extract Jira ticket ID from the response
        // The JSON typically has "id" (internal numeric ID), "key" (Jira ticket key like CAP-123)
        // If you want the human-readable “key” (like “CAP-10”), parse that from "key".
        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null) {
            throw new IllegalStateException("Jira create issue response was empty");
        }

        // You can store either the "id" or "key". Typically, "key" is more human-friendly.
        // For consistency, let's store the "id". Adjust as desired.
        Object ticketKeyObj = responseBody.get("key");
        if (ticketKeyObj == null) {
            throw new IllegalStateException("No 'id' returned from Jira create issue response");
        }
        String ticketKey = ticketKeyObj.toString();

        // 6. Update the ES doc (the Findings record) with the new ticketId
        //    Make sure the "findingId" param is the actual ES document _id or that you have it mapped.
        esClientService.updateFindingTicketId(tenantId, findingId, ticketKey);

        // 7. Insert a record in tenant_ticket table
        TenantTicketEntity tenantTicketEntity = new TenantTicketEntity(
                tenantId.intValue(),  // or cast to int carefully
                ticketKey,
                findingId
        );
        tenantTicketRepository.save(tenantTicketEntity);

        ackProducer.sendUpdateAck(jobId, true);

        return ticketKey;
    }



    private TenantEntity findTenantById(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant not found with ID=" + tenantId));
    }

    private HttpHeaders getAuthHeaders(String username, String apiToken) {
        HttpHeaders headers = new HttpHeaders();
        String auth = username + ":" + apiToken;
        byte[] encodedAuth = Base64.encodeBase64(auth.getBytes());
        String authHeader = "Basic " + new String(encodedAuth);
        headers.set("Authorization", authHeader);
        return headers;
    }

    public void handleTransitionTicket(TransitionTicketRequestEvent event) {

        String jobId = event.getEventId();

        TransitionTicketRequestPayload payload = event.getPayload();

        Long tenantId = Long.valueOf(payload.getTenantId());
        String ticketKey = payload.getTicketId();

        TenantEntity tenant = findTenantById(tenantId);

        // Build the transitions URL for the current ticket
        String transitionsUrl = "https://" + tenant.getProjectName()
                + "/rest/api/2/issue/" + ticketKey
                + "/transitions?expand=transitions.fields";

        // Create a RestTemplate
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = getAuthHeaders(tenant.getUsername(), tenant.getApiToken());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        while (true) {
            // 1. Fetch the current transitions
            ResponseEntity<Map> response = restTemplate.exchange(
                    transitionsUrl, HttpMethod.GET, entity, Map.class
            );
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                // No transitions info => break out
                System.out.println("No transitions body returned for ticket=" + ticketKey);
                break;
            }

            // 2. Parse the transitions array
            List<Map<String, Object>> transitions = (List<Map<String, Object>>) responseBody.get("transitions");
            if (transitions == null || transitions.isEmpty()) {
                // No transitions => presumably at final state (like "Done")
                System.out.println("No more transitions available. Ticket " + ticketKey + " is likely at final state.");
                break;
            }

            // 3. Pick the first transition from the list
            Map<String, Object> firstTransition = transitions.get(0);
            String transitionId = (String) firstTransition.get("id");
            String transitionName = (String) firstTransition.get("name");
            System.out.println("Applying transition: " + transitionName + " (ID=" + transitionId + ") for ticket=" + ticketKey);

            // 4. POST that transition to move to the next state
            String doTransitionUrl = "https://" + tenant.getProjectName()
                    + "/rest/api/2/issue/" + ticketKey + "/transitions";

            Map<String, Object> transitionBody = new HashMap<>();
            Map<String, String> transitionObj = new HashMap<>();
            transitionObj.put("id", transitionId);
            transitionBody.put("transition", transitionObj);

            HttpEntity<Map<String, Object>> transitionEntity = new HttpEntity<>(transitionBody, headers);
            restTemplate.exchange(doTransitionUrl, HttpMethod.POST, transitionEntity, String.class);

            // 5. Now the ticket is in the new state. Loop again to see if there's another transition
            //    until eventually no transitions remain or we reach "Done".
        }

        ackProducer.sendUpdateAck(jobId, true);

        System.out.println("Finished attempting to transition ticket " + ticketKey + " to Done.");
    }

    public void handleNewScan(NewScanRunbookEvent event) {
        String jobId = event.getEventId();
        NewScanRunbookPayload payload = event.getPayload();
        Long tenantId = payload.getTenantId();

        System.out.println("Size of list of findings in handleNewScan: " + payload.getNewFindingIds().size());

        boolean success = false;
        try {
            // 1) Load all runbooks for this tenant
            List<RunbookEntity> runbooks = runbookRepository.findByTenantId(tenantId.intValue());

            // 2) For each runbook, check if it's enabled and if runbook_config.trigger_type = "NEW_SCAN"
            for (RunbookEntity rb : runbooks) {
                if (!rb.isEnabled()) continue;

                RunbookConfigEntity config = runbookConfigRepository.findByRunbookId(rb.getRunbookId());
                if (config == null) continue;

                String triggerType = config.getTrigger(); // a VARCHAR(50)
                if (!"NEW_SCAN".equalsIgnoreCase(triggerType)) {
                    continue;
                }

                // 3) Filter the findings based on filters_json
                //    We'll parse config.getFiltersJson() to see if there's a "state" or "severity"
                Map<String, Object> filters = parseJsonToMap(config.getFiltersJson());

                // We'll fetch the actual findings from ES so we can check their state/severity
                List<String> newFindingIds = payload.getNewFindingIds();
                System.out.println("Size of newFindingIds: " + newFindingIds.size());
                List<Findings> newFindings = esClientService.fetchFindingsByIds(tenantId, newFindingIds);
                System.out.println("Size of newFindings: " + newFindings.size());

                for (Findings finding : newFindings) {
                    if (matchesFilter(finding, filters)) {
                        // 4) Apply actions
                        Map<String, Object> actions = parseJsonToMap(config.getActionsJson());
                        applyActions(actions, finding, tenantId);
                    }
                }
            }
            success = true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Possibly acknowledge the NEW_SCAN job
            ackProducer.sendUpdateAck(jobId, success);
        }
    }

    /**
     * Return true if the given finding matches the filter criteria
     * E.g. filters might have: { "state": "OPEN", "severity": "HIGH" }
     */
    private boolean matchesFilter(Findings finding, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            // no filter => match everything
            return true;
        }
        // If filter has a "state" => must match
        String desiredState = (String) filters.get("state");
        if (desiredState != null && !desiredState.isBlank()) {
            // e.g. "OPEN", "DISMISSED", "FIXED"
            // compare with finding.getState().name() or something
            if (!desiredState.equalsIgnoreCase(finding.getState().name())) {
                return false;
            }
        }
        // If filter has a "severity"
        String desiredSeverity = (String) filters.get("severity");
        if (desiredSeverity != null && !desiredSeverity.isBlank()) {
            // e.g. "HIGH", "LOW"
            if (!desiredSeverity.equalsIgnoreCase(finding.getSeverity().name())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Apply the actions described in actions_json:
     *  e.g. {
     *     "update_finding": { "from": "OPEN", "to": "DISMISS" },
     *     "create_ticket": true
     *  }
     */
    private void applyActions(Map<String, Object> actions, Findings finding, Long tenantId) {
        if (actions == null || actions.isEmpty()) return;

        // 1) Check "update_finding"
        Object updateObj = actions.get("update_finding");
        if (updateObj instanceof Map) {
            Map<String, String> updateMap = (Map<String, String>) updateObj;
            String fromState = updateMap.get("from");
            String toState   = updateMap.get("to");
            // If finding is currently fromState, or if fromState is blank => proceed
            if (fromState == null || fromState.isBlank() ||
                    fromState.equalsIgnoreCase(finding.getState().name())) {
                // We must map the "toState" to GitHub's internal states. e.g. "DISMISS" => state="dismissed", reason="other"
                // Build an UpdateAlertEvent or call handleUpdateFinding directly:

                String mappedNewState = mapRunbookStateToGitHubAction(toState); // you define this

                // Now produce an UPDATE_FINDING event, or call handleUpdateFinding directly:
                UpdateAlertEvent updateEvent = new UpdateAlertEvent(
                        UUID.randomUUID().toString(),
                        new UpdateEvent(
                                tenantId.toString(),
                                finding.getToolType(),
                                Long.parseLong(finding.getAlertNumber()), // be sure this is numeric
                                mappedNewState, // e.g. "DISMISS", "RESOLVE", or "OPEN"
                                "other"
                        )
                );

                // You can produce this event so it flows through JFC & the normal pipeline:
                // Or call handleUpdateFinding(updateEvent) directly, but producing an event
                // is more consistent with the rest of the pipeline.
                try {
                    String json = new ObjectMapper().writeValueAsString(updateEvent);
                    handleUpdateFinding(updateEvent);
                    System.out.println("[Runbook] Triggered UPDATE_FINDING => " + json);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        // 2) Check "create_ticket"
        Object createTicketObj = actions.get("create_ticket");
        if (createTicketObj instanceof Boolean && (Boolean) createTicketObj) {
            // Create a ticket using the doc’s title (summary) / description
            String summary = truncate(finding.getTitle(), 200);
            String desc    = truncate(finding.getDescription(), 200);

            CreateTicketRequestEvent createEvent = new CreateTicketRequestEvent(
                    new CreateTicketRequestPayload(
                            tenantId,
                            finding.getId(),   // the ES doc ID
                            summary,
                            desc
                    ),
                    "ticket_" + UUID.randomUUID(),
                    "jfc-jobs" // or whichever topic
            );
            try {
                String json = new ObjectMapper().writeValueAsString(createEvent);
                handleCreateTicket(createEvent);
                System.out.println("[Runbook] Triggered CREATE_TICKET => " + json);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return (text.length() <= maxLen) ? text : text.substring(0, maxLen);
    }

    private Map<String, Object> parseJsonToMap(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return new ObjectMapper().readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }

    // Example mapping from "DISMISS" => GH "DISMISS"
    private String mapRunbookStateToGitHubAction(String toState) {
        if (toState == null) return "OPEN";
        switch (toState.toUpperCase()) {
            case "SUPPRESSED": return "DISMISS";
            case "FALSE_POSITIVE": return "FALSE_POSITIVE";
            case "DISMISS":  return "DISMISS";
            case "RESOLVE":  return "RESOLVE";
            default:         return "OPEN";
        }
    }
}
