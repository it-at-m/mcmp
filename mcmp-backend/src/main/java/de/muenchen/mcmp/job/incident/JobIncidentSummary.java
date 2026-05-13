package de.muenchen.mcmp.job.incident;

import de.muenchen.mcmp.types.IncidentSourceType;
import de.muenchen.mcmp.types.IncidentStatus;

public interface JobIncidentSummary {
    IncidentStatus getStatus();
    IncidentSourceType getSourceType();
    String getIncidentNumber();
    String getIncidentLink();
    Boolean getSuccess();
    String getCloseNotes();
}