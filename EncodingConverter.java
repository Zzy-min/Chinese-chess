import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class EncodingConverter {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java EncodingConverter <source_dir> <target_dir>");
            return;
        }

        String sourceDir = args[0];
        String targetDir = args[1];

        try {
            // 获取所有Java文件
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            System.out.println("Found " + javaFiles.size() + " Java files to convert");

            // 转换每个文件
            for (Path sourcePath : javaFiles) {
                Path sourceDirPath = Paths.get(sourceDir);
                String relativePath = sourceDirPath.relativize(sourcePath).toString();
                Path targetPath = Paths.get(targetDir, relativePath);

                // 确保目标目录存在
                Files.createDirectories(targetPath.getParent());

                // 读取GBK编码的文件
                String content = new String(Files.readAllBytes(sourcePath), "GBK");

                // 写入UTF-8编码的文件
                Files.write(targetPath, content.getBytes(StandardCharsets.UTF_8));

                System.out.println("Converted: " + sourcePath + " -> " + targetPath);
            }

            System.out.println("All files converted successfully!");
        } catch (Exception e) {
            System.err.println("Error during conversion: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
