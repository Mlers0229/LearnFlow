package com.learnflow.service;

import com.learnflow.config.LearnFlowAuthProperties;
import com.learnflow.entity.User;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;

@Service
public class SmtpPasswordResetDelivery implements PasswordResetDelivery {

    private final JavaMailSender mailSender;
    private final LearnFlowAuthProperties properties;
    private final String smtpHost;

    public SmtpPasswordResetDelivery(JavaMailSender mailSender,
                                     LearnFlowAuthProperties properties,
                                     @Value("${spring.mail.host:}") String smtpHost) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.smtpHost = smtpHost;
    }

    @PostConstruct
    void validateProductionDelivery() {
        if (!"production".equalsIgnoreCase(properties.getEnvironment()) || !properties.isPasswordResetEnabled()) {
            return;
        }
        if (!properties.getPasswordResetBaseUrl().startsWith("https://")) {
            throw new IllegalStateException("Production password reset URL must use HTTPS");
        }
        if (smtpHost.isBlank() || "localhost".equalsIgnoreCase(smtpHost)) {
            throw new IllegalStateException("Production password reset requires an injected SMTP host");
        }
    }

    @Override
    public void send(User user, String rawToken) {
        String resetUrl = UriComponentsBuilder.fromUriString(properties.getPasswordResetBaseUrl())
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getPasswordResetMailFrom());
        message.setTo(user.getEmail());
        message.setSubject("LearnFlow 密码重置");
        message.setText("你好，" + user.getUsername() + "。\n\n请在有效期内打开以下链接重置密码：\n" + resetUrl
                + "\n\n如果不是你发起的请求，请忽略此邮件。");
        mailSender.send(message);
    }
}
