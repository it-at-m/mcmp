package de.muenchen.mcmp.job;

import java.time.Instant;

public interface ActiveGreenItJob {

    String getActionTitle();

    Instant getChangeStartDate();

    String getChangeNumber();

    String getChangeLink();
}
