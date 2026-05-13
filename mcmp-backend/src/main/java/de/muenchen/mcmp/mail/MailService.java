package de.muenchen.mcmp.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.nio.charset.StandardCharsets;

/**
 * Service for sending emails via Spring's {@link JavaMailSender}.
 * <p>
 * Supports HTML/plain text bodies as well as optional CC/BCC recipients and attachments.
 * Email dispatch is performed asynchronously.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender javaMailSender;
    public static final String DEFAULT_ATTACHMENT_MIME_TYPE = "application/octet-stream";

    @Value("${spring.mail.username}")
    private String sender;

    /**
     * Sends an email asynchronously based on the provided {@link MailDTO}.
     * <p>
     * The message is built as a multipart email to support attachments. Optional CC/BCC recipients
     * and attachments are only applied if present.
     *
     * @param mailDTO the mail payload containing recipients, subject, content and optional metadata
     */
    @Async
    public void sendEmail(final MailDTO mailDTO) {
        log.info("Preparing to send email to: {} with subject: {}", mailDTO.getTo(), mailDTO.getSubject());

        try {
            final MimeMessage message = javaMailSender.createMimeMessage();
            final MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            helper.setFrom(sender);
            helper.setTo(mailDTO.getTo().toArray(new String[0]));
            helper.setSubject(mailDTO.getSubject());

            helper.setText(mailDTO.getContent(), mailDTO.isHtml());

            if (!CollectionUtils.isEmpty(mailDTO.getCc())) {
                helper.setCc(mailDTO.getCc().toArray(new String[0]));
            }

            if (!CollectionUtils.isEmpty(mailDTO.getBcc())) {
                helper.setBcc(mailDTO.getBcc().toArray(new String[0]));
            }

            if (!CollectionUtils.isEmpty(mailDTO.getAttachments())) {
                for (final MailDTO.Attachment attachment : mailDTO.getAttachments()) {
                    helper.addAttachment(
                            attachment.getFilename(),
                            attachment.toResource(),
                            attachment.getMimeType() != null ? attachment.getMimeType() : DEFAULT_ATTACHMENT_MIME_TYPE
                    );
                }
            }

            javaMailSender.send(message);
            log.info("Email sent successfully to {}", mailDTO.getTo());

        } catch (MessagingException e) {
            log.error("Failed to send email to {}", mailDTO.getTo(), e);
        }
    }
}