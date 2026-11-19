package de.muenchen.mcmp.clients.awx;

import de.muenchen.mcmp.awxConfig.AwxConfigDTO;
import de.muenchen.mcmp.awxConfig.AwxConfigService;
import de.muenchen.mcmp.security.AuthUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Resolves AWX organizations and teams that the current user is allowed to manage.
 * <p>
 * This service reads the
 * {@code SOCIAL_AUTH_ORGANIZATION_MAP} and {@code SOCIAL_AUTH_TEAM_MAP} entries from
 * {@code /api/v2/settings/authentication/}. These maps define, per organization, which
 * usernames are listed as "admins". A team is only manageable if the current user is an
 * admin of the team's organization - teams have no admin concept of their own in AWX's
 * social-auth mapping.
 */
@Slf4j
@Service
@AllArgsConstructor
public class AwxOrgTeamService {

    private static final String AWX_CONFIG_DESCRIPTION = "AWX-P";
    private static final String LOGIN_PATH = "/api/login/";
    private static final String SETTINGS_PATH = "/api/v2/settings/authentication/";
    private static final String ORGANIZATION_MAP_FIELD = "SOCIAL_AUTH_ORGANIZATION_MAP";
    private static final String TEAM_MAP_FIELD = "SOCIAL_AUTH_TEAM_MAP";

    /**
     * The "Default" org/team is AWX's built-in default and should never be offered for
     * management, regardless of admin status.
     */
    private static final String EXCLUDED_DEFAULT_NAME = "Default";

    private static final String STATIC_PREFIX = "STATIC:";
    private static final String LDAP_PREFIX = "LDAP:";

    /**
     * When this property is {@code true}, {@link #fetchAuthenticationSettings()} returns
     * {@link #STUB_AUTHENTICATION_SETTINGS_JSON} instead of calling AWX. Used for Development.
     */
    private static final String STUB_ENABLED_PROPERTY = "awx.orgteam.use-stub";

    private static final String STUB_AUTHENTICATION_SETTINGS_JSON = """
            {
              "SOCIAL_AUTH_ORGANIZATION_MAP": {
                "IBS41": {
                  "users": [],
                  "admins": ["STATIC:ibs41", "ibs44", "LDAP:cn=ibs41,ou=groups,ou=org,o=example,c=de"],
                  "remove_users": true,
                  "remove_admins": true
                },
                "IBS44": {
                  "users": [],
                  "admins": ["ibs44", "LDAP:cn=ibs44,ou=groups,ou=org,o=example,c=de"],
                  "remove_users": true,
                  "remove_admins": true
                },
                "Default": {
                  "users": true,
                  "admins": false,
                  "remove_users": true,
                  "remove_admins": true
                }
              },
              "SOCIAL_AUTH_TEAM_MAP": {
                "IBS411": {
                  "users": ["ibs41"],
                  "remove": true,
                  "organization": "IBS41"
                },
                "IBS441": {
                  "users": ["ibs44"],
                  "remove": true,
                  "organization": "IBS44"
                },
                "Servers": {
                  "users": ["STATIC:demo.user", "demo.user"],
                  "remove": true,
                  "organization": "Default"
                },
                "Global": {
                  "users": true,
                  "remove": true,
                  "organization": "Default"
                },
                "Default": {
                  "users": ["demo.user"],
                  "remove": true,
                  "organization": "Default"
                }
              }
            }
            """;

    private final AwxConfigService awxConfigService;
    private final Environment environment;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Result of checking whether an org/team name already exists in AWX, and if so,
     * whether the current user is allowed to manage it.
     */
    public record AwxNameCheckDTO(boolean exists, boolean allowed) {
    }

