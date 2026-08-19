package de.muenchen.mcmp.errorlog;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.zip.GZIPInputStream;

@Mapper(componentModel = "spring")
public interface ErrorLogMapper {

    Logger LOG = LoggerFactory.getLogger(ErrorLogMapper.class);


    @Mapping(target = "referenceId", expression = "java(ErrorLogReference.format(summary.getId()))")
    @Mapping(target = "stacktrace", ignore = true)
    @Mapping(target = "requestBody", ignore = true)
    ErrorLogDTO toDTO(ErrorLogSummary summary);

    @Mapping(target = "referenceId", expression = "java(ErrorLogReference.format(errorLog.getId()))")
    ErrorLogDTO toDetailDTO(ErrorLog errorLog);

    default OffsetDateTime map(Date value) {
        if (value == null) {
            return null;
        }
        return value.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    /**
     * Decompresses the gzip-compressed stacktrace stored on the entity.
     *
     * @param value the gzip-compressed stacktrace bytes
     * @return the decompressed stacktrace, or null if unreadable
     */
    default String map(byte[] value) {
        if (value == null) {
            return null;
        }
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(value))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Failed to decompress stacktrace", e);
            return null;
        }
    }
}
