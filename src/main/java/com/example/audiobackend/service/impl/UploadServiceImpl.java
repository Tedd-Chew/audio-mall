package com.example.audiobackend.service.impl;

import com.example.audiobackend.service.UploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UploadServiceImpl implements UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadServiceImpl.class);

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "webp");

    @Value("${upload.base-path:./uploads}")
    private String uploadBasePath;

    @Value("${upload.home-path:home}")
    private String homePath;

    @Value("${upload.product-path:product}")
    private String productPath;

    @Override
    public String uploadHomeImage(MultipartFile file) {
        validateFile(file);

        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String filename = timestamp + "." + extension;

        Path uploadPath = Paths.get(uploadBasePath, homePath);
        ensureDirectoryExists(uploadPath);

        Path filePath = uploadPath.resolve(filename);
        saveFile(file, filePath);

        String url = "/uploads/" + homePath + "/" + filename;
        log.info("上传主页图成功: {}", url);
        return url;
    }

    @Override
    public String uploadProductCover(String categoryName, Long productId, MultipartFile file) {
        validateFile(file);
        validateParams(categoryName, productId);

        Path productDir = Paths.get(uploadBasePath, productPath, categoryName, String.valueOf(productId));
        ensureDirectoryExists(productDir);

        Path coverPath = productDir.resolve("main.jpg");

        String url = "/uploads/" + productPath + "/" + categoryName + "/" + productId + "/main.jpg";

        if (Files.exists(coverPath)) {
            log.warn("产品封面已存在，将被覆盖: {}", url);
        }

        saveFile(file, coverPath);
        log.info("上传产品封面成功: {}", url);
        return url;
    }

    @Override
    public String[] uploadProductImages(String categoryName, Long productId, MultipartFile[] files) {
        validateParams(categoryName, productId);

        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("请选择要上传的图片");
        }

        Path productDir = Paths.get(uploadBasePath, productPath, categoryName, String.valueOf(productId));
        ensureDirectoryExists(productDir);

        int startIndex = findNextAvailableIndex(productDir);

        String[] urls = new String[files.length];

        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];
            validateFile(file);

            String filename = String.format("%03d.jpg", startIndex + i);
            Path filePath = productDir.resolve(filename);
            saveFile(file, filePath);

            urls[i] = "/uploads/" + productPath + "/" + categoryName + "/" + productId + "/" + filename;
            log.info("上传产品详情图成功: {}", urls[i]);
        }

        return urls;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小超过限制（最大10MB）");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String extension = getFileExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new IllegalArgumentException("不支持的文件格式，仅支持: " + String.join(", ", ALLOWED_EXTENSIONS));
        }
    }

    private void validateParams(String categoryName, Long productId) {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            throw new IllegalArgumentException("分类名称无效");
        }
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("产品ID无效");
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    private void ensureDirectoryExists(Path path) {
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.debug("创建目录: {}", path);
            }
        } catch (IOException e) {
            throw new RuntimeException("创建目录失败: " + path, e);
        }
    }

    private void saveFile(MultipartFile file, Path filePath) {
        try {
            Files.copy(file.getInputStream(), filePath);
        } catch (IOException e) {
            throw new RuntimeException("保存文件失败: " + filePath, e);
        }
    }

    private int findNextAvailableIndex(Path directory) {
        int index = 1;

        try {
            if (Files.exists(directory)) {
                List<String> existingFiles = Files.list(directory)
                        .filter(Files::isRegularFile)
                        .map(p -> p.getFileName().toString())
                        .filter(name -> name.matches("\\d{3}\\.jpg"))
                        .sorted()
                        .collect(Collectors.toList());

                for (String filename : existingFiles) {
                    String numStr = filename.substring(0, 3);
                    int num = Integer.parseInt(numStr);
                    if (num >= index) {
                        index = num + 1;
                    }
                }
            }
        } catch (IOException e) {
            log.warn("获取已有文件列表失败，从1开始: {}", e.getMessage());
        }

        return index;
    }
}