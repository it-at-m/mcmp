package de.muenchen.mcmp.errorlog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;


@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorLogService {

    /**
     * Stacktraces are truncated before persisting so unbounded traces don't bloat the
     * table. Truncation is applied per "Caused by:" block rather than to the flattened
     * text, so that the root cause of a wrapped/repackaged exception is never pushed
     * out by a long chain of frames in the outer exception.
     */
    private static final int MAX_LINES_PER_CAUSE = 8;
    private static final int MAX_CAUSES = 4;

    /** Caps how much of a request body is persisted, so a large payload can't bloat the table. */
    private static final int MAX_BODY_LENGTH = 2000;

    /**
     * Matches JSON string fields whose key looks like it holds a credential (password, secret,
     * token, api key, ...), so their value can be redacted before the request body is persisted.
     * Request bodies across the app can legitimately contain such fields (e.g. integration config
     * forms with API passwords), and the error log must never become a place secrets leak into.
     */
    private static final Pattern SENSITIVE_FIELD_PATTERN = Pattern.compile(
            "(?i)\"(\\w*(?:password|secret|token|apikey|credential)\\w*)\"\\s*:\\s*\"(?:[^\"\\\\]|\\\\.)*\"");

    private final ErrorLogRepository repository;

    @Value("${error-log.retention-days:30}")
    private int retentionDays;


    @Transactional(readOnly = true)
    public Page<ErrorLogSummary> getErrorLogs(final Pageable pageable) {
        return repository.findAllBy(pageable);
    }

    @Transactional(readOnly = true)
    public Optional<ErrorLogSummary> findSummaryByReference(final String reference) {
        Long id = ErrorLogReference.parse(reference);
        if (id == null) {
            return Optional.empty();
        }
        return repository.findSummaryById(id);
    }

    @Transactional(readOnly = true)
    public Optional<ErrorLog> getErrorLogDetail(final Long id) {
        return repository.findById(id);
    }

    /**
     * Persists a captured error in its own transaction, independent of the
     * (likely rolled-back) transaction that triggered it.
     * Never propagates a failure, since this runs inside exception handling
     * and must not mask the original error.
     *
     * @param exception the exception that was caught
     * @param requestMethod the HTTP method of the request that failed, if known
     * @param requestPath the request URI that failed, if known
     * @param requestQuery the request's query string, if any
     * @param requestBody the request's JSON body, if any and if it was captured (see
     *        {@code RequestBodyCachingFilter}); redacted and truncated before being persisted
     * @param username the authenticated user at the time of failure, if known
     * @return the id of the persisted error log entry, which doubles as a reference number
     *         users can pass on to administrators; {@code null} if persistence failed
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long logError(final Throwable exception, final String requestMethod, final String requestPath,
            final String requestQuery, final String requestBody, final String username) {
        try {
            ErrorLog errorLog = new ErrorLog();
            errorLog.setExceptionClass(exception.getClass().getName());
            errorLog.setMessage(exception.getMessage());
            errorLog.setStacktrace(compress(truncateStacktrace(ExceptionUtils.getStackTrace(exception))));
            errorLog.setRequestMethod(requestMethod);
            errorLog.setRequestPath(requestPath);
            errorLog.setRequestQuery(truncateChars(requestQuery));
            errorLog.setRequestBody(truncateChars(redactSensitiveFields(requestBody)));
            errorLog.setUsername(username);
            return repository.save(errorLog).getId();
        } catch (Exception persistenceException) {
            log.error("Failed to persist error log entry", persistenceException);
            return null;
        }
    }

    /**
     * Deletes all error log entries older than the configured retention period
     * ({@code error-log.retention-days}, default 30 days).
     */
    @Transactional
    public void cleanupOldEntries() {
        Date threshold = Date.from(Instant.now().minus(Duration.ofDays(retentionDays)));
        repository.deleteByCreatedAtBefore(threshold);
    }

    /**
     * Truncates a full stacktrace (as produced by {@link ExceptionUtils#getStackTrace}) by
     * splitting it into its "Caused by:" blocks and keeping only the first
     * {@value #MAX_LINES_PER_CAUSE} lines of each of the first {@value #MAX_CAUSES} causes.
     * This guarantees the root cause of a wrapped exception is always represented, instead of
     * being pushed out of a flat line budget by a long outer frame chain.
     */
    private static String truncateStacktrace(final String fullStacktrace) {
        String[] blocks = fullStacktrace.split("(?m)^(?=Caused by:)");
        StringBuilder result = new StringBuilder();
        int included = Math.min(blocks.length, MAX_CAUSES);
        for (int i = 0; i < included; i++) {
            result.append(truncateLines(blocks[i]));
            if (!result.toString().endsWith("\n")) {
                result.append("\n");
            }
        }
        if (blocks.length > MAX_CAUSES) {
            result.append("\t... (").append(blocks.length - MAX_CAUSES).append(" further cause(s) omitted)");
        }
        return result.toString().strip();
    }

    /**
     * Replaces the value of any JSON field whose key looks like it holds a credential with
     * {@code "***"}, so request bodies can be safely persisted for debugging.
     */
    private static String redactSensitiveFields(final String json) {
        if (json == null) {
            return null;
        }
        return SENSITIVE_FIELD_PATTERN.matcher(json).replaceAll("\"$1\":\"***\"");
    }

    /**
     * Truncates the given text to at most {@code maxChars} characters.
     */
    private static String truncateChars(final String text) {
        if (text == null || text.length() <= ErrorLogService.MAX_BODY_LENGTH) {
            return text;
        }
        return text.substring(0, ErrorLogService.MAX_BODY_LENGTH) + "...";
    }

    /**
     * Keeps only the first {@code maxLines} lines of the given text.
     */
    private static String truncateLines(final String text) {
        String[] lines = text.split("\n", ErrorLogService.MAX_LINES_PER_CAUSE + 1);
        if (lines.length <= ErrorLogService.MAX_LINES_PER_CAUSE) {
            return text;
        }
        return String.join("\n", Arrays.copyOf(lines, ErrorLogService.MAX_LINES_PER_CAUSE)) + "\n\t...";
    }

    /**
     * Gzip-compresses the given text. Falls back to raw UTF-8 bytes if compression fails.
     */
    private static byte[] compress(final String text) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
            gzip.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            log.warn("Failed to compress stacktrace, storing uncompressed", e);
            return text.getBytes(StandardCharsets.UTF_8);
        }
    }
}
