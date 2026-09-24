package com.mentoring.bootcamp.ordermanager.common.mail;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Sends plain-text emails through Spring Mail (SMTP server configured with spring.mail.*).
 */
public class MailService implements MailUseCase {
    public static final String DEFAULT_SENDER = "no-reply@decathlon.com";

    private final JavaMailSender mailSender;
    private final String sender;

    public MailService(JavaMailSender mailSender, String sender) {
        this.mailSender = mailSender;
        this.sender = sender;
    }

    @Override
    public void sendMail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        message.setFrom(sender);

        mailSender.send(message);
    }
}
