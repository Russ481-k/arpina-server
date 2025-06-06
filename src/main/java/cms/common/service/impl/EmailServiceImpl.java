package cms.common.service.impl;

import cms.common.service.EmailService;
import cms.common.exception.EmailSendingException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;

import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Async
    @Override
    public void sendUserIdEmail(String toEmail, String userId, String name) throws EmailSendingException {
        String subject = "[Arpina] 요청하신 아이디 안내입니다.";
        log.info("Attempting to send User ID email to: {}, userId: {}, name: {}", toEmail, userId, name);

        Context context = new Context();
        context.setVariable("name", name); // 템플릿에서 ${name}으로 사용
        context.setVariable("username", userId); // 템플릿에서 ${username}으로 사용
        log.debug("Thymeleaf context for User ID email: name={}, username={}", name, userId);

        String htmlBody = "";
        try {
            htmlBody = templateEngine.process("email/find-id-notification", context);
            log.debug("Generated HTML body for User ID email (first 100 chars): {}",
                    htmlBody.substring(0, Math.min(htmlBody.length(), 100)));
        } catch (Exception e) {
            log.error("Error processing Thymeleaf template 'email/find-id-notification' for {}: {}", toEmail,
                    e.getMessage(), e);
            throw new EmailSendingException("아이디 안내 메일 내용 생성 중 오류가 발생했습니다.", "TEMPLATE_PROCESSING_ERROR", e);
        }

        // htmlBody가 비어있는 경우에 대한 방어 코드 (필요시)
        if (htmlBody == null || htmlBody.trim().isEmpty()) {
            log.warn(
                    "Generated HTML body for User ID email is empty for {}. Check template 'email/find-id-notification'.",
                    toEmail);
            // 비어있는 메일을 보내는 대신 오류를 발생시키거나 기본 메시지를 사용할 수 있습니다.
            // throw new EmailSendingException("생성된 메일 내용이 비어있습니다.", "EMPTY_HTML_BODY");
            // 또는, 기본 텍스트 메일로 대체:
            // htmlBody = name + "님의 아이디는 " + userId + " 입니다.";
            // sendEmail(toEmail, subject, htmlBody, false); // false는 HTML이 아님을 의미
            // (sendEmail 메소드 수정 필요)
        }

        sendEmail(toEmail, subject, htmlBody);
    }

    @Async
    @Override
    public void sendTemporaryPasswordEmail(String toEmail, String temporaryPassword, String name)
            throws EmailSendingException {
        String subject = "[Arpina] 임시 비밀번호 안내입니다.";

        Context context = new Context();
        context.setVariable("name", name);
        context.setVariable("temporaryPassword", temporaryPassword);
        String htmlBody = templateEngine.process("email/reset-password-notification", context);

        sendEmail(toEmail, subject, htmlBody);
        // System.out.println("메일 발송 완료 To: " + toEmail + " Subject: " + subject);
    }

    @Async
    @Override
    public void sendVerificationEmail(String toEmail, String code) throws EmailSendingException {
        String subject = "[Arpina] 이메일 인증 코드 안내입니다.";

        Context context = new Context();
        context.setVariable("verificationCode", code);
        String htmlBody = templateEngine.process("email/email-verification", context);

        sendEmail(toEmail, subject, htmlBody);
    }

    private void sendEmail(String to, String subject, String htmlBody) throws EmailSendingException {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            log.debug("Attempting to set recipient email for '{}': '[{}]', length: {}", subject, to,
                    to != null ? to.length() : "null");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent successfully to: {} with subject: {}", to, subject);
        } catch (AddressException e) {
            log.error("Failed to send email to '{}' due to invalid address. Subject: {}", to, subject, e);
            throw new EmailSendingException("수신자 이메일 주소(" + to + ") 형식이 올바르지 않습니다.", "INVALID_EMAIL_ADDRESS", e);
        } catch (MessagingException e) {
            log.error("Failed to send email to '{}' due to MessagingException. Subject: {}. Error: {}", to, subject,
                    e.getMessage(), e);
            String errorCode = "EMAIL_SEND_FAILURE";
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("authentication failed")) {
                errorCode = "SMTP_AUTHENTICATION_FAILED";
            }
            throw new EmailSendingException("메일 발송 중 오류가 발생했습니다: " + e.getMessage(), errorCode, e);
        } catch (Exception e) {
            log.error("An unexpected error occurred while sending email to '{}'. Subject: {}. Error: {}", to, subject,
                    e.getMessage(), e);
            throw new EmailSendingException("메일 발송 중 예상치 못한 오류가 발생했습니다: " + e.getMessage(), "UNEXPECTED_EMAIL_ERROR",
                    e);
        }
    }
}