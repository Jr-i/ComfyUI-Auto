import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.imaging.Imaging;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class ImageInfoPrint {
    public static void main(String[] args) {
        // 获取图片文件列表
        File imageFolder = new File("C:\\Users\\suyis\\OneDrive\\图片\\Stable Diffusion\\4K");
        ArrayList<File> imageFiles = new ArrayList<>();
        listImages(imageFolder, imageFiles);

        // 获取图片信息
        ArrayList<ArrayList<String>> images = printImageInfo(imageFiles);

        // 存储图片信息到sheet
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("图片信息");
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("模型");
        headerRow.createCell(1).setCellValue("设定");
        for (int i = 0; i < images.size(); i++) {
            for (int j = 0; j < images.get(i).size(); j++) {
                sheet.createRow(i + 1).createCell(j).setCellValue(images.get(i).get(j));
            }
        }

        // 写入Excel文件
        File file = new File("C:\\Users\\suyis\\OneDrive\\其他\\图片信息.xlsx");
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            workbook.write(outputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // 遍历文件夹并获取所有图片文件
    private static void listImages(File imageFolder, ArrayList<File> imageFiles) {
        File[] files = imageFolder.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    // 递归调用以处理子文件夹
                    listImages(file, imageFiles);
                } else if (file.getName().endsWith("png")) {
                    imageFiles.add(file);
                }
            }
        }
    }

    private static ArrayList<ArrayList<String>> printImageInfo(ArrayList<File> imageFiles) {
        ArrayList<ArrayList<String>> images = new ArrayList<>();

        for (File file : imageFiles) {
            ObjectNode promptNode;
            try {
                // 从图片中获取注释信息
                String comment = Imaging.getImageInfo(file).getComments().get(0);
                // 将注释信息转换为jsonNode
                int startIndex = comment.indexOf('{');
                String jsonString = comment.substring(startIndex);
                promptNode = (ObjectNode) new ObjectMapper().readTree(jsonString);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            String checkpoint = promptNode.get("43").get("inputs").get("ckpt_name")
                    .textValue().replace(".safetensors", "");

            String fileName = file.getName();
            String inputInfo = fileName.substring(0, fileName.lastIndexOf("_0"));

            ArrayList<String> imageInfo = new ArrayList<>();
            imageInfo.add(checkpoint);
            imageInfo.add(inputInfo);
            images.add(imageInfo);
        }

        return images;
    }
}