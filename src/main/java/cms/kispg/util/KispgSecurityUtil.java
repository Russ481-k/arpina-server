package cms.kispg.util;

import cms.kispg.dto.KispgNotificationRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
@RequiredArgsConstructor
public class KispgSecurityUtil {

    private static final Logger logger = LoggerFactory.getLogger(KispgSecurityUtil.class);
    
    @Value("${kispg.merchantKey}")
    private String merchantKey;

    /**
     * KISPG 결제 결과 통지의 해시값을 검증합니다.
     * 
     * @param notification KISPG 통지 데이터
     * @return 검증 성공 여부
     */
    public boolean verifyNotificationHash(KispgNotificationRequest notification) {
        if (notification.getEncData() == null || notification.getEncData().trim().isEmpty()) {
            logger.warn("KISPG notification encData is null or empty");
            return false;
        }

        try {
            // KISPG 문서에 따른 해시 생성 규칙 (예시)
            // 실제 규칙은 KISPG 문서를 확인해야 함
            String dataToHash = notification.getMid() + 
                                notification.getTid() + 
                                notification.getMoid() + 
                                notification.getAmt() + 
                                merchantKey;
            
            String calculatedHash = generateSHA256Hash(dataToHash);
            boolean isValid = calculatedHash.equalsIgnoreCase(notification.getEncData());
            
            if (!isValid) {
                logger.warn("KISPG hash verification failed. Expected: {}, Received: {}", 
                    calculatedHash, notification.getEncData());
            }
            
            return isValid;
        } catch (Exception e) {
            logger.error("Error during KISPG hash verification", e);
            return false;
        }
    }

    /**
     * 취소 요청을 위한 해시값을 생성합니다.
     * 
     * @param mid 상점 ID
     * @param ediDate 전문 생성일시
     * @param cancelAmount 취소 금액
     * @return 생성된 해시값
     */
    public String generateCancelHash(String mid, String ediDate, String cancelAmount) {
        String dataToHash = mid + ediDate + cancelAmount + merchantKey;
        return generateSHA256Hash(dataToHash);
    }

    /**
     * SHA-256 해시를 생성합니다.
     * 
     * @param data 해시할 데이터
     * @return 16진수 문자열로 변환된 해시값
     */
    private String generateSHA256Hash(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(data.getBytes());
            return Hex.encodeHexString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 algorithm not found", e);
            throw new RuntimeException("해시 생성 중 오류가 발생했습니다", e);
        }
    }
} 