    /**
     * Current members of an AWX organization (its admins) or team (its users), so that the edit
     * form can be prefilled. {@code staticMembers} ({@code STATIC:...}) and {@code ldapMembers}
     * ({@code LDAP:...}) are editable; {@code otherMembers} (any other entry) are read-only.
     * {@code organization} is only set for teams.
     */
    public record AwxOrgTeamDetailsDTO(String name, String organization, List<String> staticMembers,
                                       List<String> ldapMembers, List<String> otherMembers) {
    }

    /**
     * Returns the current admins of an organization. Only available to admins of that organization.
     *
     * @throws NoSuchElementException if the organization does not exist
     * @throws AccessDeniedException  if the current user is not an admin of the organization
     */
    public AwxOrgTeamDetailsDTO getOrganizationDetails(final String name) {
        final JsonNode organizationMap = fetchOrganizationMap();
        final String orgName = findKey(organizationMap, name);
        if (orgName == null) {
            throw new NoSuchElementException("Organization not found: " + name);
        }
        final JsonNode organization = organizationMap.get(orgName);
        if (EXCLUDED_DEFAULT_NAME.equalsIgnoreCase(orgName) || !isAdminOf(organization, AuthUtils.getUsername())) {
            log.warn("User {} tried to read AWX organization {} without permission.", AuthUtils.getUsername(), orgName);
            throw new AccessDeniedException("You are not allowed to manage this organization.");
        }
        return toDetails(orgName, null, organization.path("admins"));
    }

    /**
     * Returns the current users of a team. Only available to admins of the team's organization.
     *
     * @throws NoSuchElementException if the team does not exist
     * @throws AccessDeniedException  if the current user is not an admin of the team's organization
     */
    public AwxOrgTeamDetailsDTO getTeamDetails(final String name) {
        final JsonNode teamMap = fetchTeamMap();
        final String teamName = findKey(teamMap, name);
        if (teamName == null) {
            throw new NoSuchElementException("Team not found: " + name);
        }
        final JsonNode team = teamMap.get(teamName);
        final String organization = team.path("organization").asString(null);
        final JsonNode organizationEntry = organization == null ? null : findCaseInsensitive(fetchOrganizationMap(), organization);
        if (EXCLUDED_DEFAULT_NAME.equalsIgnoreCase(teamName) || !isAdminOf(organizationEntry, AuthUtils.getUsername())) {
            log.warn("User {} tried to read AWX team {} without permission.", AuthUtils.getUsername(), teamName);
            throw new AccessDeniedException("You are not allowed to manage this team.");
        }
        return toDetails(teamName, organization, team.path("users"));
    }

    /**
     * The members field is either a boolean (all/no users) or a list of entries; booleans carry no
     * explicit members and yield empty lists. Only entries with the exact {@code STATIC:} or
     * {@code LDAP:} prefix are editable, everything else is returned as read-only.
     */
    private AwxOrgTeamDetailsDTO toDetails(final String name, final String organization, final JsonNode members) {
        final List<String> staticMembers = new ArrayList<>();
        final List<String> ldapMembers = new ArrayList<>();
        final List<String> otherMembers = new ArrayList<>();
        if (members.isArray()) {
            for (final JsonNode member : members) {
                final String value = member.asString("");
                if (value.isBlank()) {
                    continue;
                }
                if (value.startsWith(STATIC_PREFIX)) {
                    staticMembers.add(value);
                } else if (value.startsWith(LDAP_PREFIX)) {
                    ldapMembers.add(value);
                } else {
                    otherMembers.add(value);
                }
            }
        }
        return new AwxOrgTeamDetailsDTO(name, organization, staticMembers, ldapMembers, otherMembers);
    }

