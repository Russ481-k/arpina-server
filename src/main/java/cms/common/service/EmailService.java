package cms.common.service;

import cms.common.exception.EmailSendingException;

public interface EmailService {
    void sendUserIdEmail(String toEmail, String userId, String name) throws EmailSendingException;

    void sendTemporaryPasswordEmail(String toEmail, String temporaryPassword, String name) throws EmailSendingException;

    void sendVerificationEmail(String toEmail, String code) throws EmailSendingException;
}