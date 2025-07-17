import okhttp3.*;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class QTestLinkManager {
    
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client;
    private final String baseUrl;
    private final String authToken;
    
    public QTestLinkManager(String baseUrl, String authToken) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.authToken = authToken;
        
        // Configure OkHttp client with timeouts
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Link a test case to multiple requirements in qTest
     * 
     * @param projectId qTest project ID
     * @param testCaseId ID of the test case to link
     * @param requirementIds List of requirement IDs to link to the test case
     * @return LinkResult containing success status and results for each requirement
     */
    public LinkResult linkTestCaseToRequirements(String projectId, String testCaseId, List<String> requirementIds) {
        LinkResult result = new LinkResult();
        result.setTotalAttempted(requirementIds.size());
        
        String linkUrl = String.format("%s/api/v3/projects/%s/object-links", baseUrl, projectId);
        
        for (String requirementId : requirementIds) {
            try {
                // Create JSON payload manually
                String jsonPayload = String.format(
                    "{\n" +
                    "  \"object_type\": \"test-cases\",\n" +
                    "  \"object_id\": %s,\n" +
                    "  \"linked_object_type\": \"requirements\",\n" +
                    "  \"linked_object_id\": %s\n" +
                    "}",
                    testCaseId, requirementId
                );
                
                // Create request
                RequestBody body = RequestBody.create(jsonPayload, JSON);
                Request request = new Request.Builder()
                        .url(linkUrl)
                        .addHeader("Authorization", "Bearer " + authToken)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "application/json")
                        .post(body)
                        .build();
                
                // Execute request
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        result.addSuccess(requirementId, "Successfully linked test case " + testCaseId + " to requirement " + requirementId);
                    } else {
                        String errorBody = response.body() != null ? response.body().string() : "No error details";
                        result.addFailure(requirementId, "HTTP " + response.code() + ": " + errorBody);
                    }
                }
                
            } catch (IOException e) {
                result.addFailure(requirementId, "IO Exception: " + e.getMessage());
            } catch (NumberFormatException e) {
                result.addFailure(requirementId, "Invalid ID format: " + e.getMessage());
            } catch (Exception e) {
                result.addFailure(requirementId, "Unexpected error: " + e.getMessage());
            }
        }
        
        return result;
    }
    
    /**
     * Alternative method using the linkArtifacts endpoint (batch linking)
     * This might be more efficient for linking multiple requirements at once
     */
    public LinkResult linkTestCaseToRequirementsBatch(String projectId, String testCaseId, List<String> requirementIds) {
        LinkResult result = new LinkResult();
        result.setTotalAttempted(requirementIds.size());
        
        String linkUrl = String.format("%s/api/v3/projects/%s/object-link/linkArtifacts", baseUrl, projectId);
        
        try {
            // Build requirement IDs array string
            StringBuilder reqIdsBuilder = new StringBuilder();
            for (int i = 0; i < requirementIds.size(); i++) {
                if (i > 0) reqIdsBuilder.append(", ");
                reqIdsBuilder.append(requirementIds.get(i));
            }
            
            // Create batch JSON payload manually
            String jsonPayload = String.format(
                "{\n" +
                "  \"objectType\": \"test-cases\",\n" +
                "  \"objectId\": %s,\n" +
                "  \"linkedObjectType\": \"requirements\",\n" +
                "  \"linkedObjectIds\": [%s]\n" +
                "}",
                testCaseId, reqIdsBuilder.toString()
            );
            
            // Create request
            RequestBody body = RequestBody.create(jsonPayload, JSON);
            Request request = new Request.Builder()
                    .url(linkUrl)
                    .addHeader("Authorization", "Bearer " + authToken)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .post(body)
                    .build();
            
            // Execute request
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    // If batch request is successful, mark all as successful
                    for (String reqId : requirementIds) {
                        result.addSuccess(reqId, "Successfully linked via batch operation");
                    }
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "No error details";
                    // If batch fails, mark all as failed
                    for (String reqId : requirementIds) {
                        result.addFailure(reqId, "Batch operation failed - HTTP " + response.code() + ": " + errorBody);
                    }
                }
            }
            
        } catch (IOException e) {
            for (String reqId : requirementIds) {
                result.addFailure(reqId, "IO Exception: " + e.getMessage());
            }
        } catch (NumberFormatException e) {
            for (String reqId : requirementIds) {
                result.addFailure(reqId, "Invalid ID format: " + e.getMessage());
            }
        } catch (Exception e) {
            for (String reqId : requirementIds) {
                result.addFailure(reqId, "Unexpected error: " + e.getMessage());
            }
        }
        
        return result;
    }
    
    /**
     * Alternative batch method using org.json if you prefer JSON library over manual strings
     */
    public LinkResult linkTestCaseToRequirementsBatchWithJSONLib(String projectId, String testCaseId, List<String> requirementIds) {
        LinkResult result = new LinkResult();
        result.setTotalAttempted(requirementIds.size());
        
        String linkUrl = String.format("%s/api/v3/projects/%s/object-link/linkArtifacts", baseUrl, projectId);
        
        try {
            // Create JSON using org.json library
            JSONObject payload = new JSONObject();
            payload.put("objectType", "test-cases");
            payload.put("objectId", Integer.parseInt(testCaseId));
            payload.put("linkedObjectType", "requirements");
            
            // Create JSON array for requirement IDs
            JSONArray reqIdsArray = new JSONArray();
            for (String reqId : requirementIds) {
                reqIdsArray.put(Integer.parseInt(reqId));
            }
            payload.put("linkedObjectIds", reqIdsArray);
            
            // Create request
            RequestBody body = RequestBody.create(payload.toString(), JSON);
            Request request = new Request.Builder()
                    .url(linkUrl)
                    .addHeader("Authorization", "Bearer " + authToken)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .post(body)
                    .build();
            
            // Execute request
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    for (String reqId : requirementIds) {
                        result.addSuccess(reqId, "Successfully linked via batch operation");
                    }
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "No error details";
                    for (String reqId : requirementIds) {
                        result.addFailure(reqId, "Batch operation failed - HTTP " + response.code() + ": " + errorBody);
                    }
                }
            }
            
        } catch (Exception e) {
            for (String reqId : requirementIds) {
                result.addFailure(reqId, "Error: " + e.getMessage());
            }
        }
        
        return result;
    }
    
    /**
     * Helper method to validate if requirements exist before linking
     */
    public Set<String> validateRequirements(String projectId, List<String> jiraIds, List<String> requirementIds) {
        Set<String> validRequirementIds = new HashSet<>();
        
        String requirementsUrl = String.format("%s/api/v3/projects/%s/requirements", baseUrl, projectId);
        
        try {
            Request request = new Request.Builder()
                    .url(requirementsUrl)
                    .addHeader("Authorization", "Bearer " + authToken)
                    .addHeader("Accept", "application/json")
                    .get()
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    
                    // Parse JSON response manually or use simple string contains check
                    // This is a simplified validation - adjust based on your needs
                    for (String reqId : requirementIds) {
                        if (responseBody.contains("\"id\":" + reqId) || 
                            responseBody.contains("\"id\": " + reqId)) {
                            validRequirementIds.add(reqId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error validating requirements: " + e.getMessage());
        }
        
        return validRequirementIds;
    }
    
    /**
     * Enhanced validation method using org.json for better JSON parsing
     */
    public Set<String> validateRequirementsWithJSONLib(String projectId, List<String> jiraIds, List<String> requirementIds) {
        Set<String> validRequirementIds = new HashSet<>();
        
        String requirementsUrl = String.format("%s/api/v3/projects/%s/requirements", baseUrl, projectId);
        
        try {
            Request request = new Request.Builder()
                    .url(requirementsUrl)
                    .addHeader("Authorization", "Bearer " + authToken)
                    .addHeader("Accept", "application/json")
                    .get()
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    
                    // Parse using org.json
                    JSONArray requirements = new JSONArray(responseBody);
                    
                    // Create a set of existing requirement IDs for fast lookup
                    Set<String> existingReqIds = new HashSet<>();
                    for (int i = 0; i < requirements.length(); i++) {
                        JSONObject req = requirements.getJSONObject(i);
                        if (req.has("id")) {
                            existingReqIds.add(req.get("id").toString());
                        }
                    }
                    
                    // Validate each requirement ID
                    for (String reqId : requirementIds) {
                        if (existingReqIds.contains(reqId)) {
                            validRequirementIds.add(reqId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error validating requirements: " + e.getMessage());
        }
        
        return validRequirementIds;
    }
    
    public void close() {
        if (client != null) {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
        }
    }
    
    // Result class remains the same
    public static class LinkResult {
        private List<String> successfulLinks = new ArrayList<>();
        private Map<String, String> failures = new HashMap<>();
        private int totalAttempted;
        
        public void addSuccess(String requirementId, String message) {
            successfulLinks.add(requirementId);
        }
        
        public void addFailure(String requirementId, String errorMessage) {
            failures.put(requirementId, errorMessage);
        }
        
        public int getSuccessfulLinksCount() {
            return successfulLinks.size();
        }
        
        public int getFailedLinksCount() {
            return failures.size();
        }
        
        public List<String> getSuccessfulLinks() {
            return successfulLinks;
        }
        
        public Map<String, String> getFailures() {
            return failures;
        }
        
        public int getTotalAttempted() {
            return totalAttempted;
        }
        
        public void setTotalAttempted(int totalAttempted) {
            this.totalAttempted = totalAttempted;
        }
        
        public boolean hasFailures() {
            return !failures.isEmpty();
        }
        
        public void printSummary() {
            System.out.println("=== Link Operation Summary ===");
            System.out.println("Total Attempted: " + totalAttempted);
            System.out.println("Successful Links: " + getSuccessfulLinksCount());
            System.out.println("Failed Links: " + getFailedLinksCount());
            
            if (hasFailures()) {
                System.out.println("\nFailures:");
                failures.forEach((reqId, error) -> 
                    System.out.println("  Requirement " + reqId + ": " + error));
            }
        }
    }
}

// Example usage
class QTestLinkExample {
    public static void main(String[] args) {
        QTestLinkManager linkManager = new QTestLinkManager("https://yoursite.qtestnet.com", "your-auth-token");
        
        try {
            List<String> requirementIds = Arrays.asList("12345", "12346", "12347");
            String testCaseId = "54321";
            String projectId = "98765";
            
            // Method 1: Individual linking with manual JSON strings
            LinkResult result = linkManager.linkTestCaseToRequirements(projectId, testCaseId, requirementIds);
            result.printSummary();
            
            // Method 2: Batch linking with manual JSON strings
            // LinkResult batchResult = linkManager.linkTestCaseToRequirementsBatch(projectId, testCaseId, requirementIds);
            
            // Method 3: Batch linking using org.json library (if you prefer)
            // LinkResult batchResultWithLib = linkManager.linkTestCaseToRequirementsBatchWithJSONLib(projectId, testCaseId, requirementIds);
            
        } finally {
            linkManager.close();
        }
    }
}
