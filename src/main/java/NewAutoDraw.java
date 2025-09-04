import java.net.URI;
import java.net.http.HttpClient;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class NewAutoDraw {
    private static final String clientId = "javaClient";
    public static final String host = "127.0.0.1:8188";

    public static void main(String[] args) {
        // todo 采用更优雅的保活方式
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                new AtomicInteger(0).incrementAndGet();
            }
        }, 0, 1000); // 立即开始，每秒执行一次

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
    }
}
