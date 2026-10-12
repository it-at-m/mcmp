package de.muenchen.mcmp.ip;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class IPService {

    private final IPRepository ipRepository;

    /**
     * Retrieves a mapping of network interface controller (NIC) IDs to a list of IP addresses
     * associated with them. The data is grouped by NIC ID, preserving the order of NICs as they
     * appear in the data source.
     *
     * @return a map where the key is the NIC ID (as {@code Long}) and the value is a list of IP addresses
     *         (as {@code List<String>}) associated with the corresponding NIC ID
     */
    public Map<Long, List<String>> getIPsGroupedByNicId() {
        return ipRepository.findAllNicIdWithIp().stream()
                .collect(Collectors.groupingBy(
                        IPNicDTO::getNicId,
                        LinkedHashMap::new,
                        Collectors.mapping(IPNicDTO::getIp, Collectors.toList())));
    }
}
