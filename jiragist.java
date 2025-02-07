import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class JiraChildTicketsFetcher {
    private static final String JIRA_URL = "https://your-jira-instance.atlassian.net";
    private static final String API_ENDPOINT = "/rest/api/2/search?jql=parent=ISSUE-123";
    private static final String JIRA_USERNAME = "your-email@example.com";
    private static final String JIRA_API_TOKEN = "your-api-token";

    public static void main(String[] args) throws Exception {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet request = new HttpGet(JIRA_URL + API_ENDPOINT);
        request.setHeader("Authorization", "Basic " + java.util.Base64.getEncoder()
                .encodeToString((JIRA_USERNAME + ":" + JIRA_API_TOKEN).getBytes()));
        request.setHeader("Accept", "application/json");

        CloseableHttpResponse response = httpClient.execute(request);
        String jsonResponse = EntityUtils.toString(response.getEntity());
        httpClient.close();

        extractAndSummarizeDescriptions(jsonResponse);
    }

    private static void extractAndSummarizeDescriptions(String jsonResponse) {
        JSONObject jsonObject = new JSONObject(jsonResponse);
        JSONArray issues = jsonObject.getJSONArray("issues");

        StringBuilder descriptions = new StringBuilder();
        for (int i = 0; i < issues.length(); i++) {
            JSONObject issue = issues.getJSONObject(i);
            JSONObject fields = issue.getJSONObject("fields");
            String description = fields.optString("description", "No description available");
            descriptions.append(description).append("\n\n");
        }

        System.out.println("Collected Descriptions:\n" + descriptions.toString());
        
        String summary = summarizeText(descriptions.toString());
        System.out.println("Summary:\n" + summary);
    }

    private static String summarizeText(String text) {
        // Simple summary by taking the first 300 characters
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }
}
