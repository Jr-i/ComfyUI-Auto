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
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DynamicPrompt {
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final String host = "://192.168.2.199:8188";
    private static final String clientId = "CustomJavaAPI";
    private static List<String> charLines;
    private static List<String> locationLines;
    private static Remark remark;
    private static ObjectNode workflowNode;

    public static void dynamicPrompt(String[] args) {
        readRemark();

        // 从参数中获取工作流
        if (args.length > 0) {
            String fileName = args[0];
            try {
                workflowNode = (ObjectNode) objectMapper.readTree(
                        new File("/home/jr/ComfyUI/" + fileName + ".json"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            System.out.println("请指明工作流文件的名称");
        }

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

    private static class WebSocketListener implements Listener {
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
                JsonNode queueRemainingNode = rootNode.get("data").get("status").get("exec_info").get("queue_remaining");
                if (queueRemainingNode.asInt() == 0) {
                    if (remark.getLocationIndex() == remark.getLocationTotalLines() - 1) {
                        if (remark.getCharIndex() == remark.getCharTotalLines() - 1) {
                            remark.setCharIndex(0);
                            remark.setLocationIndex(0);
                        } else {
                            remark.incrementCharIndex();
                            remark.setLocationIndex(0);
                        }
                    } else {
                        remark.incrementLocationIndex();
                    }
                    objectMapper.writeValue(new File("/home/jr/ComfyUI/remark.json"), remark);
                    pushTask();
                }

            } catch (Exception e) {
                e.printStackTrace();
                // 为避免解析json出错时，错过剩余任务为0的信息，直接追加一个新任务
                pushTask();
            }
            return Listener.super.onText(webSocket, data, last);
        }
    }

    private static void pushTask() {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            BigInteger seed = generateRandomBigInteger();
            workflowNode.with("20").with("inputs")
                    .put("seed", seed);

            String scene = charLines.get(remark.getCharIndex())
                    + "," + locationLines.get(remark.getLocationIndex());
            workflowNode.with("26").with("inputs")
                    .put("text_b", scene);
            workflowNode.with("9").with("inputs")
                    .put("filename_prefix", scene);

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
                    String result = EntityUtils.toString(responseEntity, "utf-8");
                    System.out.println(result);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static BigInteger generateRandomBigInteger() {
        BigInteger maxValue = new BigInteger("9000999999999999");
        BigInteger result;
        do {
            result = new BigInteger(maxValue.bitLength(), secureRandom);
        } while (result.compareTo(maxValue) > 0);
        return result;
    }

    /**
     * 校验char、location文件是否被修改，获取上次读取的文件位置
     */
    private static void readRemark() {
        try {
            remark = objectMapper.readValue(new File("/home/jr/ComfyUI/remark.json"), Remark.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        charLines = deleteEmptyLine("/home/jr/ComfyUI/char.txt");
        locationLines = deleteEmptyLine("/home/jr/ComfyUI/location.txt");

        remark.setCharTotalLines(charLines.size());
        remark.setLocationTotalLines(locationLines.size());

        String charFeature = computeSHA256(charLines);
        String locationFeature = computeSHA256(locationLines);

        if (!charFeature.equals(remark.getCharFeature())) {
            int randomIndex = secureRandom.nextInt(remark.getCharTotalLines());
            remark.setCharIndex(randomIndex);
            remark.setLocationIndex(0);
            remark.setCharFeature(charFeature);
        } else if (!locationFeature.equals(remark.getLocationFeature())) {
            remark.setLocationIndex(0);
            remark.setLocationFeature(locationFeature);
        }

    }

    private static List<String> deleteEmptyLine(String filePath) {
        try (Stream<String> lines = Files.lines(Paths.get(filePath))) {
            return lines.filter(line -> !line.trim().isEmpty()).collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String computeSHA256(List<String> lines) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        String combinedString = String.join("\n", lines);
        byte[] hash = digest.digest(combinedString.getBytes());
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
