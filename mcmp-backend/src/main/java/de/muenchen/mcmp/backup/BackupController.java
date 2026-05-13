package de.muenchen.mcmp.backup;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/backup")
public class BackupController {
    private final BackupService backupService;

    @GetMapping("/{serverId}")
    public List<BackupDTO> getBackupsByServerId(@PathVariable("serverId") final Long serverId) {
            return backupService.getBackupsByServerId(serverId);
    }
}

