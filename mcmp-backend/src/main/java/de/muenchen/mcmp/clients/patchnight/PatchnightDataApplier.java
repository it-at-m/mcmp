package de.muenchen.mcmp.clients.patchnight;

import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.types.EnvironmentType;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Component
public class PatchnightDataApplier {

    private static final ZoneId ZONE_BERLIN = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    public boolean applyAndDetectChanges(Server server, PatchnightDataDTO.ServerDTO serverDto) {
        final PatchnightSnapshot before = PatchnightSnapshot.from(server);
        apply(server, serverDto);
        return !before.equals(PatchnightSnapshot.from(server));
    }

    public void apply(Server server, PatchnightDataDTO.ServerDTO serverDto) {
        Short currentExitCode = server.getPatchnightExitcode();
        String currentExitString = server.getPatchnightExitstring();

        if (serverDto != null) {
            if (Boolean.FALSE.equals(serverDto.include())) {
                // include=false: reset patchnight fields (aber Exit-Felder nur anfassen, wenn exitcode im Import vorhanden ist)
                server.setPatchnightIncluded(false);
                server.setPatchnightEnvironment(null);
                server.setPatchnightStartDate(null);
                server.setPatchnightEndDate(null);
                server.setPatchnightGroup(null);
                server.setPatchnightTime(null);

                // Windows-Fall: exitcode ist im Import vorhanden -> setzen nach Regeln
                if (serverDto.exitcode() != null) {
                    applyExitCodeRules(server, currentExitCode, currentExitString, serverDto.exitcode(), serverDto.exitstring());
                }
                // Linux-Fall: exitcode fehlt -> exitcode/exitstring/changeDate/patchnightGroup bleiben unverändert
                return;
            }

            // include=true
            server.setPatchnightIncluded(true);
            server.setPatchnightEnvironment(parseEnvironmentType(serverDto.environment()));
            server.setPatchnightStartDate(serverDto.startDate());
            server.setPatchnightEndDate(serverDto.endDate());
            server.setPatchnightTime(extractBerlinTime(serverDto.startDate()));

            // Nur wenn exitcode im Import vorhanden ist (Windows), setzen wir exitcode/exitstring/patchnightGroup
            if (serverDto.exitcode() != null) {
                server.setPatchnightGroup(null);
                applyExitCodeRules(server, currentExitCode, currentExitString, serverDto.exitcode(), serverDto.exitstring());
            }
            return;
        }

        // Server not present in DTO input: clear patchnight data
        server.setPatchnightIncluded(false);
        server.setPatchnightEnvironment(null);
        server.setPatchnightGroup(null);
        server.setPatchnightStartDate(null);
        server.setPatchnightEndDate(null);
        server.setPatchnightGroup(null);
        server.setPatchnightTime(null);
        server.setPatchnightExitcode(null);
        server.setPatchnightExitstring(null);
        server.setPatchnightExitcodeChangeDate(null);
    }

    private void applyExitCodeRules(
            Server server,
            Short currentExitCode,
            String currentExitString,
            Short incomingExitCode,
            String incomingExitString
    ) {
        boolean exitCodeChanged = !Objects.equals(incomingExitCode, currentExitCode);
        boolean exitStringChanged = !Objects.equals(incomingExitString, currentExitString);

        // exitcodeChangeDate nur ändern, wenn:
        // - exitcode im Import vorhanden ist
        // - und sich exitcode ODER exitstring ändert
        if (incomingExitCode != null && (exitCodeChanged || exitStringChanged)) {
            server.setPatchnightExitcodeChangeDate(OffsetDateTime.now());
        }

        server.setPatchnightExitcode(incomingExitCode);
        server.setPatchnightExitstring(incomingExitString);
    }

    private EnvironmentType parseEnvironmentType(String environment) {
        if (environment == null) {
            return null;
        }
        if ("K".equalsIgnoreCase(environment)) {
            return EnvironmentType.K;
        }
        if ("P".equalsIgnoreCase(environment)) {
            return EnvironmentType.P;
        }
        return null;
    }

    private String extractBerlinTime(OffsetDateTime startDate) {
        if (startDate == null) {
            return null;
        }
        return startDate.atZoneSameInstant(ZONE_BERLIN)
                .toLocalTime()
                .format(HH_MM);
    }

}