package de.muenchen.mcmp.mountPoint;

import de.muenchen.mcmp.user.UserService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/mount-point")
public class MountPointController {
    private final MountPointService mountPointService;
    private final UserService userService;

    @GetMapping("/{serverId}")
    public List<MountPointDTO> getMountPointsByServerId(@PathVariable("serverId") final Long serverId) {
        return mountPointService.getMountPointsByServerId(serverId);
    }
}

