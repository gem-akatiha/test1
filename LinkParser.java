import org.json.JSONArray;
import java.util.regex.*;
import java.util.List;
import java.util.ArrayList;

public class ExtractLinksToJson {
    public static void main(String[] args) {
        String text = "Check these links: https://example.com, http://test.org, and also https://openai.com.";

        // Regular expression for extracting URLs
        String urlRegex = "(https?://[\\w.-]+(?:\\.[a-zA-Z]{2,})+(?:/\\S*)?)";
        Pattern pattern = Pattern.compile(urlRegex);
        Matcher matcher = pattern.matcher(text);

        // Store links in a JSON array
        JSONArray jsonArray = new JSONArray();
        while (matcher.find()) {
            jsonArray.put(matcher.group());
        }

        // Print JSON array
        System.out.println(jsonArray.toString());
    }
}
