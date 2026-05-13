package de.muenchen.mcmp.version;

import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class VersionController {

    private final ObjectProvider<BuildProperties> buildPropertiesProvider;
    private final ObjectProvider<GitProperties> gitPropertiesProvider;

    VersionController(ObjectProvider<BuildProperties> buildPropertiesProvider,
                      ObjectProvider<GitProperties> gitPropertiesProvider) {
        this.buildPropertiesProvider = buildPropertiesProvider;
        this.gitPropertiesProvider = gitPropertiesProvider;
    }

    @GetMapping(value = "/version", produces = MediaType.APPLICATION_JSON_VALUE)
    VersionResponse version() {
        BuildProperties build = buildPropertiesProvider.getIfAvailable();
        GitProperties git = gitPropertiesProvider.getIfAvailable();

        String version = build != null ? build.getVersion() : null;
        Instant buildTime = build != null ? build.getTime() : null;

        String javaVersion = build != null ? build.get("java.version") : null;

        String branch = git != null ? git.getBranch() : null;
        String commitIdFull = git != null ? git.getCommitId() : null;

        String commitIdShort = null;
        if (git != null) {
            // bevorzugt: property aus git.properties (passt zu deinem Maven includeOnlyProperties)
            commitIdShort = git.get("commit.id.abbrev");
            // fallback: API-Methode (falls vorhanden/gefüllt)
            if (commitIdShort == null) {
                commitIdShort = git.getShortCommitId();
            }
        }

        String commitTime = git != null ? git.get("commit.time") : null;

        Boolean dirty = null;
        if (git != null) {
            String d = git.get("dirty");
            if (d != null) {
                dirty = Boolean.valueOf(d);
            }
        }

        return new VersionResponse(
                version,
                buildTime,
                javaVersion,
                branch,
                commitIdShort != null ? commitIdShort : commitIdFull,
                commitIdFull,
                commitTime,
                dirty
        );
    }

    record VersionResponse(
            String version,
            Instant buildTime,
            String javaVersion,
            String gitBranch,
            String gitCommitId,
            String gitCommitIdFull,
            String gitCommitTime,
            Boolean gitDirty
    ) {}
}