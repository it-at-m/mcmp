package de.muenchen.mcmp.report;

import de.muenchen.mcmp.foreman.ForemanHostRepository;
import de.muenchen.mcmp.foreman.ForemanIpMismatchServerDTO;
import de.muenchen.mcmp.foreman.UnmappedForemanHostDTO;
import de.muenchen.mcmp.security.HasSpecialRole;
import de.muenchen.mcmp.server.ServerRepository;
import de.muenchen.mcmp.server.UnmanagedServerDTO;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@AllArgsConstructor
@RequestMapping("/report")
public class ReportController {
    private final ForemanHostRepository foremanHostRepository;
    private final ServerRepository serverRepository;

    /**
     * Retrieves a list of hosts from the Foreman system that are not mapped to any entity in the system.
     *
     * @return a list of {@code UnmappedForemanHostDTO} objects representing the unmapped hosts.
     */
    @HasSpecialRole
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
    @HasSpecialRole
    @GetMapping("/foreman-ip-mismatches")
    public List<ForemanIpMismatchServerDTO> getForemanIpMismatches() {
        return foremanHostRepository.findForemanIpMismatches();
    }

    /**
     * Retrieves a list of unmanaged virtual machines (VMs) based on the specified exclusion filters.
     *
     * @param excludeNames   Optional list of VM names to exclude from the results. The exclusion
     *                       supports patterns for filtering using case-insensitive matching.
     * @param excludeClusters Optional list of cluster names to exclude from the results. The exclusion
     *                        supports patterns for filtering using case-insensitive matching.
     * @return A list of unmanaged virtual machine data transfer objects (DTOs) that match the filtering
     *         criteria. Returns an empty list if no unmanaged VMs are found.
     */
    @HasSpecialRole
    @GetMapping("/unmanaged-vms")
    public List<UnmanagedServerDTO> getUnmanagedVms(
            @RequestParam(name = "excludeName", required = false) final List<String> excludeNames,
            @RequestParam(name = "excludeCluster", required = false) final List<String> excludeClusters) {
        return serverRepository.findUnmanagedNonVirtualAppliances(
                toPatternArray(excludeNames),
                toPatternArray(excludeClusters));
    }

    /**
     * Converts a list of strings into an array of non-null, trimmed, and non-empty strings.
     *
     * @param patterns the list of strings to be converted; may be null
     * @return an array containing the filtered and trimmed strings, or an empty array if the input is null
     */
    private static String[] toPatternArray(final List<String> patterns) {
        if (patterns == null) {
            return new String[0];
        }
        return patterns.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .toArray(String[]::new);
    }
}
