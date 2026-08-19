package de.muenchen.mcmp.exception;

import de.muenchen.mcmp.clients.greenit.GreenITResponseDTO;
import de.muenchen.mcmp.errorlog.ErrorLogReference;
import de.muenchen.mcmp.errorlog.ErrorLogService;
import de.muenchen.mcmp.security.AuthUtils;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.WebUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.MissingFormatArgumentException;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
@AllArgsConstructor
public class GlobalExceptionHandler {

    private final ErrorLogService errorLogService;

    /**
     * Persists the technical details of an error and returns its reference id
     * (the {@code error_log} row's id), so it can be surfaced to the user without
     * exposing anything else about the error. Users can pass this id on to
     * administrators, who can look up the full details in the error log admin view.
     */
    private Long recordError(final Throwable ex, final HttpServletRequest request) {
        return errorLogService.logError(ex, request.getMethod(), request.getRequestURI(),
                request.getQueryString(), extractRequestBody(request), AuthUtils.getUsername());
    }

    /**
     * Reads the request body cached by {@code RequestBodyCachingFilter}. The body has
     * already been consumed by Spring MVC's argument resolvers (e.g. {@code @RequestBody}) by the
     * time an exception handler runs, so it can only be read back from that cache, not the
     * (single-read) input stream itself.
     * <p>
     * Other filters (e.g. the NFC-normalization filter) wrap the request again after ours runs,
     * so by the time it reaches here it's typically several {@link jakarta.servlet.ServletRequestWrapper}
     * layers deep and no longer an instance of {@link ContentCachingRequestWrapper} directly -
     * {@link WebUtils#getNativeRequest} unwraps through those layers to find it.
     */
    private static String extractRequestBody(final HttpServletRequest request) {
        ContentCachingRequestWrapper wrapper = WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
        if (wrapper != null) {
            byte[] content = wrapper.getContentAsByteArray();
            if (content.length > 0) {
                return new String(content, StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    /**
     * Appends the formatted, fixed-width reference (see {@link ErrorLogReference}) to a
     * user-facing message, if one was successfully recorded.
     */
    private static String withReference(final String message, final Long referenceId) {
        String reference = ErrorLogReference.format(referenceId);
        return reference != null ? message + " (Fehlercode: " + reference + ")" : message;
    }

    @ExceptionHandler(BusinessValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBusinessValidationException(final BusinessValidationException ex, HttpServletRequest request) {
        log.warn("Business validation failed: {}", ex.getMessage());
        Long referenceId = recordError(ex, request);
        return withReference(ex.getMessage(), referenceId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleIllegalArgumentException(final IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("IllegalArgumentException: {}", ex.getMessage(), ex);
        Long referenceId = recordError(ex, request);
        return withReference("Die Anfrage ist ungültig.", referenceId);
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNoSuchElementException(NoSuchElementException ex, HttpServletRequest request) {
        log.warn("NoSuchElementException: {}", ex.getMessage(), ex);
        Long referenceId = recordError(ex, request);
        return withReference("Die angeforderte Ressource wurde nicht gefunden.", referenceId);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleEntityNotFoundException(EntityNotFoundException ex, HttpServletRequest request) {
        log.warn("EntityNotFoundException: {}", ex.getMessage(), ex);
        Long referenceId = recordError(ex, request);
        return withReference("Die angeforderte Ressource wurde nicht gefunden.", referenceId);
    }

    @ExceptionHandler(TransactionSystemException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleTransactionSystemException(TransactionSystemException ex, HttpServletRequest request) {
        Throwable mostSpecific = Optional.of(ex.getMostSpecificCause()).orElse(ex);

        log.error("Transaction commit failed. Most specific cause: {}: {}",
                mostSpecific.getClass().getName(), mostSpecific.getMessage(), ex);
        Long referenceId = recordError(ex, request);

        return withReference("Die Anfrage konnte nicht verarbeitet werden.", referenceId);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        Throwable mostSpecific = Optional.of(ex.getMostSpecificCause()).orElse(ex);

        log.error("Data integrity violation. Most specific cause: {}: {}",
                mostSpecific.getClass().getName(), mostSpecific.getMessage(), ex);
        Long referenceId = recordError(ex, request);

        return withReference("Die Anfrage konnte nicht verarbeitet werden.", referenceId);
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
        log.error("Unhandled RuntimeException", ex);
        Long referenceId = recordError(ex, request);
        return withReference("Interner Server Fehler", referenceId);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDeniedException(final AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {}", ex.getMessage(), ex);
        Long referenceId = recordError(ex, request);
        return withReference("Zugriff verweigert.", referenceId);
    }

    @ExceptionHandler(MissingFormatArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleMissingFormatArgumentException(final MissingFormatArgumentException ex, HttpServletRequest request) {
        log.warn("MissingFormatArgumentException: {}", ex.getMessage(), ex);
        Long referenceId = recordError(ex, request);
        return withReference("Die Anfrage ist ungültig.", referenceId);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        log.error("RequestBody konnte nicht gelesen/deserialisiert werden: {}", e.getMessage(), e);
        Long referenceId = recordError(e, request);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "invalid_json");
        body.put("message", withReference("RequestBody ist kein gültiges JSON für das erwartete DTO.", referenceId));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        String fields = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                .collect(Collectors.joining(", "));

        log.warn("Validation fehlgeschlagen für RequestBody. Felder: {}", fields);
        log.debug("Validation-Details", e);
        Long referenceId = recordError(e, request);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "validation_failed");
        body.put("message", withReference("RequestBody verletzt Validierungsregeln.", referenceId));
        body.put("fields", fields);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception e, HttpServletRequest request) {
        log.error("Unerwarteter Fehler", e);
        Long referenceId = recordError(e, request);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "internal_error");
        body.put("message", withReference("Interner Fehler.", referenceId));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(GreenITServerLockedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public GreenITResponseDTO handleServerLockedException(GreenITServerLockedException ex, HttpServletRequest request) {
        log.error("Server is locked: {}", ex.getReason());
        Long referenceId = recordError(ex, request);
        return new GreenITResponseDTO(null, withReference(ex.getReason(), referenceId));
    }

    @ExceptionHandler(ServerNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public GreenITResponseDTO handleNotFoundException(ServerNotFoundException ex, HttpServletRequest request) {
        log.error("Server not found: {}", ex.getReason());
        Long referenceId = recordError(ex, request);
        return new GreenITResponseDTO(null, withReference(ex.getReason(), referenceId));
    }

    @ExceptionHandler(AppServiceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public GreenITResponseDTO handleNotFoundException(AppServiceNotFoundException ex, HttpServletRequest request) {
        log.error("AppService not found: {}", ex.getReason());
        Long referenceId = recordError(ex, request);
        return new GreenITResponseDTO(null, withReference(ex.getReason(), referenceId));
    }

    @ExceptionHandler(GreenITIllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public GreenITResponseDTO handleGreenITIllegalArgumentException(GreenITIllegalArgumentException ex, HttpServletRequest request) {
        log.error("GreenIT operation failed due to invalid argument: {}", ex.getReason());
        Long referenceId = recordError(ex, request);
        return new GreenITResponseDTO(null, withReference(ex.getReason(), referenceId));
    }

    @ExceptionHandler(ExcelGenerationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public GreenITResponseDTO handleExcelGenerationException(ExcelGenerationException ex, HttpServletRequest request) {
        log.error("Excel generation failed: {}", ex.getReason(), ex);
        Long referenceId = recordError(ex, request);
        return new GreenITResponseDTO(null, withReference(ex.getReason(), referenceId));
    }

    /**
     * Handles the {@link MaintenanceModeException} when the system is in maintenance mode.
     * <p>
     * <b>Important Note on Status Code:</b>
     * Although {@code 503 SERVICE_UNAVAILABLE} would be the semantically correct HTTP status code for a maintenance mode,
     * we are intentionally using {@code 403 FORBIDDEN} here.
     * </p>
     * <p>
     * <b>Reason:</b>
     * The underlying RefArch Gateway uses a global filter called {@code GlobalBackend5xxTo400Mapper}.
     * This filter intercepts all {@code 5xx} server errors from the backend and maps them to a generic
     * {@code 400 Bad Request} or {@code 500 Internal Server Error} response, effectively discarding our
     * custom response body (e.g., "MAINTENANCE_MODE_ACTIVE").
     * </p>
     * <p>
     * By using a {@code 4xx} status code like {@code 403}, we ensure that the Gateway passes the response
     * and its body through to the frontend unchanged, allowing the client to correctly identify and handle
     * the active maintenance mode.
     * </p>
     *
     * @param ex The exception indicating that maintenance mode is active.
     * @return A {@link ResponseEntity} containing a structured error body with status {@code 403}.
     */
    @ExceptionHandler(MaintenanceModeException.class)
    public ResponseEntity<Map<String, Object>> handleMaintenanceModeException(MaintenanceModeException ex) {
        log.warn("Maintenance mode active: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "MAINTENANCE_MODE_ACTIVE");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }
}