package de.muenchen.mcmp.clients.patchnight;

import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.types.EnvironmentType;

import java.time.OffsetDateTime;

record PatchnightSnapshot(
        Boolean included,
        EnvironmentType environment,
        String group,
        OffsetDateTime startDate,
        OffsetDateTime endDate,
        Short exitcode,
        String exitstring,
        OffsetDateTime exitcodeChangeDate,
        String time
) {
    static PatchnightSnapshot from(Server s) {
        return new PatchnightSnapshot(
                s.getPatchnightIncluded(),
                s.getPatchnightEnvironment(),
                s.getPatchnightGroup(),
                s.getPatchnightStartDate(),
                s.getPatchnightEndDate(),
                s.getPatchnightExitcode(),
                s.getPatchnightExitstring(),
                s.getPatchnightExitcodeChangeDate(),
                s.getPatchnightTime()
        );
    }
}
