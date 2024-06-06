import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.imaging.Imaging;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.IOException;

public class Util {
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static void getInfo(String url) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(url);
            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                if (response.getStatusLine().getStatusCode() == 200) {
                    String result = EntityUtils.toString(response.getEntity(), "UTF-8");
                    System.out.println(result);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static JsonNode getPrompt(final File file) {
        try {
            // 从图片中获取注释信息
            String comment = Imaging.getImageInfo(file).getComments().get(0);
            // 将注释信息转换为jsonNode
            int startIndex = comment.indexOf('{');
            String jsonString = comment.substring(startIndex);
            return objectMapper.readTree(jsonString);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
