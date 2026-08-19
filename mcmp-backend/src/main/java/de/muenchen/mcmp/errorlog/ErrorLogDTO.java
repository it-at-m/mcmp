package de.muenchen.mcmp.errorlog;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;


@Data
@Builder
public class ErrorLogDTO {
    private Long id;
    private String referenceId;
    private String exceptionClass;
    private String message;
    private String stacktrace;
    private String requestMethod;
    private String requestPath;
    private String requestQuery;
    private String requestBody;
    private String username;
    private OffsetDateTime createdAt;
}
