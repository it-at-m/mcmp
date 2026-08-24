package de.muenchen.mcmp.errorlog;

import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.DynamicUpdate;

@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
@Table(name = "error_log")
@DynamicUpdate
public class ErrorLog extends AbstractEntity {

    @NotNull
    @Column(name = "exception_class", nullable = false)
    private String exceptionClass;

    @Column(name = "message", length = Integer.MAX_VALUE)
    private String message;

    /**
     * Gzip-compressed, line-truncated stacktrace (see {@link ErrorLogService}).
     */
    @Column(name = "stacktrace")
    private byte[] stacktrace;

    @Column(name = "request_method")
    private String requestMethod;

    @Column(name = "request_path")
    private String requestPath;

    @Column(name = "request_query")
    private String requestQuery;

    @Column(name = "request_body", length = Integer.MAX_VALUE)
    private String requestBody;

    @Column(name = "username")
    private String username;
}
