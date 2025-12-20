package com.sc1hub.member.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Random;


@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    @Autowired
    private JavaMailSender emailSender;

    private MimeMessage createMessage(String recipient, String verificationCode) throws Exception {
        log.info("Recipient: {}", recipient);
        log.info("Verification code: {}", verificationCode);
        MimeMessage message = emailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "utf-8");

        helper.setTo(recipient);
        helper.setSubject("SC1Hub 이메일 인증");
        helper.setText(generateEmailContent(verificationCode), true);
        helper.setFrom(new InternetAddress("mealchelin@gmail.com", "admin"));

        return message;
    }

    private String generateEmailContent(String verificationCode) {

        return "<div style='margin:20px;'>" +
                "<h2> 안녕하세요 SC1Hub 이메일 인증입니다. </h2>" +
                "<br>" +
                "<p>아래 코드를 회원가입 이메일 인증칸에 입력해주세요<p>" +
                "<br>" +
                "<p>감사합니다.<p>" +
                "<br>" +
                "<div align='center' style='border:1px solid black; font-family:verdana';>" +
                "<h3 style='color:blue;'>이메일 인증코드</h3>" +
                "<div style='font-size:130%'>" +
                "CODE : <strong>" +
                verificationCode +
                "</strong><div><br/> " +
                "</div>";
    }

    public static String createVerificationCode() {
        StringBuilder key = new StringBuilder();
        Random rnd = new Random();

        for (int i = 0; i < 8; i++) {
            int index = rnd.nextInt(3);

            switch (index) {
                case 0:
                    key.append((char) (rnd.nextInt(26) + 97));
                    break;
                case 1:
                    key.append((char) (rnd.nextInt(26) + 65));
                    break;
                case 2:
                    key.append(rnd.nextInt(10));
                    break;
            }
        }

        return key.toString();
    }

    @Override
    public String sendSimpleMessage(String recipient) throws Exception {
        String verificationCode = createVerificationCode();
        MimeMessage message = createMessage(recipient, verificationCode);

        try {
            emailSender.send(message);
        } catch (MailException e) {
            log.error("Email sending error", e);
            throw new IllegalArgumentException();
        }

        return verificationCode;
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