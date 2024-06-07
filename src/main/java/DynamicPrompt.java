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

    public static ObjectNode dynamicBuilder(String fileName) {
        if (workflowNode == null) {
            try {
                workflowNode = (ObjectNode) objectMapper.readTree(
                        new File(fileName + ".json"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        BigInteger seed = generateRandomBigInteger();
        workflowNode.with("20").with("inputs")
                .put("seed", seed);

        if (remark == null) {
            readRemark();
        } else {
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
            try {
                objectMapper.writeValue(new File("remark.json"), remark);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        String scene = charLines.get(remark.getCharIndex())
                + "," + locationLines.get(remark.getLocationIndex());
        workflowNode.with("26").with("inputs")
                .put("text_b", scene);
        workflowNode.with("9").with("inputs")
                .put("filename_prefix", scene);

        return workflowNode;
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
