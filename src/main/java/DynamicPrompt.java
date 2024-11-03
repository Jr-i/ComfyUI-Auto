import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DynamicPrompt {
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final SecureRandom secureRandom = new SecureRandom();
    private static List<String> charLines;
    private static List<String> locationLines;
    private static Remark remark;
    private static ObjectNode workflowNode;
    final static String[] configName =
            {"WAI-REALMIX", "Animagine", "LEOSAM", "Raemu"};

    public static ObjectNode dynamicBuilder() {
        if (remark == null) {
            readRemark();
        } else {
            remark.setLandscape(!remark.isLandscape());

            if (remark.isLandscape()) {
                if (remark.getLocationIndex() == remark.getLocationTotalLines() - 1) {
                    if (remark.getCharIndex() == remark.getCharTotalLines() - 1) {
                        remark.incrementConfigIndex();
                        workflowNode = null;
                        remark.setCharIndex(0);
                        remark.setLocationIndex(0);
                    } else {
                        remark.incrementCharIndex();
                        remark.setLocationIndex(0);
                    }
                } else {
                    remark.incrementLocationIndex();
                }
            }

            try {
                objectMapper.writeValue(new File("remark.json"), remark);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (workflowNode == null) {
            try {
                workflowNode = (ObjectNode) objectMapper.readTree(
                        new File(configName[remark.getConfigIndex()] + ".json"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        BigInteger seed = new BigInteger(64, secureRandom); // 生成一个64位的随机数
        workflowNode.with("20").with("inputs")
                .put("seed", seed);

        String scene = charLines.get(remark.getCharIndex())
                + "," + locationLines.get(remark.getLocationIndex());
        workflowNode.with("26").with("inputs")
                .put("text_b", scene);
        workflowNode.with("9").with("inputs")
                .put("filename_prefix", scene);

        if (remark.isLandscape()) {
            workflowNode.with("43").with("inputs")
                    .put("empty_latent_width", 1280)
                    .put("empty_latent_height", 768);
        } else {
            workflowNode.with("43").with("inputs")
                    .put("empty_latent_width", 768)
                    .put("empty_latent_height", 1280);
        }

        return workflowNode;
    }

    /**
     * 校验char、location文件是否被修改，获取上次读取的文件位置
     */
    private static void readRemark() {
        try {
            remark = objectMapper.readValue(new File("remark.json"), Remark.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        charLines = deleteEmptyLine("char.txt");
        locationLines = deleteEmptyLine("location.txt");

        remark.setCharTotalLines(charLines.size());
        remark.setLocationTotalLines(locationLines.size());

        String charFeature = computeSHA256(charLines);
        String locationFeature = computeSHA256(locationLines);

        if (!charFeature.equals(remark.getCharFeature())) {
            remark.setCharIndex(0);
            remark.setLocationIndex(0);
            remark.setCharFeature(charFeature);
        } else if (!locationFeature.equals(remark.getLocationFeature())) {
            remark.setLocationIndex(0);
            remark.setLocationFeature(locationFeature);
        }

    }

    static List<String> deleteEmptyLine(String filePath) {
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
