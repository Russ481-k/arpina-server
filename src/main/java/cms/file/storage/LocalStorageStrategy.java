package cms.file.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Slf4j
@Component
public class LocalStorageStrategy implements StorageStrategy {

    @Value("${spring.file.storage.local.base-path}")
    private String basePath;

    @Override
    public String upload(MultipartFile file, String path) {
        try {
            Path targetPath = Paths.get(basePath, path);
            Files.createDirectories(targetPath.getParent());
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            return path;
        } catch (IOException e) {
            log.error("Failed to upload file: {}", path, e);
            throw new RuntimeException("Failed to upload file", e);
        }
    }

    @Override
    public InputStream download(String path) {
        try {
            Path filePath = Paths.get(basePath, path);
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            log.error("Failed to download file: {}", path, e);
            throw new RuntimeException("Failed to download file", e);
        }
    }

    @Override
    public void delete(String path) {
        try {
            Path filePath = Paths.get(basePath, path);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("Failed to delete file: {}", path, e);
            throw new RuntimeException("Failed to delete file", e);
        }
    }

    @Override
    public String getUrl(String path) {
        return "/files/" + path;
    }
} 