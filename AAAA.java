import okhttp3.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class QTestLinkManager {
    
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client;
    private final String baseUrl;
    private final String authToken;
    private final ObjectMapper objectMapper;
    
    public QTestLinkManager(String baseUrl, String authToken) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.authToken = authToken;
        this.objectMapper = new ObjectMapper();
        
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
                // Create payload for linking
                ObjectNode payload = objectMapper.createObjectNode();
                payload.put("object_type", "test-cases");
                payload.put("object_id", Integer.parseInt(testCaseId));
                payload.put("linked_object_type", "requirements");
                payload.put("linked_object_id", Integer.parseInt(requirementId));
                
                // Create request
                RequestBody body = RequestBody.create(objectMapper.writeValueAsString(payload), JSON);
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
            // Create batch payload
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("objectType", "test-cases");
            payload.put("objectId", Integer.parseInt(testCaseId));
            payload.put("linkedObjectType", "requirements");
            
            // Add all requirement IDs as an array
            payload.putArray("linkedObjectIds")
                    .addAll(requirementIds.stream()
                            .map(id -> objectMapper.valueToTree(Integer.parseInt(id)))
                            .toList());
            
            // Create request
            RequestBody body = RequestBody.create(objectMapper.writeValueAsString(payload), JSON);
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
     * Helper method to validate if requirements exist before linking
     */
    public Set<String> validateRequirements(String projectId, List<String> jiraIds, List<String> requirementIds) {
        Set<String> validRequirementIds = new HashSet<>();
        
        // This assumes you have a method to fetch requirements
        // You can integrate this with your existing step 2 and 3 logic
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
                    JsonNode requirements = objectMapper.readTree(response.body().string());
                    
                    // Validate each requirement ID against the fetched requirements
                    for (String reqId : requirementIds) {
                        // Add your validation logic here based on your requirements structure
                        // This is a placeholder - adjust based on your actual API response structure
                        if (isValidRequirement(requirements, reqId)) {
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
    
    private boolean isValidRequirement(JsonNode requirements, String requirementId) {
        // Implement your validation logic here
        // This depends on the structure of your requirements API response
        return true; // Placeholder
    }
    
    public void close() {
        if (client != null) {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
        }
    }
    
    // Result class to encapsulate the linking results
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
            
            // Method 1: Individual linking (more granular error handling)
            LinkResult result = linkManager.linkTestCaseToRequirements(projectId, testCaseId, requirementIds);
            result.printSummary();
            
            // Method 2: Batch linking (more efficient for large sets)
            // LinkResult batchResult = linkManager.linkTestCaseToRequirementsBatch(projectId, testCaseId, requirementIds);
            // batchResult.printSummary();
            
        } finally {
            linkManager.close();
        }
    }
}
