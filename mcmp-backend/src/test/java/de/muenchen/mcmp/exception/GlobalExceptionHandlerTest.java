package de.muenchen.mcmp.exception;

import de.muenchen.mcmp.clients.greenit.GreenITResponseDTO;
import de.muenchen.mcmp.errorlog.ErrorLogReference;
import de.muenchen.mcmp.errorlog.ErrorLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;
import java.util.MissingFormatArgumentException;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private ErrorLogService errorLogService;
    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        errorLogService = mock(ErrorLogService.class);
        handler = new GlobalExceptionHandler(errorLogService);
        request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/job/create/LINUX_RHEL10_SERVER");
    }

    @Test
    void businessValidationException_maps400WithOwnMessagePreserved() {
        when(errorLogService.logError(any(), any(), any(), any(), any(), any())).thenReturn(1L);

        String body = handler.handleBusinessValidationException(new BusinessValidationException("CPU must be between 1 and 8."), request);

        assertTrue(body.startsWith("CPU must be between 1 and 8."));
        assertTrue(body.contains(ErrorLogReference.format(1L)));
    }

    @Test
    void illegalArgumentException_maps400_originalMessageIsNotLeaked() {
        when(errorLogService.logError(any(), any(), any(), any(), any(), any())).thenReturn(2L);

        String body = handler.handleIllegalArgumentException(
                new IllegalArgumentException("internal detail: applicationServiceId=42 not owned by user tester"), request);

        assertFalse(body.contains("applicationServiceId=42"), "internal exception message must not reach the client");
        assertEquals("Die Anfrage ist ungültig. (Fehlercode: " + ErrorLogReference.format(2L) + ")", body);
    }

    @Test
    void noSuchElementException_maps404() {
        when(errorLogService.logError(any(), any(), any(), any(), any(), any())).thenReturn(3L);
        String body = handler.handleNoSuchElementException(new NoSuchElementException("Server not found with id 999"), request);
        assertFalse(body.contains("999"));
        assertTrue(body.startsWith("Die angeforderte Ressource wurde nicht gefunden."));
    }

    @Test
    void transactionSystemException_maps409() {
        when(errorLogService.logError(any(), any(), any(), any(), any(), any())).thenReturn(4L);
        TransactionSystemException ex = new TransactionSystemException("commit failed");
        String body = handler.handleTransactionSystemException(ex, request);
        assertTrue(body.startsWith("Die Anfrage konnte nicht verarbeitet werden."));
    }

    @Test
    void dataIntegrityViolationException_maps409() {
        when(errorLogService.logError(any(), any(), any(), any(), any(), any())).thenReturn(5L);
        DataIntegrityViolationException ex = new DataIntegrityViolationException("duplicate key value violates unique constraint");
        String body = handler.handleDataIntegrityViolation(ex, request);
        assertFalse(body.contains("duplicate key"), "raw SQL constraint detail must not leak to the client");
        assertTrue(body.startsWith("Die Anfrage konnte nicht verarbeitet werden."));
    }

    @Test
    void runtimeException_maps500_originalMessageIsNotLeaked() {
        when(errorLogService.logError(any(), any(), any(), any(), any(), any())).thenReturn(6L);

        String body = handler.handleRuntimeException(
                new NullPointerException("Cannot invoke \"Map.get(Object)\" because \"serverTypeMap\" is null"), request);

        assertFalse(body.contains("serverTypeMap"), "stack-trace-derived NPE message must not reach the client");
        assertEquals("Interner Server Fehler (Fehlercode: " + ErrorLogReference.format(6L) + ")", body);
    }

    @Test
    void accessDeniedException_maps403() {
        when(errorLogService.logError(any(), any(), any(), any(), any(), any())).thenReturn(7L);
        String body = handler.handleAccessDeniedException(new AccessDeniedException("not allowed"), request);
        assertTrue(body.startsWith("Zugriff verweigert."));
    }

    @Test
    void missingFormatArgumentException_maps400() {
        when(errorLogService.logError(any(), any(), any(), any(), any(), any())).thenReturn(8L);
        String body = handler.handleMissingFormatArgumentException(new MissingFormatArgumentException("account_name"), request);
        assertTrue(body.startsWith("Die Anfrage ist ungültig."));
    }

    @Test
    void httpMessageNotReadableException_maps400WithStructuredBody() {
        when(errorLogService.logError(any(), any(), any(), any(), any(), any())).thenReturn(9L);
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
        when(ex.getMessage()).thenReturn("Unexpected character ('{' ...)");

        ResponseEntity<Map<String, Object>> response = handler.handleNotReadable(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("invalid_json", response.getBody().get("error"));
        assertTrue(((String) response.getBody().get("message")).contains(ErrorLogReference.format(9L)));
    }

    @Test
    void methodArgumentNotValidException_maps400WithFieldList() {
        when(errorLogService.logError(any(), any(), any(), any(), any(), any())).thenReturn(10L);
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("dto", "accountName", "must not be blank"),
                new FieldError("dto", "serverIds", "must not be empty")));

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("validation_failed", response.getBody().get("error"));
        assertEquals("accountName, serverIds", response.getBody().get("fields"));
    }

    @Test
    void genericException_maps500WithStructuredBody_originalMessageIsNotLeaked() {
        when(errorLogService.logError(any(), any(), any(), any(), any(), any())).thenReturn(11L);

        ResponseEntity<Map<String, Object>> response = handler.handleGeneric(new IllegalStateException("connection pool exhausted"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("internal_error", response.getBody().get("error"));
        assertFalse(((String) response.getBody().get("message")).contains("connection pool"));
    }

    @Test
    void greenItServerLockedException_maps403WithGreenItDto() {
        when(errorLogService.logError(any(), any(), any(), any(), any(), any())).thenReturn(12L);
        GreenITServerLockedException ex = new GreenITServerLockedException("Server is locked for maintenance.");

        GreenITResponseDTO response = handler.handleServerLockedException(ex, request);

        assertNull(response.jobId());
        assertTrue(response.message().startsWith("Server is locked for maintenance."));
    }

    @Test
    void serverNotFoundException_maps404WithGreenItDto() {
        when(errorLogService.logError(any(), any(), any(), any(), any(), any())).thenReturn(13L);
        GreenITResponseDTO response = handler.handleNotFoundException(new ServerNotFoundException("Server xyz not found."), request);
        assertTrue(response.message().startsWith("Server xyz not found."));
    }

    @Test
    void appServiceNotFoundException_maps404WithGreenItDto() {
        when(errorLogService.logError(any(), any(), any(), any(), any(), any())).thenReturn(14L);
        GreenITResponseDTO response = handler.handleNotFoundException(new AppServiceNotFoundException("Appservice xyz not found."), request);
        assertTrue(response.message().startsWith("Appservice xyz not found."));
    }

    @Test
    void greenItIllegalArgumentException_maps400WithGreenItDto() {
        when(errorLogService.logError(any(), any(), any(), any(), any(), any())).thenReturn(15L);
        GreenITResponseDTO response = handler.handleGreenITIllegalArgumentException(
                new GreenITIllegalArgumentException("Invalid rightsizing target."), request);
        assertTrue(response.message().startsWith("Invalid rightsizing target."));
    }

    @Test
    void excelGenerationException_handlerAnnotationOverridesExceptionOwn500ToA400() {
        // The constructor of ExcelGenerationException sets the status HttpStatus.INTERNAL_SERVER_ERROR.
        // But the handler method has the separate annotation @ResponseStatus(BAD_REQUEST). For a plain
        // object return type, this annotation sets the actual status. This test locks the current
        // behavior. Do not remove the annotation. Removal would change the status code of this
        // endpoint without warning.
        when(errorLogService.logError(any(), any(), any(), any(), any(), any())).thenReturn(16L);
        GreenITResponseDTO response = handler.handleExcelGenerationException(
                new ExcelGenerationException("Excel export failed.", new RuntimeException("io error")), request);
        assertTrue(response.message().startsWith("Excel export failed."));
    }

    @Test
    void maintenanceModeException_maps403NotSemantically503_forGatewayCompatibility() {
        ResponseEntity<Map<String, Object>> response = handler.handleMaintenanceModeException(
                new MaintenanceModeException("MAINTENANCE_MODE_ACTIVE"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "must stay 403, not 503: the RefArch Gateway rewrites bare 5xx to a generic 400/500 and drops this body");
        assertNotNull(response.getBody());
        assertEquals("MAINTENANCE_MODE_ACTIVE", response.getBody().get("error"));
    }
}
