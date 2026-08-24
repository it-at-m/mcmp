package de.muenchen.mcmp.errorlog;

import java.util.Date;

public interface ErrorLogSummary {

    Long getId();

    String getExceptionClass();

    String getMessage();

    String getRequestMethod();

    String getRequestPath();

    String getRequestQuery();

    String getUsername();

    Date getCreatedAt();
}
