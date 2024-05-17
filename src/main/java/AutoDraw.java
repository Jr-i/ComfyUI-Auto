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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class AutoDraw {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String host = "://192.168.2.199:8188";
    private static final String clientId = "CustomJavaAPI";

    public static void main(String[] args) {
        autoDraw();
//        getQueueRemain();
    }

    private static void autoDraw() {
        pushTask();

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
        public void onOpen(WebSocket webSocket) {
            System.out.println("WebSocket连接建立");
            Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            System.out.println("WebSocket连接关闭");
            return Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            System.out.println(data.toString());
            try {
                JsonNode rootNode = objectMapper.readTree(data.toString());
                JsonNode queueRemainingNode = rootNode.path("data").path("status").path("exec_info").path("queue_remaining");
                if (queueRemainingNode.asInt() == 0) {
                    pushTask();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return Listener.super.onText(webSocket, data, last);
        }
    }

    private static void pushTask() {
        String result;

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            JsonNode workflowNode = objectMapper.readTree(
                    new File("/home/jr/ComfyUI/workflow_api.json"));

            ObjectNode rootNode = objectMapper.createObjectNode();
            rootNode.put("client_id", clientId);
            rootNode.set("prompt", workflowNode);

            String jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);

            StringEntity params = new StringEntity(jsonString, "utf-8");

            HttpPost httpPost = new HttpPost("http" + host + "/prompt");
            httpPost.addHeader("content-type", "application/json");
            httpPost.setEntity(params);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                HttpEntity responseEntity = response.getEntity();
                if (responseEntity != null) {
                    result = EntityUtils.toString(responseEntity, "utf-8");
                    Map<String, Object> map = objectMapper.readValue(result, new TypeReference<Map<String, Object>>() {
                    });
                    String promptId = (String) map.get("prompt_id");
                    System.out.println("新建任务： " + promptId);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
