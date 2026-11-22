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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Resolves AWX organizations and teams that the current user is allowed to manage.
 * <p>
 * This service reads the
 * {@code SOCIAL_AUTH_ORGANIZATION_MAP} and {@code SOCIAL_AUTH_TEAM_MAP} entries from
 * {@code /api/v2/settings/authentication/}. These maps define, per organization, which
 * usernames are listed as "admins". All teams live in the "Default" organization; a team is
 * only manageable if the current user is listed as one of its users.
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
     */
    public record AwxOrgTeamDetailsDTO(String name, List<String> staticMembers,
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
        return toDetails(orgName, organization.path("admins"));
    }

    /**
     * Returns the current users of a team. Only available to users who are members of the team.
     *
     * @throws NoSuchElementException if the team does not exist
     * @throws AccessDeniedException  if the current user is not a member of the team
     */
    public AwxOrgTeamDetailsDTO getTeamDetails(final String name) {
        final JsonNode teamMap = fetchTeamMap();
        final String teamName = findKey(teamMap, name);
        if (teamName == null) {
            throw new NoSuchElementException("Team not found: " + name);
        }
        final JsonNode team = teamMap.get(teamName);
        if (EXCLUDED_DEFAULT_NAME.equalsIgnoreCase(teamName) || !isMemberOf(team, AuthUtils.getUsername())) {
            log.warn("User {} tried to read AWX team {} without permission.", AuthUtils.getUsername(), teamName);
            throw new AccessDeniedException("You are not allowed to manage this team.");
        }
        return toDetails(teamName, team.path("users"));
    }

    /**
     * The members field is either a boolean (all/no users) or a list of entries; booleans carry no
     * explicit members and yield empty lists. Only entries with the exact {@code STATIC:} or
     * {@code LDAP:} prefix are editable, everything else is returned as read-only.
     */
    private AwxOrgTeamDetailsDTO toDetails(final String name, final JsonNode members) {
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
        return new AwxOrgTeamDetailsDTO(name, staticMembers, ldapMembers, otherMembers);
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
        final JsonNode teamMap = fetchTeamMap();
        final String username = AuthUtils.getUsername();

        final List<String> result = new ArrayList<>();
        for (final String teamName : teamMap.propertyNames()) {
            if (!EXCLUDED_DEFAULT_NAME.equalsIgnoreCase(teamName) && isMemberOf(teamMap.get(teamName), username)) {
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
     * current user is allowed to manage it (is a member of the team).
     */
    public AwxNameCheckDTO checkTeam(final String name) {
        final JsonNode teamMap = fetchTeamMap();
        final JsonNode team = findCaseInsensitive(teamMap, name);
        if (team == null) {
            return new AwxNameCheckDTO(false, true);
        }
        return new AwxNameCheckDTO(true, isMemberOf(team, AuthUtils.getUsername()));
    }

    /**
     * The "admins" field of a SOCIAL_AUTH_ORGANIZATION_MAP entry is either a boolean (applies to
     * all authenticated users) or a list of entries - matched here against the current user's
     * username, either plain (legacy) or as {@code STATIC:<username>}.
     */
    private boolean isAdminOf(final JsonNode organizationMapEntry, final String username) {
        if (organizationMapEntry == null) {
            return false;
        }
        final JsonNode admins = organizationMapEntry.path("admins");
        return admins.isBoolean() ? admins.asBoolean() : listsUsername(admins, username);
    }

    /**
     * The "users" field of a SOCIAL_AUTH_TEAM_MAP entry is either a boolean or a list of entries. Only
     * an explicit entry makes the current user a member; a boolean does not.
     */
    private boolean isMemberOf(final JsonNode teamMapEntry, final String username) {
        return teamMapEntry != null && listsUsername(teamMapEntry.path("users"), username);
    }

    private boolean listsUsername(final JsonNode entries, final String username) {
        if (username == null || !entries.isArray()) {
            return false;
        }
        for (final JsonNode entry : entries) {
            final String value = entry.asString("");
            if (username.equalsIgnoreCase(value) || (STATIC_PREFIX + username).equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
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
        headers.set(HttpHeaders.COOKIE, loginAndGetCookies(awxConfig));
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
     * and return the resulting session cookies as a {@code Cookie} header value.
     */
    private String loginAndGetCookies(final AwxConfigDTO awxConfig) {
        final String loginUrl = awxConfig.apiEndpoint() + LOGIN_PATH;

        final HttpHeaders csrfRequestHeaders = new HttpHeaders();
        csrfRequestHeaders.setAccept(List.of(MediaType.TEXT_HTML));
        final Map<String, String> cookies = new LinkedHashMap<>();
        collectCookies(
                restTemplate.exchange(loginUrl, HttpMethod.GET, new HttpEntity<>(csrfRequestHeaders), Void.class).getHeaders(),
                cookies);
        final String csrfToken = cookies.get("csrftoken");
        if (csrfToken == null) {
            throw new IllegalStateException("AWX-Login fehlgeschlagen: kein CSRF-Token erhalten.");
        }

        final HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.TEXT_HTML));
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set(HttpHeaders.COOKIE, cookieHeader(cookies));
        headers.set(HttpHeaders.REFERER, loginUrl);
        headers.set("X-CSRFToken", csrfToken);

        final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", awxConfig.apiUsername());
        form.add("password", awxConfig.apiPassword());
        form.add("next", SETTINGS_PATH);
        form.add("csrfmiddlewaretoken", csrfToken);

        final ResponseEntity<Void> response = restTemplate.exchange(
                loginUrl, HttpMethod.POST, new HttpEntity<>(form, headers), Void.class);
        collectCookies(response.getHeaders(), cookies);
        if (!response.getStatusCode().is3xxRedirection()) {
            throw new IllegalStateException("AWX-Login fehlgeschlagen: unerwartete Antwort " + response.getStatusCode());
        }
        return cookieHeader(cookies);
    }

    private void collectCookies(final HttpHeaders headers, final Map<String, String> cookies) {
        headers.getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .flatMap(header -> HttpCookie.parse(header).stream())
                .forEach(cookie -> cookies.put(cookie.getName(), cookie.getValue()));
    }

    private String cookieHeader(final Map<String, String> cookies) {
        return cookies.entrySet().stream()
                .map(cookie -> cookie.getKey() + "=" + cookie.getValue())
                .collect(Collectors.joining("; "));
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
