package de.muenchen.mcmp.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.muenchen.mcmp.dto.NetworkRequestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.common.HttpMethods;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import de.muenchen.mcmp.config.InfobloxProperties;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class EaiRouteBuilder extends RouteBuilder {

    @Value("${output}")
    private String outputRoute;

    private final InfobloxProperties infobloxProperties;

    @Value("${infoblox.controller.endpoint}")
    private String infobloxControllerEndpoint;

    // JSON-Datei für Mock-Daten
    @Value("${infoblox.mock.json.file:infoblox-data.json}")
    private String mockJsonFile;

    // Backup-Verzeichnis für JSON-Dateien
    @Value("${backup.directory:.}")
    private String backupDirectory;

    // Keycloak Konfiguration für Service Token
    @Value("${keycloak.auth-server-url}")
    private String keycloakAuthServerUrl;

    @Value("${keycloak.realm}")
    private String keycloakRealm;

    @Value("${keycloak.service.client-id}")
    private String serviceClientId;

    @Value("${keycloak.service.client-secret}")
    private String serviceClientSecret;

    public static final String DIRECT_ROUTE = "direct:eai-route";
    public static final String DIRECT_INFOBLOX_ROUTE = "direct:infoblox-route";
    public static final String DIRECT_GET_JWT_TOKEN = "direct:get-jwt-token";
    public static final String DIRECT_BACKUP_JSON = "direct:backup-json";
    public static final String ID_ROUTE_BACKUP_JSON = "ID_ROUTE_BACKUP_JSON";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApplicationContext applicationContext;
    private final Map<Integer, String> cidrToNetmaskCache = new ConcurrentHashMap<>();

    @Override
    public void configure() {
        onException(Exception.class)
                .handled(true)
                .log(LoggingLevel.ERROR, "Fehler bei der Verarbeitung: ${exception.message}");

        // Backup-Route für JSON-Daten
        from(DIRECT_BACKUP_JSON)
                .routeId(ID_ROUTE_BACKUP_JSON)
                .log(LoggingLevel.INFO, "Erstelle JSON-Backup für Fehlersuche...")
                .process(exchange -> {
                    exchange.getIn().setHeader(Exchange.FILE_NAME, "infoblox_export.json");
                })
                .to("file://" + backupDirectory + "?fileExist=Override")
                .log(LoggingLevel.INFO, "JSON-Backup erfolgreich erstellt: ${header.CamelFileName}");

        // HTTP-Route für GET API-Aufrufe
        from("direct:makeHttpCall")
                .routeId("http-call-route")
                .log(LoggingLevel.DEBUG, "Führe HTTP GET Call aus: ${header.targetUrl}")
                .setHeader(Exchange.HTTP_URI, header("targetUrl"))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .to("http://dummy?throwExceptionOnFailure=false")
                .log(LoggingLevel.DEBUG, "HTTP Response Code: ${header.CamelHttpResponseCode}");

        // HTTP-Route für POST API-Aufrufe
        from("direct:makeHttpPostCall")
                .routeId("http-post-call-route")
                .log(LoggingLevel.DEBUG, "Führe HTTP POST Call aus: ${header.targetUrl}")
                .setHeader(Exchange.HTTP_URI, header("targetUrl"))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .to("http://dummy?throwExceptionOnFailure=false")
                .log(LoggingLevel.DEBUG, "HTTP Response Code: ${header.CamelHttpResponseCode}");

        // Rest Ihrer bestehenden Routen...
        from("timer://infobloxTimer?delay=5000&repeatCount=1")
                .routeId("infoblox-timer-route")
                .log(LoggingLevel.INFO, "Timer ausgelöst - starte Infoblox Verarbeitung")
                .to(DIRECT_INFOBLOX_ROUTE)
                .process(exchange -> {
                    log.info("Infoblox Verarbeitung abgeschlossen - beende Anwendung");
                    new Thread(() -> {
                        try {
                            Thread.sleep(20000);
                            SpringApplication.exit(applicationContext, () -> 0);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                });

        from(DIRECT_ROUTE)
                .routeId("eai-route")
                .log(LoggingLevel.DEBUG, "de.muenchen", "Starte EAI Route...")
                .to(outputRoute);

        from(DIRECT_GET_JWT_TOKEN)
                .routeId("jwt-token-route")
                .log(LoggingLevel.DEBUG, "Hole JWT Token von Keycloak...")
                .process(exchange -> {
                    final String tokenEndpoint = keycloakAuthServerUrl + "/realms/" + keycloakRealm + "/protocol/openid-connect/token";
                    final String requestBody = "grant_type=client_credentials&client_id=" + serviceClientId + "&client_secret=" + serviceClientSecret;
                    exchange.getIn().setHeader("targetUrl", tokenEndpoint);
                    exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/x-www-form-urlencoded");
                    exchange.getIn().setHeader("Accept", "application/json");
                    exchange.getIn().setBody(requestBody);
                })
                .to("direct:makeHttpPostCall")
                .process(exchange -> {
                    String responseBody = exchange.getIn().getBody(String.class);
                    JsonNode tokenResponse = objectMapper.readTree(responseBody);

                    String accessToken = tokenResponse.path("access_token").asText();
                    if (accessToken.isEmpty()) {
                        throw new RuntimeException("Kein Access Token in Keycloak Response erhalten");
                    }

                    exchange.setProperty("jwtAccessToken", accessToken);
                    log.info("JWT Token erfolgreich erhalten");
                });

        from(DIRECT_INFOBLOX_ROUTE)
                .routeId("infoblox-route")
                .log(LoggingLevel.INFO, "Starte Infoblox Datenabfrage...")
                .to(DIRECT_GET_JWT_TOKEN)
                .process(exchange -> {
                    List<NetworkRequestDTO> allNetworks = collectAllInfobloxData();
                    if (allNetworks != null && !allNetworks.isEmpty()) {
                        log.info("Alle Infoblox Server erfolgreich abgefragt. Sende {} Netzwerke zur MCMP", allNetworks.size());
                        sendToController(allNetworks, exchange);
                    } else {
                        log.warn("Nicht alle Infoblox Server konnten erfolgreich abgefragt werden. Keine Daten werden zur MCMP gesendet.");
                    }
                })
                .log(LoggingLevel.INFO, "Infoblox Datenabfrage abgeschlossen");
    }

    private Map<String, InfobloxProperties.ServerConfig> getInfobloxServers() {
        return infobloxProperties.getServers();
    }

    /**
     * Sammelt Daten von allen aktivierten Infoblox-Servern.
     * Gibt null zurück, wenn mindestens ein Server fehlschlägt oder nicht erreichbar ist.
     */
    private List<NetworkRequestDTO> collectAllInfobloxData() {
        List<NetworkRequestDTO> allNetworks = new ArrayList<>();
        Map<String, InfobloxProperties.ServerConfig> servers = getInfobloxServers();

        // Filtere nur aktivierte Server
        List<String> enabledServers = servers.entrySet().stream()
                .filter(entry -> entry.getValue().isEnabled())
                .map(Map.Entry::getKey)
                .toList();
        if (enabledServers.isEmpty()) {
            log.warn("Keine aktivierten Infoblox Server gefunden");
            return null;
        }
        log.info("Beginne Abfrage von {} aktivierten Infoblox Servern: {}", enabledServers.size(), enabledServers);
        for (String serverName : enabledServers) {
            try {
                List<NetworkRequestDTO> serverNetworks = processInfobloxServer(serverName);
                if (serverNetworks == null) {
                    log.error("Server {} konnte nicht erfolgreich abgefragt werden", serverName);
                    return null; // Bei Fehler eines Servers: Abbruch
                }
                allNetworks.addAll(serverNetworks);
                log.info("Server {} erfolgreich abgefragt: {} Netzwerke erhalten", serverName, serverNetworks.size());
            } catch (Exception e) {
                log.error("Fehler beim Verarbeiten von Server {}: {}", serverName, e.getMessage(), e);
                return null; // Bei Exception: Abbruch
            }
        }
        log.info("Alle {} Server erfolgreich abgefragt. Insgesamt {} Netzwerke gesammelt", enabledServers.size(), allNetworks.size());
        return allNetworks;
    }

    /**
     * Verarbeitet einen einzelnen Infoblox-Server und gibt die Netzwerke zurück.
     * Gibt null zurück bei Fehlern.
     */
    private List<NetworkRequestDTO> processInfobloxServer(String serverName) {
        try {
            Map<String, InfobloxProperties.ServerConfig> infobloxServers = getInfobloxServers();
            InfobloxProperties.ServerConfig serverConfig = infobloxServers.get(serverName);
            if (serverConfig == null) {
                log.error("Konfiguration für Server {} nicht gefunden", serverName);
                return null;
            }
            String baseUrl = serverConfig.getBaseUrl();
            String username = serverConfig.getUsername();
            String password = serverConfig.getPassword();
            String request = serverConfig.getRequest();

            log.info("Verarbeite Server: {}", serverName);
            return processInfobloxRequest(serverName, baseUrl, username, password, request);
        } catch (Exception e) {
            log.error("Fehler beim Verarbeiten von Server {}: {}", serverName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Führt den Request zu einem Infoblox-Server aus und parst die Antwort.
     * Gibt null zurück bei Fehlern.
     */
    private List<NetworkRequestDTO> processInfobloxRequest(String serverName, String baseUrl, String username, String password, String requestPath) {
        try {
            log.debug("Führe Request aus für Server {}: {} {}", serverName, baseUrl, requestPath);
            String infobloxResponse = callInfobloxApi(baseUrl, username, password, requestPath);
            if (infobloxResponse == null || infobloxResponse.trim().isEmpty()) {
                log.error("Leere oder null Response von Server {} für Request {}", serverName, requestPath);
                return null;
            }
            List<NetworkRequestDTO> networks = parseInfobloxResponse(infobloxResponse, baseUrl, serverName);
            log.info("Server {} lieferte {} Netzwerke für Request {}", serverName, networks.size(), requestPath);
            return networks;
        } catch (Exception e) {
            log.error("Fehler beim Verarbeiten von Request {} für Server {}: {}", requestPath, serverName, e.getMessage(), e);
            return null;
        }
    }

    private String callInfobloxApi(String baseUrl, String username, String password, String requestPath) {
        try {
            String fullUrl = baseUrl + "/" + requestPath;
            log.info("Rufe Infoblox API auf: {}", fullUrl);
            Exchange exchange = getContext().getEndpoint("direct:makeHttpCall").createExchange();
            if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
                String auth = username + ":" + password;
                String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes());
                exchange.getIn().setHeader("Authorization", "Basic " + encodedAuth);
            }
            exchange.getIn().setHeader("Content-Type", "application/json");
            exchange.getIn().setHeader("Accept", "application/json");
            exchange.getIn().setHeader("CamelHttpTimeout", 30000);
            exchange.getIn().setHeader("targetUrl", fullUrl);
            try (var producerTemplate = getContext().createProducerTemplate()) {
                producerTemplate.send("direct:makeHttpCall", exchange);
            }
            String response = exchange.getIn().getBody(String.class);
            Integer responseCode = exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
            log.info("HTTP Response Code: {}", responseCode);
            log.info("Antwort erhalten: {} Zeichen", response != null ? response.length() : 0);
            // Prüfe auf erfolgreichen HTTP Status Code
            if (responseCode == null || responseCode < 200 || responseCode >= 300) {
                log.error("HTTP Fehler für Infoblox API Call. Response Code: {}", responseCode);
                return null;
            }
            return response;
        } catch (Exception e) {
            log.error("Fehler beim Aufruf der Infoblox API: {}", e.getMessage(), e);
            return null;
        }
    }

    private List<NetworkRequestDTO> parseInfobloxResponse(String responseBody, String baseUrl, String serverName) {
        List<NetworkRequestDTO> networks = new ArrayList<>();
        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode resultArray = rootNode.get("result");
            if (resultArray != null && resultArray.isArray()) {
                for (JsonNode networkNode : resultArray) {
                    NetworkRequestDTO network = parseNetworkFromInfoblox(networkNode, baseUrl, serverName);
                    if (network != null) {
                        networks.add(network);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Fehler beim Parsen der Infoblox Response von Server {}: {}", serverName, e.getMessage(), e);
        }
        return networks;
    }

    private void sendToController(List<NetworkRequestDTO> networks, Exchange mainExchange) {
        try {
            String jwtToken = mainExchange.getProperty("jwtAccessToken", String.class);
            if (jwtToken == null || jwtToken.isEmpty()) {
                log.error("JWT Token nicht verfügbar für Controller-Aufruf");
                return;
            }
            String jsonBody = objectMapper.writeValueAsString(networks);

            // JSON-Backup erstellen vor dem Senden
            createJsonBackup(jsonBody);

            Exchange exchange = getContext().getEndpoint("direct:makeHttpPostCall").createExchange();
            exchange.getIn().setHeader("targetUrl", infobloxControllerEndpoint);
            exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
            exchange.getIn().setHeader("Authorization", "Bearer " + jwtToken);
            exchange.getIn().setHeader("Accept", "application/json");
            exchange.getIn().setBody(jsonBody);
            //log.info("Sende alle gesammelten Netzwerkdaten zur MCMP:\n{}", jsonBody);
            try (var producerTemplate = getContext().createProducerTemplate()) {
                producerTemplate.send("direct:makeHttpPostCall", exchange);
            }
            Integer responseCode = exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
            if (responseCode != null && responseCode >= 200 && responseCode < 300) {
                log.info("Erfolgreich {} Netzwerke an Controller gesendet (Response Code: {})", networks.size(), responseCode);
            } else {
                log.error("Controller Response Code: {}", responseCode);
                String responseBody = exchange.getIn().getBody(String.class);
                log.error("Controller Response Body: {}", responseBody);
            }
        } catch (Exception e) {
            log.error("Fehler beim Senden an Controller: {}", e.getMessage(), e);
        }
    }

    /**
     * Erstellt ein Backup der JSON-Daten ins Dateisystem
     * @param jsonData Die JSON-Daten als String
     */
    private void createJsonBackup(String jsonData) {
        try {
            Exchange backupExchange = getContext().getEndpoint(DIRECT_BACKUP_JSON).createExchange();
            backupExchange.getIn().setBody(jsonData);

            try (var producerTemplate = getContext().createProducerTemplate()) {
                producerTemplate.send(DIRECT_BACKUP_JSON, backupExchange);
            }
        } catch (Exception e) {
            log.error("Fehler beim Erstellen des JSON-Backups: {}", e.getMessage(), e);
        }
    }

    private NetworkRequestDTO parseNetworkFromInfoblox(JsonNode networkNode, String baseUrl, String serverName) {
        try {
            if (networkNode == null || networkNode.isMissingNode()) {
                log.warn("NetworkNode ist null oder fehlt für Server {}", serverName);
                return null;
            }
            NetworkInfo networkInfo = extractNetworkInfo(networkNode);
            if (networkInfo == null) {
                log.warn("Network Node fehlt");
            }
            ExtendedAttributes extAttrs = extractExtendedAttributes(networkNode.path("extattrs"));
            if (extAttrs == null) {
                log.warn("Extended attributes fehlen");
                return null;
            }
            NetworkConfig networkConfig = extractNetworkConfig(networkNode);
            Integer[] vlans = extractVlans(networkNode);
            String comment = Optional.ofNullable(networkNode.path("comment").asText(""))
                    .filter(c -> !c.isEmpty())
                    .orElse(null);
            return NetworkRequestDTO.builder()
                    .apiEndpoint(baseUrl)
                    .vlans(vlans)
                    .cidr(networkInfo.cidr())
                    .ipAddress(networkInfo.ipAddress())
                    .netmask(networkInfo.netmaskString())
                    .gateway(networkConfig.gateway())
                    .broadcast(networkConfig.broadcast())
                    .dnsPrimary(networkConfig.dnsPrimary())
                    .dnsSecondary(networkConfig.dnsSecondary())
                    .name(extAttrs.name())
                    .referat(extAttrs.referat())
                    .environment(determineEnvironmentFromServer(extAttrs.klassifizierung()))
                    .networktype(extAttrs.networktype())
                    .comment(comment)
                    .mcmpStatus(extAttrs.mcmpStatus)
                    .mcmpNetworkTyp(extAttrs.mcmpNetworkTyp)
                    .mcmpNetworkGroup(extAttrs.mcmpNetworkGroup)
                    .build();
        } catch (NumberFormatException e) {
            log.error("Ungültiges Zahlenformat beim Parsen des Netzwerks von Server {}: {}", serverName, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Fehler beim Parsen des Netzwerks von Server {}: {}", serverName, e.getMessage(), e);
            return null;
        }
    }

    private record NetworkInfo(String cidr, String ipAddress, String netmaskString) {}

    private record ExtendedAttributes(
            String name,
            String referat,
            String networktype,
            String adresse,
            String klassifizierung,
            Boolean mcmpStatus,
            String mcmpNetworkTyp,
            String mcmpNetworkGroup) {}

    private record NetworkConfig(String gateway, String broadcast, String dnsPrimary, String dnsSecondary) {}

    private NetworkInfo extractNetworkInfo(JsonNode networkNode) {
        String network = networkNode.path("network").asText("");
        if (network.isEmpty()) {
            return null;
        }

        String[] networkParts = network.split("/");
        String ipAddress = networkParts.length > 0 ? networkParts[0] : "";

        int netmask = networkNode.path("netmask").asInt(24);
        String netmaskString = convertCidrToNetmask(netmask);

        return new NetworkInfo(network, ipAddress, netmaskString);
    }

    private ExtendedAttributes extractExtendedAttributes(JsonNode extattrs) {
        if (extattrs.isMissingNode()) {
            return null;
        }
        return new ExtendedAttributes(
                extattrs.path("VSwitchID").path("value").asText(""),
                extattrs.path("Referat").path("value").asText(""),
                extattrs.path("Netztyp").path("value").asText(""),
                extattrs.path("Adresse").path("value").asText(""),
                extattrs.path("Klassifizierung").path("value").asText(""),
                parseMcmpStatus(extattrs.path("mcmp_status").path("value").asText("false")),
                extattrs.path("mcmp_network_typ").path("value").asText(""),
                extattrs.path("mcmp_networkgroup").path("value").asText("")
        );
    }

    private Integer parseVlan(String vlanString) {
        try {
            return Integer.parseInt(vlanString);
        } catch (NumberFormatException e) {
            log.warn("Ungültige VLAN-ID '{}', verwende Standardwert 0", vlanString);
            return 0;
        }
    }

    private Boolean parseMcmpStatus(String mcmpStatusString) {
        return Boolean.parseBoolean(mcmpStatusString);
    }

    private NetworkConfig extractNetworkConfig(JsonNode networkNode) {
        String gateway = extractGatewayFromOptions(networkNode.path("options"));
        String broadcast = extractBroadcastFromOptions(networkNode.path("options"));
        String[] dnsServers = extractDnsServersFromMembers(networkNode.path("members"));
        String dnsPrimary = dnsServers.length > 0 ? dnsServers[0] : null;
        String dnsSecondary = dnsServers.length > 1 ? dnsServers[1] : null;

        return new NetworkConfig(gateway, broadcast, dnsPrimary, dnsSecondary);
    }

    private Integer[] extractVlans(JsonNode networkNode) {
        final JsonNode vlansNode = networkNode.path("vlans");
        final List<Integer> vlanList = new ArrayList<>();
        if (vlansNode != null && vlansNode.isArray()) {
            for (JsonNode vlanNode : vlansNode) {
                int id = vlanNode.path("id").asInt(-1);
                if (id >= 0) {
                    vlanList.add(id);
                }
            }
        }
        vlanList.sort(Integer::compareTo);
        if (vlanList.isEmpty()) {
            return new Integer[0];
        }
        return vlanList.toArray(new Integer[0]);
    }

    private String determineEnvironmentFromServer(String environment) {
        if ("Konsolidierung".equalsIgnoreCase(environment)) {
            return "K";
        }
        if ("Produktiv".equalsIgnoreCase(environment)) {
            return "P";
        }
        if ("Coding".equalsIgnoreCase(environment)) {
            return "C";
        }
        if ("Testlabor".equalsIgnoreCase(environment)) {
            return "TL";
        }
        return environment;
    }

    private String extractGatewayFromOptions(JsonNode options) {
        if (options != null && options.isArray()) {
            for (JsonNode option : options) {
                if ("routers".equals(option.path("name").asText())) {
                    return option.path("value").asText("");
                }
            }
        }
        return "";
    }

    private String extractBroadcastFromOptions(JsonNode options) {
        if (options != null && options.isArray()) {
            for (JsonNode option : options) {
                if ("broadcast-address".equals(option.path("name").asText())) {
                    return option.path("value").asText("");
                }
            }
        }
        return "";
    }

    private String[] extractDnsServersFromMembers(JsonNode members) {
        final List<String> dnsServers = new ArrayList<>();
        if (members != null && members.isArray()) {
            for (JsonNode member : members) {
                if ("dhcpmember".equals(member.path("_struct").asText())) {
                    String ipv4addr = member.path("ipv4addr").asText();
                    if (!ipv4addr.isEmpty()) {
                        dnsServers.add(ipv4addr);
                        log.debug("DNS-Server aus Member extrahiert: {} ({})", ipv4addr, member.path("name").asText());
                    }
                }
            }
        }
        dnsServers.sort(String::compareTo);
        if (!dnsServers.isEmpty()) {
            log.debug("Extrahierte DNS-Server: Primary={}, Secondary={}",
                    dnsServers.size() > 0 ? dnsServers.get(0) : "null",
                    dnsServers.size() > 1 ? dnsServers.get(1) : "null");
        }
        return dnsServers.toArray(new String[0]);
    }

    private String convertCidrToNetmask(int cidr) {
        return cidrToNetmaskCache.computeIfAbsent(cidr, this::calculateNetmask);
    }

    private String calculateNetmask(int cidr) {
        int mask = 0xffffffff << (32 - cidr);
        return String.format("%d.%d.%d.%d",
                (mask >> 24) & 0xff,
                (mask >> 16) & 0xff,
                (mask >> 8) & 0xff,
                mask & 0xff);
    }
}
