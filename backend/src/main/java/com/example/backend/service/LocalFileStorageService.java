package com.example.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class LocalFileStorageService {

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    public String storeDispatchMedia(Long orderId, MultipartFile file) throws IOException {
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) {
            ext = original.substring(dot).toLowerCase();
        }
        if (!List.of(".jpg", ".jpeg", ".png", ".webp").contains(ext)) {
            throw new RuntimeException("仅支持 jpg/png/webp");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new RuntimeException("文件不能超过5MB");
        }
        Path dir = Paths.get(uploadDir, "dispatch", String.valueOf(orderId)).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String name = System.currentTimeMillis() + ext;
        Path target = dir.resolve(name).normalize();
        if (!target.startsWith(dir)) {
            throw new RuntimeException("非法路径");
        }
        file.transferTo(target);
        return "dispatch/" + orderId + "/" + name;
    }

    public void deleteDispatchMedia(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new RuntimeException("非法路径");
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new RuntimeException("文件删除失败");
        }
    }
}
