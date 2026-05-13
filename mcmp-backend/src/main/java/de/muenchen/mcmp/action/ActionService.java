package de.muenchen.mcmp.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.muenchen.mcmp.awxConfig.AwxConfig;
import de.muenchen.mcmp.awxConfig.AwxConfigDTO;
import de.muenchen.mcmp.awxConfig.AwxConfigRepository;
import de.muenchen.mcmp.awxConfig.AwxConfigService;
import de.muenchen.mcmp.snowConfig.SnowConfig;
import de.muenchen.mcmp.snowConfig.SnowConfigRepository;
import de.muenchen.mcmp.testenvironment.TestEnvProperties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@Slf4j
@AllArgsConstructor
public class ActionService {

    private final ActionRepository actionRepository;
    private final ActionMapper actionMapper;

    private final AwxConfigService awxConfigService;

    private final AwxConfigRepository awxConfigRepository;
    private final SnowConfigRepository snowConfigRepository;

    private final TestEnvProperties testEnvProperties;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String CHANGE_TYPE_NORMAL = "normal";
    private static final String CHANGE_TYPE_STANDARD = "standard";

    public List<ActionDTO> getAllActions() {
        return actionRepository.findAll(Sort.by(Sort.Direction.ASC, "identifier")).stream()
                .map(actionMapper::toDTO)
                .toList();
    }

