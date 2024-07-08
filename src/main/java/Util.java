import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.imaging.Imaging;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class Util {
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static void main(String[] args) {
        PrintCheckpointName();
    }

    public static void PrintCheckpointName() {
        File fileFolder = new File("C:\\Users\\suyis\\OneDrive\\图片\\SD\\Raemu");
        List<File> files = Arrays.stream(fileFolder.listFiles())
                .filter(e -> e.getName().endsWith("png")).toList();

        for (File file : files) {
            ObjectNode promptNode;
            try {
                // 从图片中获取注释信息
                String comment = Imaging.getImageInfo(file).getComments().get(0);
                // 将注释信息转换为jsonNode
                int startIndex = comment.indexOf('{');
                String jsonString = comment.substring(startIndex);
                promptNode = (ObjectNode) objectMapper.readTree(jsonString);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            String checkpoint = promptNode.get("43").get("inputs").get("ckpt_name")
                    .textValue().replace(".safetensors", "");

            String name = file.getName();
            System.out.println(checkpoint + " " + name);
        }
    }

}
