package de.muenchen.mcmp.report;

import de.muenchen.mcmp.foreman.ForemanHostRepository;
import de.muenchen.mcmp.foreman.ForemanIpMismatchServerDTO;
import de.muenchen.mcmp.foreman.UnmappedForemanHostDTO;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/report")
public class ReportController {
    private final ForemanHostRepository foremanHostRepository;

    /**
     * Retrieves a list of hosts from the Foreman system that are not mapped to any entity in the system.
     *
     * @return a list of {@code UnmappedForemanHostDTO} objects representing the unmapped hosts.
     */
    @GetMapping("/unmapped-foreman-hosts")
    public List<UnmappedForemanHostDTO> getUnmappedForemanHosts() {
        return foremanHostRepository.findUnmappedForemanHosts();
    }

    /**
     * Retrieves a list of servers where there are mismatches between the IP addresses reported
     * by the Foreman system and the IP addresses stored in the system.
     *
     * @return a list of {@code ForemanIpMismatchServerDTO} objects representing the servers
     *         with IP mismatches.
     */
    @GetMapping("/foreman-ip-mismatches")
    public List<ForemanIpMismatchServerDTO> getForemanIpMismatches() {
        return foremanHostRepository.findForemanIpMismatches();
    }
}
