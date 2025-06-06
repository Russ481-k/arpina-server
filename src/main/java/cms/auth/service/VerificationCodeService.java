package cms.auth.service;

public interface VerificationCodeService {
    String generateAndStoreCode(String email);

    boolean verifyCode(String email, String code);
}