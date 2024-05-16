import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class AutoDraw {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String host = "://192.168.195.199:8188";
    private static final String clientId = "CustomJavaAPI";

    public static void main(String[] args) {
//        autoDraw();
        getQueueRemain();
    }

    private static void autoDraw() {
        HttpClient client = HttpClient.newHttpClient();
        URI uri = URI.create("ws" + host + "/ws?client_id=" + clientId);

        client.newWebSocketBuilder().buildAsync(uri, new WebSocketListener()).join();

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    static class WebSocketListener implements Listener {
        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            try {
                JsonNode jsonNode = objectMapper.readTree(data.toString());
                if ("executing".equals(jsonNode.path("type").asText())) {
                    JsonNode dataNode = jsonNode.path("data");
                    if (dataNode.path("node").isNull()) {
                        pushTask(data.toString());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return Listener.super.onText(webSocket, data, last);
        }
    }

    private static void pushTask(String message) {
        System.out.println(message);
        String result;

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            JsonNode workflowNode = objectMapper.readTree(new File("C:\\Users\\suyis\\Downloads\\workflow_api.json"));

            ObjectNode rootNode = objectMapper.createObjectNode();
            rootNode.put("client_id", clientId);
            rootNode.set("prompt", workflowNode);

            // 将修改后的JsonNode转换回JSON字符串
            String jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);

            // Step 3: 使用Apache HttpClient发送POST请求
            StringEntity params = new StringEntity(jsonString, "utf-8");

            HttpPost httpPost = new HttpPost("http" + host + "/prompt");
            httpPost.addHeader("content-type", "application/json");
            httpPost.setEntity(params);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                // 处理响应
                HttpEntity responseEntity = response.getEntity();
                if (responseEntity != null) {
                    result = EntityUtils.toString(responseEntity, "utf-8");
                    Map<String, Object> map = objectMapper.readValue(result, new TypeReference<Map<String, Object>>() {
                    });
                    String promptId = (String) map.get("prompt_id");
                    System.out.println(promptId);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void getQueueRemain() {
        CloseableHttpClient httpClient = HttpClients.createDefault();

        String result = "";
        CloseableHttpResponse response = null;

        try {
            HttpGet httpGet = new HttpGet("http" + host + "/prompt");
            response = httpClient.execute(httpGet);

            //判断响应状态
            if (response.getStatusLine().getStatusCode() == 200) {
                result = EntityUtils.toString(response.getEntity(), "UTF-8");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (response != null) {
                    response.close();
                }
                httpClient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println(result);
    }
}
