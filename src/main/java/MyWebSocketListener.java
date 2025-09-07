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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;

/**
 * 实现了 WebSocket.Listener 接口的监听器。
 * 这是处理所有WebSocket事件（连接、消息、关闭、错误）的核心。
 */
public class MyWebSocketListener implements WebSocket.Listener {
    // 定义一个成员变量来累积消息分片
    private final StringBuilder messageBuffer = new StringBuilder();
    // 格式化时间（可选）
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    // 定义超时时间
    private static final long TIMEOUT_MINUTES = 5;
    // 创建一个单线程的调度器来处理超时任务。
    // 使用单线程确保任务调度的顺序性。
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    // 用于持有当前待执行的超时任务的引用，以便我们可以取消它。
    private ScheduledFuture<?> timeoutTask;
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final WorkflowGenerator generator = new WorkflowGenerator();
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
        // 如果连续5分钟未收到任何消息，尝试主动推送任务，以增强代码的健壮性。
        resetTimeout();

        // 2. 将收到的数据分片追加到缓冲区
        messageBuffer.append(data);
        // 3. 只有当这是最后一个分片时，才处理完整的消息
        if (last) {
            try {
                String completeMessage = messageBuffer.toString();

                JsonNode rootNode = objectMapper.readTree(completeMessage);
                JsonNode queueNode = rootNode
                        .path("data")
                        .path("status")
                        .path("exec_info")
                        .path("queue_remaining");

                // 在 ComfyUI 的任务序列中维持至少三个待完成任务
                if (!queueNode.isMissingNode() && queueNode.isInt() && queueNode.asInt() < 3) {
                    // todo 打印日志：推送任务，上一张图的计算时间
                    pushTask();
                }
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            } finally {
                // 4. 处理完一条完整的消息后，清空缓冲区，为下一条消息做准备
                messageBuffer.setLength(0);
            }
        }

        // 处理完消息后，继续请求下一条消息。
        webSocket.request(1);

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
        shutdownScheduler(); // 清理资源
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
        shutdownScheduler(); // 清理资源
        // 释放latch，让主线程可以结束。
        latch.countDown();
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
                // 打印结果
                System.out.println(LocalDateTime.now().format(formatter) + " 向ComfyUI推送了新的任务");

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

    private synchronized void resetTimeout() {
        // 1. 如果存在一个已经安排好的超时任务，取消它。
        if (timeoutTask != null && !timeoutTask.isDone()) {
            // false表示不中断 timeoutTask，但取消 timeoutTask尚未开始执行的任务，如取消调用pushTask方法
            timeoutTask.cancel(false);
        }
        // 2. 安排一个新的超时任务。
        //    这个任务将在 TIMEOUT_SECONDS 秒后执行 pushTask() 方法。
        timeoutTask = scheduler.schedule(() -> {
            System.out.printf("超时！在 %d 分钟内未收到新消息，执行 pushTask...\n", TIMEOUT_MINUTES);
            pushTask();
            // 主动推送任务后，如果 ComfyUI仍未回复消息，自动触发倒计时，则不应手动重启计时器，导致 ComfyUI彻底卡死。
//            resetTimeout();
        }, TIMEOUT_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * 安全地关闭调度器。
     */
    private void shutdownScheduler() {
        System.out.println("正在关闭调度器...");
        scheduler.shutdownNow(); // 尝试立即停止所有正在执行和等待的任务
        try {
            // 等待一段时间以确保线程池完全关闭
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("调度器在超时后仍未关闭。");
            }
        } catch (InterruptedException e) {
            // 在关闭过程中当前线程被中断，也强制关闭
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
