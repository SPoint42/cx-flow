package com.custodela.machina.service;

import com.custodela.machina.config.MachinaProperties;
import com.custodela.machina.utils.ScanUtils;
import org.slf4j.Logger;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.validation.constraints.NotNull;
import java.beans.ConstructorProperties;
import java.util.List;
import java.util.Map;

@Service
public class EmailService {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(EmailService.class);
    private final MachinaProperties properties;
    private final TemplateEngine templateEngine;
    private final JavaMailSender emailSender;

    @ConstructorProperties({"properties", "templateEngine", "emailSender"})
    public EmailService(MachinaProperties properties, TemplateEngine templateEngine, JavaMailSender emailSender) {
        this.properties = properties;
        this.templateEngine = templateEngine;
        this.emailSender = emailSender;
    }

    /**
     * Send email
     *
     * @param recipients
     * @param subject
     * @param ctx
     * @param template
     */
    public void sendmail(List<String> recipients, @NotNull String subject, @NotNull Map<String, Object> ctx, String template){
        log.info("Sending email");
        if(ScanUtils.empty(recipients)){
            log.warn("Email not sent - no recipients listed");
            return;
        }
        MimeMessagePreparator messagePreparator = mimeMessage -> {
            MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage);
            messageHelper.setFrom(properties.getContact());
            messageHelper.setTo(recipients.toArray(new String[0]));
            messageHelper.setSubject(subject);
            String content = generateContent(ctx, template);
            messageHelper.setText(content, true);
        };
        try {
            if(properties.getMail().isEnabled()) {
                emailSender.send(messagePreparator);
            }
        } catch (MailException e) {
            log.error("Error occured while attempting to send an email",e);
        }


    }

    /**
     * Generate HTML content for email
     *
     * @param ctx
     * @param template
     * @return
     */
    private String generateContent(Map<String, Object> ctx, String template) {
        Context context = new Context();
        context.setVariables(ctx);
        return templateEngine.process(template, context);
    }
}

