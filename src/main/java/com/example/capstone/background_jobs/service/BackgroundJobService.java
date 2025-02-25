package com.example.capstone.background_jobs.service;

import com.example.capstone.background_jobs.dto.CreateTicketRequestEvent;
import com.example.capstone.background_jobs.dto.TransitionTicketRequestEvent;
import com.example.capstone.background_jobs.dto.UpdateAlertEvent;
import com.example.capstone.background_jobs.github.GithubApiClient;
import com.example.capstone.background_jobs.model.*;
import com.example.capstone.background_jobs.producer.AcknowledgementProducer;
import com.example.capstone.background_jobs.repository.TenantRepository;
import com.example.capstone.background_jobs.repository.TenantTicketRepository;
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

    public BackgroundJobService(TenantRepository tenantRepository,
                                GithubApiClient githubApiClient,
                                ElasticsearchClientService esClientService,
                                AcknowledgementProducer ackProducer,
                                TenantTicketRepository tenantTicketRepository) {
        this.tenantRepository = tenantRepository;
        this.githubApiClient = githubApiClient;
        this.esClientService = esClientService;
        this.ackProducer = ackProducer;
        this.tenantTicketRepository = tenantTicketRepository;
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

            boolean success;
            switch (toolType) {
                case "CODE_SCANNING":
                    success = githubApiClient.updateCodeScanningAlertState(
                            tenant.getPersonalAccessToken(),
                            tenant.getOwner(),
                            tenant.getRepo(),
                            String.valueOf(payload.getAlertNumber()),
                            payload.getNewState(),
                            payload.getReason()
                    );
                    break;
                case "DEPENDABOT":
                    success = githubApiClient.updateDependabotAlertState(
                            tenant.getPersonalAccessToken(),
                            tenant.getOwner(),
                            tenant.getRepo(),
                            String.valueOf(payload.getAlertNumber()),
                            payload.getNewState(),
                            payload.getReason()
                    );
                    break;
                case "SECRET_SCANNING":
                    success = githubApiClient.updateSecretScanningAlertState(
                            tenant.getPersonalAccessToken(),
                            tenant.getOwner(),
                            tenant.getRepo(),
                            String.valueOf(payload.getAlertNumber()),
                            payload.getNewState(),
                            payload.getReason()
                    );
                    break;
                default:
                    success = false;
                    System.out.println("[background-jobs] Unknown toolType => " + toolType);
            }

            // If success => update ES
            if (success) {
                String rawState = payload.getNewState();
                String dismissReason = payload.getReason();   // e.g. "false_positive"

                AlertStateBg mappedState = AlertStateBg.fromRaw(rawState, toolType, dismissReason);

                esClientService.updateFindingInEs(
                        tenant.getEsIndex(),
                        payload.getAlertNumber(),
                        mappedState.name()  // e.g. "SUPPRESSED" or "FALSE_POSITIVE"
                );

                List<String> doneStates = Arrays.asList("FALSE_POSITIVE", "SUPPRESSED", "FIXED", "CONFIRM");
                if (doneStates.contains(mappedState.name())) {
                    // First, find the doc’s ID in ES so we know which row in tenant_ticket to use
                    Optional<String> findingIdOpt = esClientService.findFindingIdByAlertNumber(
                            tenant.getEsIndex(), payload.getAlertNumber()
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

            try {
                Thread.sleep(10000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }

            // Acknowledge
            ackProducer.sendUpdateAck(jobId, success);

        } catch (Exception e) {
            e.printStackTrace();
            ackProducer.sendUpdateAck(jobId, false);
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
}
