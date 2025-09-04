import com.fasterxml.jackson.core.JsonProcessingException;
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

import java.io.IOException;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;

/**
 * 实现了 WebSocket.Listener 接口的监听器。
 * 这是处理所有WebSocket事件（连接、消息、关闭、错误）的核心。
 */
public class MyWebSocketListener implements WebSocket.Listener {
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final ComfyUIWorkflowGenerator generator = new ComfyUIWorkflowGenerator();
    // 使用 CountDownLatch 来等待 onClose 事件，以防止主线程过早退出
    private final CountDownLatch latch;

    public MyWebSocketListener(CountDownLatch latch) {
        this.latch = latch;
    }

    /**
     * 当WebSocket连接成功建立时被调用。
     *
     * @param webSocket The WebSocket that was opened.
     */
    @Override
    public void onOpen(WebSocket webSocket) {
        System.out.println("WebSocket 连接已打开!");
        // 在建立连接后，请求接收第一条消息。这是背压（back-pressure）机制的一部分。
        webSocket.request(1);
    }

    /**
     * 当收到文本消息时被调用。
     *
     * @param webSocket The WebSocket on which the message was received.
     * @param data      The received message data.
     * @param last      Indicates if this is the last part of a multi-part message.
     * @return a CompletionStage that completes when the message has been processed.
     */
    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        System.out.println("监听到消息: " + data);

        try {
            JsonNode rootNode = objectMapper.readTree(data.toString());
            JsonNode queueNode = rootNode
                    .path("data")
                    .path("status")
                    .path("exec_info")
                    .path("queue_remaining");

            if (!queueNode.isMissingNode() && queueNode.isInt() && queueNode.asInt() == 0) {
                // todo 打印日志：推送任务，上一张图的计算时间
                pushTask();
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        } finally {
            // 处理完消息后，继续请求下一条消息。
            webSocket.request(1);
        }

        // 返回 null 表示我们已经同步处理完成。
        return null;
    }

    /**
     * 当连接关闭时被调用。
     *
     * @param webSocket  The WebSocket that was closed.
     * @param statusCode The WebSocket status code.
     * @param reason     A descriptive reason for the closure.
     * @return a CompletionStage that completes when the closure has been processed.
     */
    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        System.out.println("WebSocket 连接已关闭. 状态码: " + statusCode + ", 原因: " + reason);
        // 释放latch，让主线程可以结束。
        latch.countDown();
        return null;
    }

    /**
     * 当发生错误时被调用。
     *
     * @param webSocket The WebSocket on which the error occurred.
     * @param error     The Throwable that describes the error.
     */
    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        System.err.println("发生错误: " + error.getMessage());
        error.printStackTrace();
        // 释放latch，让主线程可以结束。
//        latch.countDown();
    }

    private void pushTask() {
        ObjectNode rootNode = objectMapper.createObjectNode();
        rootNode.set("prompt", generator.nextWorkflow());

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            String jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);

            StringEntity params = new StringEntity(jsonString, "utf-8");

            HttpPost httpPost = new HttpPost("http://" + NewAutoDraw.host + "/prompt");
            httpPost.addHeader("content-type", "application/json");
            httpPost.setEntity(params);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                HttpEntity responseEntity = response.getEntity();
                if (responseEntity != null) {
                    String result = EntityUtils.toString(responseEntity, "utf-8");
                    System.out.println("请求获得回复：" + result);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
