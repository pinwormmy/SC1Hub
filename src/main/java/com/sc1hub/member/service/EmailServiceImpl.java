package com.sc1hub.member.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;


@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender emailSender;

    public EmailServiceImpl(@Lazy JavaMailSender emailSender) {
        this.emailSender = emailSender;
    }

    public void sendNewPasswordMessage(String recipient, String newPassword) throws Exception {
        MimeMessage message = createNewPasswordMessage(recipient, newPassword);

        try {
            emailSender.send(message);
        } catch (MailException e) {
            log.error("Email sending error", e);
            throw new IllegalArgumentException();
        }
    }

    private MimeMessage createNewPasswordMessage(String recipient, String newPassword) throws Exception {
        MimeMessage message = emailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "utf-8");

        helper.setTo(recipient);
        helper.setSubject("SC1Hub의 새 임시 비밀번호 발급");
        helper.setText(generateNewPasswordEmailContent(newPassword), true);
        helper.setFrom(new InternetAddress("mealchelin@gmail.com", "admin"));

        return message;
    }

    private String generateNewPasswordEmailContent(String newPassword) {

        return "<h2>SC1Hub의 임시 비밀번호입니다</h2>" +
                "<p>아래 임시 비밀번호로 로그인해 주 세요: <strong>" +
                newPassword +
                "</strong></p>" +
                "<p>로그인 후 비밀번호를 변경해 주세요.</p>";
    }


}
