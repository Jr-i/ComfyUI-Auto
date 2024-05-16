import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class AutoDraw {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        try {
            pushTask();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void pushTask() throws IOException {
        JsonNode workflowNode = objectMapper.readTree(
                new File("C:\\Users\\suyis\\Downloads\\workflow_api.json"));

        ObjectNode rootNode = objectMapper.createObjectNode();
        rootNode.put("client_id", "CustomJavaAPI");
        rootNode.putIfAbsent("prompt", workflowNode);

        // 将修改后的JsonNode转换回JSON字符串
        String jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);

        // Step 3: 使用Apache HttpClient发送POST请求
        CloseableHttpClient httpClient = HttpClients.createDefault();
        StringEntity params = new StringEntity(jsonString, "utf-8");

        HttpPost httpPost = new HttpPost("http://192.168.2.199:8188/prompt");
        httpPost.addHeader("content-type", "application/json");
        httpPost.setEntity(params);

        CloseableHttpResponse response = httpClient.execute(httpPost);
        // 处理响应
        HttpEntity responseEntity = response.getEntity();
        if (responseEntity != null) {
            String result = EntityUtils.toString(responseEntity, "utf-8");
            Map<String, Object> map =
                    objectMapper.readValue(result, new TypeReference<Map<String, Object>>() {
                    });
            String promptId = (String) map.get("prompt_id");
            System.out.println(promptId);
        }
    }
}
