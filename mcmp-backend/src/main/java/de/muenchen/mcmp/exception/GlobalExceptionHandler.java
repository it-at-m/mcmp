package de.muenchen.mcmp.exception;

import de.muenchen.mcmp.clients.greenit.GreenITResponseDTO;
import jakarta.persistence.EntityNotFoundException;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.MissingFormatArgumentException;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleIllegalArgumentException(final IllegalArgumentException ex) {
        log.warn("IllegalArgumentException: {}", ex.getMessage(), ex);
        return "Die Anfrage ist ungültig.";
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNoSuchElementException(NoSuchElementException ex) {
        log.warn("NoSuchElementException: {}", ex.getMessage(), ex);
        return "Die angeforderte Ressource wurde nicht gefunden.";
    }

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleEntityNotFoundException(EntityNotFoundException ex) {
        log.warn("EntityNotFoundException: {}", ex.getMessage(), ex);
        return "Die angeforderte Ressource wurde nicht gefunden.";
    }

    @ExceptionHandler(TransactionSystemException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleTransactionSystemException(TransactionSystemException ex) {
        Throwable mostSpecific = Optional.ofNullable(ex.getMostSpecificCause()).orElse(ex);

        log.error("Transaction commit failed. Most specific cause: {}: {}",
                mostSpecific.getClass().getName(), mostSpecific.getMessage(), ex);

        return "Die Anfrage konnte nicht verarbeitet werden.";
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        Throwable mostSpecific = Optional.ofNullable(ex.getMostSpecificCause()).orElse(ex);

        log.error("Data integrity violation. Most specific cause: {}: {}",
                mostSpecific.getClass().getName(), mostSpecific.getMessage(), ex);

        return "Die Anfrage konnte nicht verarbeitet werden.";
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleRuntimeException(RuntimeException ex) {
        log.error("Unhandled RuntimeException", ex);
        return "Interner Server Fehler";
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDeniedException(final AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage(), ex);
        return "Zugriff verweigert.";
    }

    @ExceptionHandler(MissingFormatArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleMissingFormatArgumentException(final MissingFormatArgumentException ex) {
        log.warn("MissingFormatArgumentException: {}", ex.getMessage(), ex);
        return "Die Anfrage ist ungültig.";
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException e) {
        log.error("RequestBody konnte nicht gelesen/deserialisiert werden: {}", e.getMessage(), e);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "invalid_json");
        body.put("message", "RequestBody ist kein gültiges JSON für das erwartete DTO.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String fields = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                .collect(Collectors.joining(", "));

        log.warn("Validation fehlgeschlagen für RequestBody. Felder: {}", fields);
        log.debug("Validation-Details", e);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "validation_failed");
        body.put("message", "RequestBody verletzt Validierungsregeln.");
        body.put("fields", fields);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception e) {
        log.error("Unerwarteter Fehler", e);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "internal_error");
        body.put("message", "Interner Fehler.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(GreenITServerLockedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public GreenITResponseDTO handleServerLockedException(GreenITServerLockedException ex) {
        log.error("Server is locked: {}", ex.getReason());
        return new GreenITResponseDTO(null, ex.getReason());
    }

    @ExceptionHandler(ServerNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public GreenITResponseDTO handleNotFoundException(ServerNotFoundException ex) {
        log.error("Server not found: {}", ex.getReason());
        return new GreenITResponseDTO(null, ex.getReason());
    }

    @ExceptionHandler(AppServiceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public GreenITResponseDTO handleNotFoundException(AppServiceNotFoundException ex) {
        log.error("AppService not found: {}", ex.getReason());
        return new GreenITResponseDTO(null, ex.getReason());
    }

    @ExceptionHandler(GreenITIllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public GreenITResponseDTO handleGreenITIllegalArgumentException(GreenITIllegalArgumentException ex) {
        log.error("GreenIT operation failed due to invalid argument: {}", ex.getReason());
        return new GreenITResponseDTO(null, ex.getReason());
    }

    @ExceptionHandler(ExcelGenerationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public GreenITResponseDTO handleExcelGenerationException(ExcelGenerationException ex) {
        log.error("Excel generation failed: {}", ex.getReason(), ex);
        return new GreenITResponseDTO(null, ex.getReason());
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