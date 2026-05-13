package de.muenchen.mcmp.job;

import java.time.OffsetDateTime;

public record ActiveGreenItJobDTO(
        String actionTitle,
        OffsetDateTime changeStartDate,
        String changeNumber,
        String changeLink
) {}