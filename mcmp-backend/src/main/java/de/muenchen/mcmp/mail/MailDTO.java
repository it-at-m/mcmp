package de.muenchen.mcmp.mail;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import org.springframework.core.io.ByteArrayResource;

import java.util.List;
import java.util.Set;

@Getter
@Builder
public class MailDTO {

    @Singular("to")
    private Set<String> to;

    @Singular("cc")
    private Set<String> cc;

    @Singular("bcc")
    private Set<String> bcc;

    private String subject;

    private String content;

    @Builder.Default
    private boolean isHtml = false;

    @Singular
    private List<Attachment> attachments;

    @Getter
    @Builder
    public static class Attachment {
        private String filename;
        private byte[] data;
        private String mimeType;

        public ByteArrayResource toResource() {
            return new ByteArrayResource(data);
        }
    }
}