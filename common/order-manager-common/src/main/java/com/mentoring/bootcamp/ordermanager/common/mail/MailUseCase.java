package com.mentoring.bootcamp.ordermanager.common.mail;

public interface MailUseCase {
    void sendMail(String to, String subject, String body);
}
