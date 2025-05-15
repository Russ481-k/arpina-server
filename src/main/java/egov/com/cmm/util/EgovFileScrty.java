package egov.com.cmm.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class EgovFileScrty {
    
    /**
     * 비밀번호를 암호화하는 기능(복호화가 되면 안되므로 SHA-256 인코딩 방식 적용)
     * 
     * @param password 암호화될 패스워드
     * @param id salt로 사용될 사용자 ID 지정
     * @return 암호화된 패스워드
     */
    public static String encryptPassword(String password, String id) {
        if (password == null || id == null) {
            throw new IllegalArgumentException("Password or ID should not be null");
        }
        
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((password + id).getBytes());
            
            byte[] byteData = md.digest();
            StringBuilder sb = new StringBuilder();
            
            for (byte byteDatum : byteData) {
                sb.append(Integer.toString((byteDatum & 0xff) + 0x100, 16).substring(1));
            }
            
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to encrypt password", e);
        }
    }
} 