package com.example;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

@Service
public class SearchService {

    private final String[] processingServers = {
            // "http://34.172.60.58:8080",
            // "http://34.66.126.176:8080",
            // "http://34.31.70.152:8080"
            "http://10.128.0.40:8080",
            "http://10.128.0.42:8080",
            "http://10.128.0.41:8080"
    };

    private final RestTemplate restTemplate = new RestTemplate();

    public Map<String, Object> performDistributedSearch(int n) {
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> results = new ArrayList<>();
        long totalProcessingTime = 0;

        int availableServers = 0;
        List<String> availableServerUrls = new ArrayList<>();

        for (String server : processingServers) {
            try {
                Map<String, Object> status = restTemplate.getForObject(server + "/status", Map.class);
                if ("online".equals(status.get("status"))) {
                    availableServers++;
                    availableServerUrls.add(server);
                }
            } catch (Exception e) {
                // Handle the exception (e.g., server is down)
            }
        }

        if (availableServers == 0) {
            response.put("error", "No processing servers are available");
            return response;
        }

        int totalDocuments = 45;
        int documentsPerServer = totalDocuments / availableServers;
        int startDocument = 0;
        int endDocument;

        for (String serverUrl : availableServerUrls) {
            endDocument = startDocument + documentsPerServer - 1;
            if (serverUrl.equals(availableServerUrls.get(availableServerUrls.size() - 1))) {
                endDocument = totalDocuments; // Ensure the last server processes the remaining documents
            }

            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(serverUrl + "/search")
                    .queryParam("n", n)
                    .queryParam("startDocumentIndex", startDocument)
                    .queryParam("endDocumentIndex", endDocument);

            try {
                Map<String, Object> serverResponse = restTemplate.getForObject(uriBuilder.toUriString(), Map.class);
                results.add(serverResponse);
                totalProcessingTime += (Long) serverResponse.get("processingTime");
                System.out.println(totalProcessingTime);
            } catch (Exception e) {
                // Handle the exception (e.g., server is down)
            }

            startDocument = endDocument + 1;
        }

        // Combine the results from all servers
        List<Map<String, Object>> combinedResults = combineResults(results);

        response.put("totalProcessingTime", totalProcessingTime);
        response.put("n", n);
        response.put("results", combinedResults);
        response.put("activeServers", availableServers);

        return response;
    }

    private List<Map<String, Object>> combineResults(List<Map<String, Object>> results) {
        Map<String, Set<String>> phraseToDocumentsMap = new HashMap<>();

        for (Map<String, Object> result : results) {
            List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
            for (Map<String, Object> match : matches) {
                String phrase = (String) match.get("phrase");
                List<String> documents = (List<String>) match.get("documents");
                phraseToDocumentsMap.computeIfAbsent(phrase, k -> new HashSet<>()).addAll(documents);
            }
        }

        List<Map<String, Object>> combinedResults = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : phraseToDocumentsMap.entrySet()) {
            Map<String, Object> combinedResult = new HashMap<>();
            combinedResult.put("phrase", entry.getKey());
            combinedResult.put("documents", new ArrayList<>(entry.getValue()));
            combinedResults.add(combinedResult);
        }

        // Sort results alphabetically by phrase
        combinedResults.sort(Comparator.comparing(result -> (String) result.get("phrase")));

        return combinedResults;
    }

    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();
        List<Map<String, Object>> serverStatuses = new ArrayList<>();

        for (String server : processingServers) {
            try {
                Map<String, Object> serverStatus = restTemplate.getForObject(server + "/status", Map.class);
                serverStatuses.add(serverStatus);
            } catch (Exception e) {
                Map<String, Object> serverStatus = new HashMap<>();
                serverStatus.put("status", "offline");
                serverStatus.put("cpuUsage", "N/A");
                serverStatuses.add(serverStatus);
            }
        }

        status.put("servers", serverStatuses);
        return status;
    }
}
