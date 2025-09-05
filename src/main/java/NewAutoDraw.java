import java.net.URI;
import java.net.http.HttpClient;
import java.util.concurrent.CountDownLatch;

public class NewAutoDraw {
    private static final String clientId = "javaClient";
    public static final String host = "127.0.0.1:8188";

    public static void main(String[] args) {
        // 用于在示例中阻塞主线程，直到WebSocket关闭
        CountDownLatch latch = new CountDownLatch(1);

        // 1. 创建 HttpClient 实例
        HttpClient client = HttpClient.newHttpClient();
        System.out.println("正在连接到 WebSocket 服务器...");

        // 2. 使用 HttpClient 构建 WebSocket 连接
        //    - 第一个参数是服务器URI
        //    - 第二个参数是我们自定义的监听器实例
        client.newWebSocketBuilder()
                .buildAsync(
                        URI.create("ws://" + host + "/ws?client_id=" + clientId), // 一个公共的WebSocket回显服务器
                        new MyWebSocketListener(latch)
                )
                .join();
        System.out.println("WebSocket 握手完成，客户端已就绪。");

        try {
            // 让主线程等待，直到WebSocket连接关闭或出错
            latch.await();
            System.out.println("主线程已解除阻塞，程序即将退出。");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 重新设置中断状态
            System.err.println("主线程在等待WebSocket关闭时被中断: " + e.getMessage());
        }
    }
}
