package egov.com.cmm.util;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EgovFileUploadUtil {
    
    public static String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(lastDotIndex + 1).toLowerCase() : "";
    }
} 