    public List<String> getManageableOrganizationNames() {
        final JsonNode organizationMap = fetchOrganizationMap();
        final String username = AuthUtils.getUsername();

        final List<String> result = new ArrayList<>();
        for (final String orgName : organizationMap.propertyNames()) {
            if (!EXCLUDED_DEFAULT_NAME.equalsIgnoreCase(orgName) && isAdminOf(organizationMap.get(orgName), username)) {
                result.add(orgName);
            }
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    public List<String> getManageableTeamNames() {
        final JsonNode organizationMap = fetchOrganizationMap();
        final JsonNode teamMap = fetchTeamMap();
        final String username = AuthUtils.getUsername();
        final Set<String> adminOrganizations = adminOrganizationNames(organizationMap, username);

        final List<String> result = new ArrayList<>();
        for (final String teamName : teamMap.propertyNames()) {
            final String organization = teamMap.get(teamName).path("organization").asString(null);
            if (!EXCLUDED_DEFAULT_NAME.equalsIgnoreCase(teamName)
                    && organization != null && containsIgnoreCase(adminOrganizations, organization)) {
                result.add(teamName);
            }
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    /**
     * Checks whether an organization with the given name already exists in AWX and, if so,
     * whether the current user is allowed to manage it. Used before an "add" action so that
     * creating an already-existing organization is treated as an edit requiring admin rights.
     */
    public AwxNameCheckDTO checkOrganization(final String name) {
        final JsonNode organizationMap = fetchOrganizationMap();
        final JsonNode organization = findCaseInsensitive(organizationMap, name);
        if (organization == null) {
            return new AwxNameCheckDTO(false, true);
        }
        return new AwxNameCheckDTO(true, isAdminOf(organization, AuthUtils.getUsername()));
    }

    /**
     * Checks whether a team with the given name already exists in AWX and, if so, whether the
     * current user is allowed to manage it (is an admin of the team's organization).
     */
    public AwxNameCheckDTO checkTeam(final String name) {
        final JsonNode teamMap = fetchTeamMap();
        final JsonNode team = findCaseInsensitive(teamMap, name);
        if (team == null) {
            return new AwxNameCheckDTO(false, true);
        }

        final String organization = team.path("organization").asString(null);
        if (organization == null) {
            return new AwxNameCheckDTO(true, false);
        }

        final JsonNode organizationMap = fetchOrganizationMap();
        final JsonNode organizationEntry = findCaseInsensitive(organizationMap, organization);
        final boolean allowed = organizationEntry != null && isAdminOf(organizationEntry, AuthUtils.getUsername());
        return new AwxNameCheckDTO(true, allowed);
    }

    private Set<String> adminOrganizationNames(final JsonNode organizationMap, final String username) {
        final Set<String> adminOrganizations = new HashSet<>();
        for (final String orgName : organizationMap.propertyNames()) {
            if (isAdminOf(organizationMap.get(orgName), username)) {
                adminOrganizations.add(orgName);
            }
        }
        return adminOrganizations;
    }

    /**
     * The "admins" field of a SOCIAL_AUTH_ORGANIZATION_MAP entry is either a boolean (applies to
     * all authenticated users) or a list of entries - matched here against the current user's
     * username, either plain (legacy) or as {@code STATIC:<username>}.
     */
    private boolean isAdminOf(final JsonNode organizationMapEntry, final String username) {
        if (organizationMapEntry == null || username == null) {
            return false;
        }
        final JsonNode admins = organizationMapEntry.path("admins");
        if (admins.isBoolean()) {
            return admins.asBoolean();
        }
        if (admins.isArray()) {
            for (final JsonNode admin : admins) {
                final String value = admin.asString("");
                if (username.equalsIgnoreCase(value) || (STATIC_PREFIX + username).equalsIgnoreCase(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsIgnoreCase(final Set<String> values, final String value) {
        return values.stream().anyMatch(v -> v.equalsIgnoreCase(value));
    }

    private JsonNode findCaseInsensitive(final JsonNode map, final String name) {
        final String key = findKey(map, name);
        return key == null ? null : map.get(key);
    }

    private String findKey(final JsonNode map, final String name) {
        if (map == null || name == null) {
            return null;
        }
        for (final String fieldName : map.propertyNames()) {
            if (fieldName.equalsIgnoreCase(name)) {
                return fieldName;
            }
        }
        return null;
    }

    private JsonNode fetchOrganizationMap() {
        return fetchAuthenticationSettings().path(ORGANIZATION_MAP_FIELD);
    }

    private JsonNode fetchTeamMap() {
        return fetchAuthenticationSettings().path(TEAM_MAP_FIELD);
    }

    private JsonNode fetchAuthenticationSettings() {
        if (environment.getProperty(STUB_ENABLED_PROPERTY, Boolean.class, false)) {
            log.warn("Property '{}' is enabled - returning stubbed AWX authentication settings instead of calling AWX.",
                    STUB_ENABLED_PROPERTY);
            try {
                return objectMapper.readTree(STUB_AUTHENTICATION_SETTINGS_JSON);
            } catch (JacksonException e) {
                throw new IllegalStateException("Could not parse stubbed AWX authentication settings.", e);
            }
        }

        final AwxConfigDTO awxConfig = getAwxPConfig();

        final String url = awxConfig.apiEndpoint() + SETTINGS_PATH;
        final HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, "sessionid=" + loginAndGetSessionId(awxConfig));
        final ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);

        final JsonNode body = response.getBody();
        if (body == null) {
            throw new IllegalStateException("AWX-Authentifizierungseinstellungen konnten nicht geladen werden.");
        }
        return body;
    }

    /**
     * fetch a CSRF token from {@code /api/login/}, post the credentials,
     * and return the resulting {@code sessionid} cookie.
     */
    private String loginAndGetSessionId(final AwxConfigDTO awxConfig) {
        final String loginUrl = awxConfig.apiEndpoint() + LOGIN_PATH;

        final HttpHeaders csrfRequestHeaders = new HttpHeaders();
        csrfRequestHeaders.setAccept(List.of(MediaType.TEXT_HTML));
        final String csrfToken = cookieValue(
                restTemplate.exchange(loginUrl, HttpMethod.GET, new HttpEntity<>(csrfRequestHeaders), Void.class).getHeaders(),
                "csrftoken");
        if (csrfToken == null) {
            throw new IllegalStateException("AWX-Login fehlgeschlagen: kein CSRF-Token erhalten.");
        }

        final HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.TEXT_HTML));
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set(HttpHeaders.COOKIE, "csrftoken=" + csrfToken);
        headers.set(HttpHeaders.REFERER, loginUrl);
        headers.set("X-CSRFToken", csrfToken);

        final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", awxConfig.apiUsername());
        form.add("password", awxConfig.apiPassword());
        form.add("next", SETTINGS_PATH);
        form.add("csrfmiddlewaretoken", csrfToken);

        final ResponseEntity<Void> response = restTemplate.exchange(
                loginUrl, HttpMethod.POST, new HttpEntity<>(form, headers), Void.class);
        final String sessionId = cookieValue(response.getHeaders(), "sessionid");
        if (sessionId == null) {
            throw new IllegalStateException("AWX-Login fehlgeschlagen: Benutzername oder Passwort ungültig.");
        }
        return sessionId;
    }

    private String cookieValue(final HttpHeaders headers, final String name) {
        return headers.getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .flatMap(header -> HttpCookie.parse(header).stream())
                .filter(cookie -> name.equals(cookie.getName()))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private AwxConfigDTO getAwxPConfig() {
        return awxConfigService.getAwxConfigs().stream()
                .filter(AwxConfigDTO::enabled)
                .filter(config -> AWX_CONFIG_DESCRIPTION.equalsIgnoreCase(config.apiDescription()))
                .findFirst()
                .map(config -> awxConfigService.getAwxDecrypted(config.id()))
                .orElseThrow(() -> new IllegalStateException(
                        "Keine aktive AWX-Konfiguration mit der Bezeichnung '" + AWX_CONFIG_DESCRIPTION + "' gefunden."));
    }
}
