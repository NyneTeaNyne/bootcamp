package com.mentoring.bootcamp.ordermanager.common.autoconfigure;

import com.mentoring.bootcamp.ordermanager.common.mail.MailService;
import com.mentoring.bootcamp.ordermanager.common.mail.MailUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Provides a {@link MailUseCase} as soon as Spring Mail is configured (spring.mail.host).
 * The sender address can be changed with the bootcamp.mail.sender property.
 */
@AutoConfiguration(afterName = "org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.mail.javamail.JavaMailSender")
public class CommonMailAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MailUseCase.class)
    @ConditionalOnBean(JavaMailSender.class)
    public MailService mailService(JavaMailSender mailSender,
                                   @Value("${bootcamp.mail.sender:" + MailService.DEFAULT_SENDER + "}") String sender) {
        return new MailService(mailSender, sender);
    }
}
