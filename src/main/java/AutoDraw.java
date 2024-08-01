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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
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
    private static ObjectNode jsonNodes;
    private static boolean moveFlag = false; // 完成全部放大操作，需要移动图片
    private static boolean deleteFlag = false; // 执行过放大操作，需要删除原图


    public static void main(String[] args) {
        File[] files = new File("input").listFiles();
        // 有待放大图片，执行放大工作流
        if (files.length > 0) {
            jsonNodes = Upscale.upscaleWorkflowBuilder(files[0]);
            deleteFlag = true;
        }
        // 无待放大图片，执行动态提示词工作流
        else {
            jsonNodes = DynamicPrompt.dynamicBuilder();
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
                    // 放大操作执行成功，删除原图
                    if (deleteFlag) {
                        new File("input").listFiles()[0].delete();
                        deleteFlag = false;
                        moveFlag = true;
                    }

                    File[] files = new File("input").listFiles();
                    // 有待放大图片，执行放大工作流
                    if (files.length > 0) {
                        jsonNodes = Upscale.upscaleWorkflowBuilder(files[0]);
                        deleteFlag = true;
                    }
                    // 无待放大图片，执行动态提示词工作流
                    else {
                        // 执行过放大操作，且完成全部放大工作
                        if (moveFlag) {
                            // 移动4K图片
                            move4K();
                            moveFlag = false;
                        }
                        jsonNodes = DynamicPrompt.dynamicBuilder();
                    }
                    pushTask(jsonNodes);
                }

            } catch (Exception e) {
                e.printStackTrace();
                // 为避免解析json出错时，错过剩余任务为0的信息，直接追加一个新任务
                jsonNodes = DynamicPrompt.dynamicBuilder();
                pushTask(jsonNodes);
            }
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }
    }

    private static void move4K() {
        File output = new File("output");
        File[] files = output.listFiles(e ->
                e.isFile() && e.getName().endsWith(".png"));

        for (File file : files) {
            try {
                BufferedImage image = ImageIO.read(file);
                if (image.getWidth() == 2160 || image.getHeight() == 2160) {
                    // 分辨率为4K，移动文件
                    Path source = file.toPath();
                    Path target = Path.of("output/upscale/" + file.getName());
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("文件移动成功");
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
