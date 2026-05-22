package de.muenchen.mcmp.action;


import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/action")
public class ActionController {

    private final ActionService actionService;

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping
    public List<ActionDTO> getAllActions() {
        return actionService.getAllActions();
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PutMapping
    public void updateAction(@RequestBody final ActionDTO actionDTO) {
        actionService.updateAction(actionDTO);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void createAction(@RequestBody final ActionDTO actionDTO) {
        actionService.createAction(actionDTO);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/getTemplatesFromAwx")
    public JsonNode getTemplatesFromAwx(@RequestParam final String department, @RequestParam final Long awxConfigId) {
        return actionService.getJobTemplatesFromAwx(department, awxConfigId);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/getSingleJobTemplateFromAwx")
    public JsonNode getSingleJobTemplateFromAwx(@RequestParam final int templateId, @RequestParam final Long awxConfigId) {
        return actionService.getSingleJobTemplateFromAwx(templateId, awxConfigId);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/getOrganizationsFromAwx")
    public JsonNode getOrganizationsFromAwx(@RequestParam final Long awxConfigId) {
        return actionService.getAwxOrganizations(awxConfigId);
    }

    @GetMapping("/enabled")
    public boolean isActionEnabled(@RequestParam final String actionIdentifier) {
        return actionService.isActionEnabled(actionIdentifier);
    }
}
