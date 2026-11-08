package service;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

public class GmailSender {

    public void sendCode(String recipientEmail, String code)
            throws Exception {

        String senderEmail = System.getenv("MAIL_SENDER_EMAIL");
        String appPassword = System.getenv("MAIL_APP_PASSWORD");

        if (senderEmail == null || senderEmail.isBlank()
                || appPassword == null || appPassword.isBlank()) {
            throw new IllegalStateException(
                    "메일 발송 환경변수가 설정되지 않았습니다."
            );
        }

        Properties properties = new Properties();
        properties.put("mail.smtp.host", "smtp.gmail.com");
        properties.put("mail.smtp.port", "587");
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.starttls.required", "true");
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");

        Session session = Session.getInstance(
                properties,
                new Authenticator() {
                    @Override
                    protected PasswordAuthentication
                            getPasswordAuthentication() {
                        return new PasswordAuthentication(
                                senderEmail,
                                appPassword
                        );
                    }
                }
        );

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(senderEmail));
        message.setRecipient(
                Message.RecipientType.TO,
                new InternetAddress(recipientEmail, true)
        );
        message.setSubject(
                "Java Chat 이메일 인증번호",
                "UTF-8"
        );
        message.setText(
                "인증번호: " + code
                + "\n요청하지 않았다면 이 메일을 무시하세요.",
                "UTF-8"
        );

        Transport.send(message);
    }
}