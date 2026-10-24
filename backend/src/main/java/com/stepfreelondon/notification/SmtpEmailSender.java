package com.stepfreelondon.notification;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** SMTP via Spring Mail. Locally this is Mailpit (docker-compose), which catches every message. */
@Component
class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    SmtpEmailSender(JavaMailSender mailSender, NotificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.fromAddress());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
