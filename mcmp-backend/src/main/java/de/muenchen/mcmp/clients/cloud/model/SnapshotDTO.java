package de.muenchen.mcmp.clients.cloud.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.snapshot.Snapshot;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;

public record SnapshotDTO(
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("create_time") OffsetDateTime createTime,
        @JsonProperty("quiesced") Boolean quiesced,
        @JsonProperty("state") String state,
        @JsonProperty("replay_supported") Boolean replaySupported
) {
    public SnapshotDTO {
        Objects.requireNonNull(name);

        if (quiesced == null) quiesced = false;
        if (replaySupported == null) replaySupported = false;
    }

    /**
     * Determines if this DTO contains changes that warrant writing
     * to the database, based on an existing snapshot entity.
     *
     * @param existing The snapshot entity to compare.
     * @return True if changes should be written.
     */
    public boolean hasChanges(final Snapshot existing) {
        return !Objects.equals(existing.getDescription(), description)
                || !Objects.equals(existing.getCreateTime(), createTime)
                || !Objects.equals(existing.isQuiesced(), quiesced)
                || !Objects.equals(existing.isReplaySupported(), replaySupported);
    }

    /**
     * Applies the DTO's data to an existing snapshot entity.
     *
     * @param existing The entity to be modified.
     */
    public void applyChanges(final Snapshot existing) {
        existing.setDescription(description);
        existing.setCreateTime(createTime());
        existing.setRetentionPeriod(retentionTime());
        existing.setQuiesced(quiesced());
        existing.setReplaySupported(replaySupported());
    }

    /**
     * Builds a new snapshot entity which can be persisted to the
     * Database.
     *
     * @param server The Server entity the snapshot belongs to.
     * @return A new snapshot entity.
     */
    public Snapshot build(final Server server) {
        final Snapshot snapshot = new Snapshot();
        snapshot.setSnapshotId(Math.abs(name.hashCode()));
        snapshot.setServerId(server.getId());
        applyChanges(snapshot);
        return snapshot;
    }

    /**
     * Calculate a Snapshot's retention period based on naming convention.
     *
     * <p>The following formats are recognized:</p>
     * <ul>
     *   <li>
     *     <code>###YYYYMMDD###</code>, where Y, M and D are digits, is
     *     parsed as the date YYYY-MM-DD.
     *   </li>
     *   <li>
     *     <code>###n###</code>, where n is any other number, is parsed
     *     as an offset from the creation date of n hours.
     *   </li>
     *   <li>
     *     If the name does not match any of the previous patterns,
     *     the date five days after creation of the snapshot is returned.
     *   </li>
     * </ul>
     *
     * @return the retention period/deletion date of the snapshot.
     */
    public OffsetDateTime retentionTime() {
        try {
            final var retention = StringUtils.substringBetween(name, "###");
            try {
                return LocalDate.parse(retention, DateTimeFormatter.ofPattern("yyyyMMdd"))
                        .atTime(0, 0)
                        .atOffset(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {
            }

            try {
                return createTime.plusHours(Long.parseLong(retention));
            } catch (NumberFormatException ignored) {
            }
        } catch (NullPointerException ignored) {
        }

        return createTime.plusDays(5);
    }

}
