package de.muenchen.mcmp.markdown;

import lombok.extern.slf4j.Slf4j;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
/**
 * Service for converting Markdown text to sanitized HTML.
 */
public class MarkdownService {

    private static final List<Extension> EXTENSIONS = Collections.singletonList(TablesExtension.create());

    private static final Parser PARSER = Parser.builder()
            .extensions(EXTENSIONS)
            .build();

    private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
            .extensions(EXTENSIONS)
            .build();

    private static final PolicyFactory SANITIZER = new HtmlPolicyBuilder()
            .allowElements("p", "b", "i", "em", "strong", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote", "br")
            .allowElements("table", "thead", "tbody", "tr", "th", "td")
            .allowAttributes("align").onElements("th", "td")
            .allowElements("a")
            .allowAttributes("href").onElements("a")
            .allowAttributes("target").matching(value -> "_blank".equals(value)).onElements("a")
            .allowElements("img")
            .allowAttributes("src", "alt", "title").onElements("img")
            .allowStandardUrlProtocols()
            .allowUrlProtocols("data")
            .requireRelNofollowOnLinks()
            .allowElements("pre", "code")
            .toFactory();

    /**
     * Converts Markdown to safe HTML.
     * @param markdown the Markdown string
     * @return sanitized HTML or null if input is null
     */
    public String convertToHtml(String markdown) {
        if (markdown == null) {
            return null;
        }

        final Node document = PARSER.parse(markdown);
        final String rawHtml = RENDERER.render(document);

        return SANITIZER.sanitize(rawHtml);
    }
}