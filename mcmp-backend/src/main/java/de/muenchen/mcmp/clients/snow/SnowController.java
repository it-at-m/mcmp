package de.muenchen.mcmp.clients.snow;

import de.muenchen.mcmp.appservice.Appservice;
import de.muenchen.mcmp.appservice.AppserviceRepository;
import de.muenchen.mcmp.group.Group;
import de.muenchen.mcmp.group.GroupRepository;
import de.muenchen.mcmp.security.HasApiRole;
import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.server.ServerRepository;
import de.muenchen.mcmp.server.ServerService;
import de.muenchen.mcmp.server.matching.MatcherStrategyFactory;
import de.muenchen.mcmp.server.matching.ServerMatcher;
import de.muenchen.mcmp.types.EnvironmentType;
import de.muenchen.mcmp.user.User;
import de.muenchen.mcmp.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@AllArgsConstructor
@RequestMapping("/snow")
@Slf4j
public class SnowController {

    private final SnowService snowService;
    private final UserRepository userRepository;

    @HasApiRole
    @GetMapping("/ping")
    public String ping() {
        log.debug("Health check request received for ServiceNow API endpoint");
        return "ServiceNow EAI API is accessible";
    }

    @HasApiRole
    @PostMapping("/snowData")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void processSnowData(@Valid @RequestBody final SnowDataRequestDTO snowDataRequestDTO) {
        log.debug("ServiceNow request received: users='{}', groups='{}', cmdbCIs='{}', appServices='{}'",
                snowDataRequestDTO.users(),
                snowDataRequestDTO.groups(),
                snowDataRequestDTO.cmdbCIs(),
                snowDataRequestDTO.appServices());
        try {
            snowService.importAsync(snowDataRequestDTO);
            log.debug("ServiceNow task successfully submitted to background queue. Task is now pending in the execution queue.");
        } catch (Exception e) {
            log.error("ServiceNow import task submission failed. error='{}'. The request was received and validated but could not be queued for background processing.", e.getMessage(), e);
        }
    }

    @HasApiRole
    @GetMapping("/specialUsers")
    public List<String> getSpecialUsernames() {
        return userRepository.findSpecialUsernames();
    }
}