    @Transactional
    public void updateAction(ActionDTO actionDTO) {
        final Action actionToUpdate = actionRepository.findByIdentifier(actionDTO.identifier());
        if (actionToUpdate == null) {
            throw new IllegalArgumentException("Aktion mit Identifier " + actionDTO.identifier() + " existiert nicht.");
        }
        final Action updatedAction = actionMapper.toEntity(actionDTO);

        if (!testEnvProperties.isEnabled() || actionDTO.createIncidents() == null) {
            updatedAction.setCreateIncidents(true);
        }

        if (actionDTO.snowConfig() == null) {
            updatedAction.setChangeRequired(false);
            updatedAction.setSnowConfig(null);
        } else {
            final SnowConfig snowConfig = snowConfigRepository.findById(actionDTO.snowConfig().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Snow-Konfiguration mit ID " + actionDTO.snowConfig().getId() + " existiert nicht."));
            updatedAction.setSnowConfig(snowConfig);
        }

        if (CHANGE_TYPE_NORMAL.equalsIgnoreCase(updatedAction.getChangeType())) {
            updatedAction.setChangeTemplate(null);
        }
        if (CHANGE_TYPE_STANDARD.equalsIgnoreCase(updatedAction.getChangeType())) {
            updatedAction.setChangeAction(null);
        }
        if (actionDTO.awxJobEnabled()) {
            final AwxConfig awxConfig = awxConfigRepository.findById(actionDTO.awxConfig().getId())
                    .orElseThrow(() -> new IllegalArgumentException("AWX-Konfiguration mit ID " + actionDTO.awxConfig().getId() + " existiert nicht."));
            updatedAction.setAwxConfig(awxConfig);
        }

        updatedAction.setId(actionToUpdate.getId());
        updatedAction.setCreatedAt(actionToUpdate.getCreatedAt());
        updatedAction.setIdentifier(actionToUpdate.getIdentifier());
        updatedAction.setVersion(actionToUpdate.getVersion());
        actionRepository.save(updatedAction);
    }

    @Transactional
    public void createAction(ActionDTO actionDTO) {
        if (actionDTO.identifier() == null || actionDTO.identifier().isBlank()) {
            throw new IllegalArgumentException("Identifier muss angegeben werden.");
        }
        final Action action = actionRepository.findByIdentifier(actionDTO.identifier());
        if (action != null) {
            throw new IllegalArgumentException("Identifier ist bereits vergeben!");
        }
        final Action actionToCreate = actionMapper.toEntity(actionDTO);

        if (!testEnvProperties.isEnabled() || actionDTO.createIncidents() == null) {
            actionToCreate.setCreateIncidents(true);
        }
        if (actionToCreate.getSnowConfig() == null) {
            actionToCreate.setChangeRequired(false);
        }

        if (actionDTO.awxJobEnabled()) {
            final AwxConfig awxConfig = awxConfigRepository.findById(actionDTO.awxConfig().getId())
                    .orElseThrow(() -> new IllegalArgumentException("AWX-Konfiguration mit ID " + actionDTO.awxConfig().getId() + " existiert nicht."));
            actionToCreate.setAwxConfig(awxConfig);
        } else {
            actionToCreate.setAwxConfig(null);
        }
        if (actionDTO.changeRequired()) {
            final SnowConfig snowConfig = snowConfigRepository.findById(actionDTO.snowConfig().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Snow-Konfiguration mit ID " + actionDTO.snowConfig().getId() + " existiert nicht."));
            actionToCreate.setSnowConfig(snowConfig);
        } else {
            actionToCreate.setSnowConfig(null);
        }
        actionRepository.save(actionToCreate);
    }


    public JsonNode getAwxOrganizations(final Long awxConfigId) {
        final AwxConfigDTO awxConfig = awxConfigService.getAwxDecrypted(awxConfigId);
        HttpHeaders headers = new HttpHeaders();
        final String basicAuth = java.util.Base64.getEncoder().encodeToString(
                (awxConfig.apiUsername() + ":" + awxConfig.apiPassword()).getBytes());
        headers.set("Authorization", "Basic " + basicAuth);

        String awxUrl = awxConfig.apiEndpoint() + "/api/v2/organizations/?page_size=200";
        final HttpEntity<Void> entity = new HttpEntity<>(headers);

        ArrayNode names = JsonNodeFactory.instance.arrayNode();
        do {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    awxUrl, HttpMethod.GET, entity, JsonNode.class);
            JsonNode body = response.getBody();
            if (body == null || !body.has("results")) break;

            for (JsonNode org : body.get("results")) {
                if (org.has("name")) {
                    names.add(org.get("name").asText());
                }
            }
            if (body.has("next") && !body.get("next").isNull()) {
                String next = body.get("next").asText();
                awxUrl = next.startsWith("http") ? next : awxConfig.apiEndpoint() + next;
            } else {
                awxUrl = null;
            }
        } while (awxUrl != null);

        return names;
    }

    public JsonNode getJobTemplatesFromAwx(final String requestedDepartment, final Long awxConfigId) {
        final AwxConfigDTO awxConfig = awxConfigService.getAwxDecrypted(awxConfigId);
        HttpHeaders headers = new HttpHeaders();
        final String basicAuth = java.util.Base64.getEncoder().encodeToString(
                (awxConfig.apiUsername() + ":" + awxConfig.apiPassword()).getBytes());
        headers.set("Authorization", "Basic " + basicAuth);

        final String baseUrl = awxConfig.apiEndpoint();
        String awxUrl = baseUrl + "/api/v2/unified_job_templates/" +
                "?organization__name=" + requestedDepartment +
                "&or__type=job_template&or__type=workflow_job_template&page_size=200";
        final HttpEntity<Void> entity = new HttpEntity<>(headers);

        ArrayNode results = JsonNodeFactory.instance.arrayNode();
        do {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    awxUrl, HttpMethod.GET, entity, JsonNode.class);
            JsonNode body = response.getBody();
            if (body == null || !body.has("results")) break;

            for (JsonNode job : body.get("results")) {
                ObjectNode node = JsonNodeFactory.instance.objectNode();
                if (job.has("id")) node.put("id", job.get("id").asInt());
                if (job.has("name")) node.put("name", job.get("name").asText());
                results.add(node);
            }
            if (body.has("next") && !body.get("next").isNull()) {
                String next = body.get("next").asText();
                awxUrl = next.startsWith("http") ? next : baseUrl + next;
            } else {
                awxUrl = null;
            }
        } while (awxUrl != null);

        return results;
    }

    public JsonNode getSingleJobTemplateFromAwx(final int templateId, final Long awxConfigId) {
        final AwxConfigDTO awxConfig = awxConfigService.getAwxDecrypted(awxConfigId);
        HttpHeaders headers = new HttpHeaders();
        final String basicAuth = java.util.Base64.getEncoder().encodeToString(
                (awxConfig.apiUsername() + ":" + awxConfig.apiPassword()).getBytes());
        headers.set("Authorization", "Basic " + basicAuth);

        String awxUrl = awxConfig.apiEndpoint() + "/api/v2/unified_job_templates/?id=" + templateId;
        final HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                awxUrl, HttpMethod.GET, entity, JsonNode.class);
        return response.getBody();
    }

    public boolean isActionEnabled(final String actionIdentifier) {
        final Action action = actionRepository.findByIdentifier(actionIdentifier);
        return action != null && action.getEnabled();
    }
}
