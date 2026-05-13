package de.muenchen.mcmp.converter;

import de.muenchen.mcmp.types.JobStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class JobStatusConverter extends BaseEnumConverter<JobStatus> {

    public JobStatusConverter() {
        super(JobStatus.class);
    }
}