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

    public String storeDetainMedia(Long detainId, MultipartFile file) throws IOException {
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
        Path dir = Paths.get(uploadDir, "detain", String.valueOf(detainId)).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String name = System.currentTimeMillis() + ext;
        Path target = dir.resolve(name).normalize();
        if (!target.startsWith(dir)) {
            throw new RuntimeException("非法路径");
        }
        file.transferTo(target);
        return "detain/" + detainId + "/" + name;
    }

    public String copyToDetainMedia(Long detainId, String sourceRelativePath) {
        if (detainId == null || sourceRelativePath == null || sourceRelativePath.isBlank()) {
            return null;
        }
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path source = root.resolve(sourceRelativePath).normalize();
        if (!source.startsWith(root) || !Files.isRegularFile(source)) {
            return null;
        }
        String name = source.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot).toLowerCase() : "";
        if (!List.of(".jpg", ".jpeg", ".png", ".webp").contains(ext)) {
            return null;
        }
        try {
            Path dir = root.resolve(Paths.get("detain", String.valueOf(detainId))).normalize();
            if (!dir.startsWith(root)) {
                return null;
            }
            Files.createDirectories(dir);
            String destName = System.currentTimeMillis() + ext;
            Path target = dir.resolve(destName).normalize();
            if (!target.startsWith(dir)) {
                return null;
            }
            Files.copy(source, target);
            return "detain/" + detainId + "/" + destName;
        } catch (IOException e) {
            throw new RuntimeException("文件复制失败");
        }
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
