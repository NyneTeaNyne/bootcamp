package com.mentoring.bootcamp.ordermanager.common.tests.unit.mail;

import com.mentoring.bootcamp.ordermanager.common.mail.MailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MailServiceTest {

    @Test
    void should_send_a_plain_text_email_from_the_configured_sender() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MailService mailService = new MailService(mailSender, "no-reply@decathlon.com");

        mailService.sendMail("alice@decathlon.com", "Order Confirmation", "Your order number : 42 has been created");

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly("alice@decathlon.com");
        assertThat(sent.getValue().getFrom()).isEqualTo("no-reply@decathlon.com");
        assertThat(sent.getValue().getSubject()).isEqualTo("Order Confirmation");
        assertThat(sent.getValue().getText()).isEqualTo("Your order number : 42 has been created");
    }
}
