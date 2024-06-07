import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.imaging.Imaging;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

public class Upscale {
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static void upscaleWorkflowBuilder(File file) {
        try {
            // 从图片中获取注释信息
            String comment = Imaging.getImageInfo(file).getComments().get(0);
            // 将注释信息转换为jsonNode
            int startIndex = comment.indexOf('{');
            String jsonString = comment.substring(startIndex);
            ObjectNode promptNode = (ObjectNode) objectMapper.readTree(jsonString);

            promptNode.with("9").with("inputs")
                    .putArray("images").add("52").add(0);

            ObjectNode kSamplerNode = (ObjectNode) promptNode.get("20").get("inputs");
            kSamplerNode.put("denoise", 0.55);
            kSamplerNode.putArray("latent_image").add("45").add(0);

            InputStream resourceAsStream = Upscale.class.getResourceAsStream("upscale.json");
            ObjectNode upscaleNodes = (ObjectNode) objectMapper.readTree(resourceAsStream);
            upscaleNodes.with("46").with("inputs")
                    .put("image", file.getName());

            // 将upscaleNodes合并到promptNode中
            for (Iterator<String> it = upscaleNodes.fieldNames(); it.hasNext(); ) {
                String key = it.next();
                promptNode.set(key, upscaleNodes.get(key));
            }

            System.out.println(promptNode);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
