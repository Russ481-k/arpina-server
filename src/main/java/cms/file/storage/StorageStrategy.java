package cms.file.storage;

import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;

public interface StorageStrategy {
    String upload(MultipartFile file, String path);
    InputStream download(String path);
    void delete(String path);
    String getUrl(String path);
} 