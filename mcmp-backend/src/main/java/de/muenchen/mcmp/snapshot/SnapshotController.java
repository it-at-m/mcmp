package de.muenchen.mcmp.snapshot;

import de.muenchen.mcmp.user.UserService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/snapshot")
public class SnapshotController {
    private final SnapshotService snapshotService;
    private final UserService userService;

    @GetMapping("/{serverId}")
    public List<SnapshotDTO> getSnapshotsByServerId(@PathVariable("serverId") final Long serverId) {
        return snapshotService.getSnapshotsByServerId(serverId);
    }
}
