package com.sc1hub.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class EmailConfig {

    private final int port;
    private final int socketPort;
    private final boolean auth;
    private final boolean startTls;
    private final boolean startTlsRequired;
    private final boolean fallback;
    private final String id;
    private final String password;

    public EmailConfig(
            @Value("${mail.smtp.port}") int port,
            @Value("${mail.smtp.socketFactory.port}") int socketPort,
            @Value("${mail.smtp.auth}") boolean auth,
            @Value("${mail.smtp.starttls.enable}") boolean startTls,
            @Value("${mail.smtp.starttls.required}") boolean startTlsRequired,
            @Value("${mail.smtp.socketFactory.fallback}") boolean fallback,
            @Value("${AdminMail.id}") String id,
            @Value("${AdminMail.password}") String password) {
        this.port = port;
        this.socketPort = socketPort;
        this.auth = auth;
        this.startTls = startTls;
        this.startTlsRequired = startTlsRequired;
        this.fallback = fallback;
        this.id = id;
        this.password = password;
    }

    @Bean
    public JavaMailSender javaMailService() {
        JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();
        javaMailSender.setHost("smtp.gmail.com");
        javaMailSender.setUsername(id);
        javaMailSender.setPassword(password);
        javaMailSender.setPort(port);
        javaMailSender.setJavaMailProperties(getMailProperties());
        javaMailSender.setDefaultEncoding("UTF-8");
        return javaMailSender;
    }
    private Properties getMailProperties()
    {
        Properties pt = new Properties();
        pt.put("mail.smtp.socketFactory.port", socketPort);
        pt.put("mail.smtp.auth", auth);
        pt.put("mail.smtp.starttls.enable", startTls);
        pt.put("mail.smtp.starttls.required", startTlsRequired);
        pt.put("mail.smtp.socketFactory.fallback",fallback);
        pt.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        return pt;
    }
}
