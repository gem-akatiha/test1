import okhttp3.*;
import java.io.IOException;
import java.util.List;

public class QTestApiClient {

    private static final String BASE_URL = "https://your-qtest-url.com/api/v3";
    private static final String API_TOKEN = "your-api-token";
    private static final OkHttpClient client = new OkHttpClient();

    public static void linkRequirementsToTestCase(int projectId, int testCaseId, List<Integer> requirementIds) throws IOException {
        HttpUrl url = HttpUrl.parse(BASE_URL + "/projects/" + projectId + "/test-cases/" + testCaseId + "/links");

        if (url == null) {
            throw new IllegalArgumentException("Invalid URL");
        }

        // Construct the JSON payload
        StringBuilder jsonBody = new StringBuilder("[");
        for (int i = 0; i < requirementIds.size(); i++) {
            int reqId = requirementIds.get(i);
            jsonBody.append("{\"id\":").append(reqId).append(",\"type\":\"requirement\"}");
            if (i != requirementIds.size() - 1) {
                jsonBody.append(",");
            }
        }
        jsonBody.append("]");

        RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.parse("application/json"));

        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + API_TOKEN)
            .post(body)
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Linking failed. Code: " + response.code() + ", Body: " + response.body().string());
            }
            System.out.println("Requirements linked successfully to test case " + testCaseId);
        }
    }
}
