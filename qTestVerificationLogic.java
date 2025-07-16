private boolean verifyJiraIdWithProjectRequirements(String[] toVerify) throws BaseException, IOException {
    if (toVerify == null || toVerify.length == 0) {
        throw new IllegalArgumentException("Jira IDs to verify must not be null or empty");
    }

    String projectId = Data.getProjectID(ListenerUtility.key);
    String bearerToken = Data.getBearerToken();
    if (!bearerToken.toLowerCase().startsWith("bearer ")) {
        bearerToken = "Bearer " + bearerToken.trim();
    }

    OkHttpClient client = new OkHttpClient();
    String baseUrl = "https://edwardjonesus.qtestnet.com";
    String fetchRequirementIds = "/api/v3/projects/" + projectId + "/requirements";
    String uri = baseUrl + fetchRequirementIds;

    int currentPage = 1;
    int pageSize = 200;
    Set<String> values = new HashSet<>();

    while (true) {
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(uri)).newBuilder()
                .addQueryParameter("page", String.valueOf(currentPage))
                .addQueryParameter("size", String.valueOf(pageSize))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", bearerToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error body";
                throw new IOException("Failed fetching requirements: " + errorBody);
            }

            if (response.body() != null) {
                String body = response.body().string();
                JSONArray items = new JSONArray(body);
                if (items.isEmpty()) {
                    break;
                }

                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    if (item.has("name")) {
                        values.add(item.getString("name"));
                    }
                }

                currentPage++;
            } else {
                break;
            }
        }
    }

    return values.containsAll(List.of(toVerify));
}
