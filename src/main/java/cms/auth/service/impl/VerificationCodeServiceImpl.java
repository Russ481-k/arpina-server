package cms.auth.service.impl;

import cms.auth.service.VerificationCodeService;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class VerificationCodeServiceImpl implements VerificationCodeService {

    private static final int CODE_LENGTH = 6;
    private static final long EXPIRATION_MINUTES = 3;
    private final Map<String, VerificationInfo> codeStore = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Override
    public String generateAndStoreCode(String email) {
        String code = generateRandomCode();
        long expirationTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(EXPIRATION_MINUTES);
        codeStore.put(email, new VerificationInfo(code, expirationTime));

        // Schedule removal of expired code
        scheduler.schedule(() -> codeStore.remove(email, new VerificationInfo(code, expirationTime)),
                EXPIRATION_MINUTES, TimeUnit.MINUTES);

        return code;
    }

    @Override
    public boolean verifyCode(String email, String code) {
        VerificationInfo info = codeStore.get(email);
        if (info == null) {
            return false; // No code found or already expired and removed
        }

        if (System.currentTimeMillis() > info.getExpirationTime()) {
            codeStore.remove(email); // Clean up expired code
            return false; // Code expired
        }

        if (info.getCode().equals(code)) {
            codeStore.remove(email); // Code is correct, remove it after verification
            return true;
        }

        return false;
    }

    private String generateRandomCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    private static class VerificationInfo {
        private final String code;
        private final long expirationTime;

        public VerificationInfo(String code, long expirationTime) {
            this.code = code;
            this.expirationTime = expirationTime;
        }

        public String getCode() {
            return code;
        }

        public long getExpirationTime() {
            return expirationTime;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            VerificationInfo that = (VerificationInfo) o;
            return expirationTime == that.expirationTime && code.equals(that.code);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(code, expirationTime);
        }
    }
}