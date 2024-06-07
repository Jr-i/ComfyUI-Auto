import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletionStage;

public class AutoDraw {
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final String host = "://192.168.2.199:8188";
    private static final String clientId = "CustomJavaAPI";
    private static String workflowFileName;
    private static ObjectNode jsonNodes;

    public static void main(String[] args) {
        if (args.length > 0) {
            workflowFileName = args[0];
        } else {
            System.out.println("请指明工作流文件的名称");
        }

        File[] files = new File("input").listFiles();
        // 有待放大图片，执行放大工作流
        if (files != null) {
            jsonNodes = Upscale.upscaleWorkflowBuilder(files[0]);
        }
        // 无待放大图片，执行动态提示词工作流
        else {
            jsonNodes = DynamicPrompt.dynamicBuilder(workflowFileName);
        }
        pushTask(jsonNodes);

        // 启动WebSocket
        HttpClient client = HttpClient.newHttpClient();
        URI uri = URI.create("ws" + host + "/ws?client_id=" + clientId);
        client.newWebSocketBuilder().buildAsync(uri, new WebSocketListener()).join();

        // 维持WebSocket
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void pushTask(ObjectNode workflowNode) {
        ObjectNode rootNode = objectMapper.createObjectNode();
        rootNode.put("client_id", clientId);
        rootNode.set("prompt", workflowNode);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            String jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);

            StringEntity params = new StringEntity(jsonString, "utf-8");

            HttpPost httpPost = new HttpPost("http" + host + "/prompt");
            httpPost.addHeader("content-type", "application/json");
            httpPost.setEntity(params);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                HttpEntity responseEntity = response.getEntity();
                if (responseEntity != null) {
                    String result = EntityUtils.toString(responseEntity, "utf-8");
                    System.out.println(result);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class WebSocketListener implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket webSocket) {
            System.out.println("WebSocket连接建立");
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            System.out.println("WebSocket连接关闭");
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            System.out.println(data.toString());
            try {
                JsonNode rootNode = objectMapper.readTree(data.toString());
                JsonNode queueRemainingNode = rootNode.get("data").get("status").get("exec_info").get("queue_remaining");
                if (queueRemainingNode.asInt() == 0) {
                    // 移动已放大的原图片
                    File[] files = new File("input").listFiles();
                    if (files != null) {
                        Path source = Path.of("input/" + files[0].getName());
                        Path target = Path.of("output/upscale/" + files[0].getName());
                        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("文件移动成功");
                    }

                    File[] files2 = new File("input").listFiles();
                    // 有待放大图片，执行放大工作流
                    if (files2 != null) {
                        jsonNodes = Upscale.upscaleWorkflowBuilder(files2[0]);
                    }
                    // 无待放大图片，执行动态提示词工作流
                    else {
                        jsonNodes = DynamicPrompt.dynamicBuilder(workflowFileName);
                    }
                    pushTask(jsonNodes);
                }

            } catch (Exception e) {
                e.printStackTrace();
                // 为避免解析json出错时，错过剩余任务为0的信息，直接追加一个新任务
                jsonNodes = DynamicPrompt.dynamicBuilder(workflowFileName);
                pushTask(jsonNodes);
            }
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }
    }

}
