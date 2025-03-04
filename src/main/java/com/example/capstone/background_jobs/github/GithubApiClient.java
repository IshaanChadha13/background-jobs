package com.example.capstone.background_jobs.github;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class GithubApiClient {

    private final RestTemplate restTemplate;

    public GithubApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean updateCodeScanningAlertState(
            String personalAccessToken, // dynamically passed
            String owner,
            String repo,
            String alertNumber,
            String newState,
            String dismissReason
    ) {
        try {
            String url = String.format(
                    "https://api.github.com/repos/%s/%s/code-scanning/alerts/%s",
                    owner, repo, alertNumber
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(personalAccessToken);
            headers.set("Accept", "application/vnd.github+json");
            headers.setContentType(MediaType.APPLICATION_JSON);

            // GH expects "state": "dismissed"|"open"
            // if dismissed => "dismissed_reason" as well
            String finalState = newState.equalsIgnoreCase("DISMISS")
                    ? "dismissed" : "open";

            Map<String,Object> body;
            if (finalState.equals("dismissed")) {
                body = Map.of(
                        "state", "dismissed",
                        "dismissed_reason", dismissReason != null ? dismissReason : "other"
                );
            } else {
                body = Map.of("state", "open");
            }

            HttpEntity<Map<String,Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = restTemplate.exchange(
                    url, HttpMethod.PATCH, entity, String.class
            );

            return resp.getStatusCode().is2xxSuccessful();

        } catch (HttpClientErrorException.Conflict conflictEx) {
            // 409 => already open => treat it as success
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateDependabotAlertState(
            String personalAccessToken,
            String owner,
            String repo,
            String alertNumber,
            String newState,
            String dismissReason
    ) {
        try {
            String url = String.format(
                    "https://api.github.com/repos/%s/%s/dependabot/alerts/%s",
                    owner, repo, alertNumber
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(personalAccessToken);
            headers.set("Accept", "application/vnd.github+json");
            headers.setContentType(MediaType.APPLICATION_JSON);

            // GH expects { "state": "dismissed"|"open", "dismissed_reason": "..."}
            String finalState = newState.equalsIgnoreCase("DISMISS")
                    ? "dismissed" : "open";

            Map<String,Object> body;
            if ("dismissed".equals(finalState)) {
                body = Map.of(
                        "state", "dismissed",
                        "dismissed_reason", dismissReason != null ? dismissReason : "other"
                );
            } else {
                body = Map.of("state", "open");
            }

            HttpEntity<Map<String,Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = restTemplate.exchange(
                    url, HttpMethod.PATCH, entity, String.class
            );

            return resp.getStatusCode().is2xxSuccessful();

        } catch (HttpClientErrorException.Conflict conflictEx) {
            // 409 => already open => treat it as success
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateSecretScanningAlertState(
            String personalAccessToken,
            String owner,
            String repo,
            String alertNumber,
            String newState,
            String dismissReason
    ) {
        try {
            String url = String.format(
                    "https://api.github.com/repos/%s/%s/secret-scanning/alerts/%s",
                    owner, repo, alertNumber
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(personalAccessToken);
            headers.set("Accept", "application/vnd.github+json");
            headers.setContentType(MediaType.APPLICATION_JSON);

            /*
             * Secret Scanning states: "open", "resolved", "dismissed"
             * If "dismissed" or "resolved", pass "resolution" key (e.g. "false_positive", "wont_fix")
             */
            String ghState;
            if ("DISMISS".equalsIgnoreCase(newState) || "RESOLVE".equalsIgnoreCase(newState)) {
                ghState = "resolved";
            } else {
                ghState = "open";
            }

            Map<String, Object> body;
            if ("resolved".equals(ghState)) {
                body = Map.of(
                        "state", "resolved",
                        "resolution", (dismissReason != null && !dismissReason.isBlank())
                                ? dismissReason
                                : "wont_fix"
                );
            } else {
                body = Map.of("state", "open");
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = restTemplate.exchange(
                    url, HttpMethod.PATCH, entity, String.class
            );

            return resp.getStatusCode().is2xxSuccessful();

        } catch (HttpClientErrorException.Conflict conflictEx) {
            // 409 => already open => treat it as success
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
