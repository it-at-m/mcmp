package de.muenchen.mcmp.infoblox;

import de.muenchen.mcmp.appservice.Appservice;
import de.muenchen.mcmp.appservice.AppserviceService;
import de.muenchen.mcmp.cloud.Cloud;
import de.muenchen.mcmp.cloud.CloudService;
import de.muenchen.mcmp.http.HttpClientFactory;
import de.muenchen.mcmp.http.JsonUtils;
import de.muenchen.mcmp.infoblox.json.JsonInfobloxSearch;
import de.muenchen.mcmp.infobloxConfig.InfobloxConfigService;
import de.muenchen.mcmp.infobloxConfig.InfobloxConfigWithDecryptedPassword;
import de.muenchen.mcmp.job.JobRepository;
import de.muenchen.mcmp.sleeper.Sleeper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for interacting with Infoblox API to perform domain-name-related operations.
 * This service integrates with Infoblox to compute available fully qualified domain names (FQDNs),
 * perform hostname searches, and retrieve configurations.
 * <p>
 * The service relies on other components such as cloud services and app services to determine
 * configurations and dependencies. It also enforces validation on inputs and handles potential
 * exceptions during communication with external systems.
 */
@Service
@AllArgsConstructor
@Slf4j
public class InfobloxService {

    private final InfobloxConfigService infobloxConfigService;
    private final CloudService cloudService;
    private final AppserviceService appserviceService;
    private final JobRepository jobRepository;
    private final HttpClientFactory httpClientFactory;
    private final Sleeper sleeper;
    private final InfobloxProperties infobloxProperties;

    /**
     * Calculates a fully qualified domain name (FQDN) based on the given parameters, validates input values,
     * and interacts with external systems like Infoblox to determine an available FQDN.
     *
     * @param prefix the prefix to be included in the hostname
     * @param application the application identifier forming part of the hostname
     * @param serverType the server type to be used in the hostname; must pass validation
     * @param applicationServiceId the ID of the application service to associate with the hostname
     * @param customNumber the custom starting number for generating hostnames
     * @param domain the domain name to complete the FQDN
     * @param cloudId the ID of the cloud configuration; if null, a default configuration will be used
     * @return the calculated FQDN that is available for use
     * @throws InvalidInputException if any input is invalid, required configurations are missing, or the FQDN cannot be calculated
     */
    public String calculateFqdn(final String prefix, final String application, final String serverType, final Long applicationServiceId, final Integer customNumber, final String domain, final Long cloudId) throws InvalidInputException {
        String normalizedServerType = ServernameUtils.normalizeAndValidateServerType(serverType);
        String normalizedPrefix = ServernameUtils.normalizeAndValidatePrefix(prefix, normalizedServerType);
        String normalizedApplication = ServernameUtils.normalizeAndValidateApplication(application, !normalizedPrefix.isBlank());
        String normalizedDomain = ServernameUtils.normalizeAndValidateDomain(domain);
        int normalizedCustomNumber = ServernameUtils.validateCustomNumber(customNumber);

        if (applicationServiceId == null) {
            throw new NoSuchElementException("Ein Anwendungsservice muss ausgewählt sein!");
        }
        final Appservice appservice = appserviceService.getAppservice(applicationServiceId);
        if (appservice == null) {
            throw new NoSuchElementException("Der Anwendungsservice existiert nicht oder Sie haben nicht die erforderlichen Rechte!");
        }

        if (infobloxProperties.isSkipSearch()) {
            return buildHostname(normalizedPrefix, normalizedApplication, normalizedServerType, appservice.getCurrentEnvironment(), normalizedCustomNumber, normalizedDomain + ".skip.Infoblox");
        }

        Long infobloxConfigId = null;
        if (cloudId != null) {
            Cloud cloud = cloudService.findById(cloudId);
            if (cloud != null) {
                infobloxConfigId = cloud.getConfigInfobloxId();
            }
        }
        final InfobloxConfigWithDecryptedPassword infobloxConfig = (infobloxConfigId != null) ? infobloxConfigService.findByIdDecrypted(infobloxConfigId) : infobloxConfigService.findDefaultInfoblox();
        if (infobloxConfig == null) {
            throw new InvalidInputException("Die Infoblox Zugangsdaten konnten nicht ermittelt werden!");
        }

        if (infobloxConfig.getApiUsername() == null || infobloxConfig.getApiUsername().isBlank() ||
            infobloxConfig.getApiPassword() == null || infobloxConfig.getApiPassword().isBlank() ||
            infobloxConfig.getApiEndpoint() == null || infobloxConfig.getApiEndpoint().isBlank()) {
            throw new InvalidInputException("Ungültige Infoblox Zugangsdaten!");
        }

        final Set<String> blockedFQDNs = findHostnamesForActiveServerInstallations();
        final String endpoint = normalizeEndpoint(infobloxConfig.getApiEndpoint()) + "search?_max_results=5000&_return_type=json&_return_fields=name&fqdn~=";
        try (var httpClient = httpClientFactory.createHttpClientWithBasicAuthentication(infobloxConfig.getApiUsername(), infobloxConfig.getApiPassword())) {
            final ArrayList<String> searchFQDNs = new ArrayList<>();
            for (int no = normalizedCustomNumber; no < normalizedCustomNumber + 1000; no++) {
                int serverNumber = no % 1000;
                if (serverNumber == 0) continue;
                final String hostname = buildHostname(normalizedPrefix, normalizedApplication, normalizedServerType, appservice.getCurrentEnvironment(), serverNumber, normalizedDomain);
                searchFQDNs.add(hostname);
                if (searchFQDNs.size() >= 5 || no == normalizedCustomNumber + 999) {
                    final String searchFQDN = String.join("|", searchFQDNs);
                    try {
                        final Set<String> infobloxEntries = searchFqdn(httpClient, endpoint, searchFQDN, blockedFQDNs);
                        if (infobloxEntries == null || infobloxEntries.isEmpty()) {
                            return searchFQDNs.getFirst();
                        }
                        for (final String fqdn : searchFQDNs) {
                            if (!infobloxEntries.contains(fqdn)) {
                                return fqdn;
                            }
                        }
                    } catch (Exception e) {
                        log.error("Fehler bei der Abfrage der Infoblox (endpoint={}, batchSize={}, message={})", endpoint, searchFQDNs.size(), e.getMessage(), e);
                        throw new InvalidInputException("Fehler bei der Abfrage der Infoblox!");
                    }
                    searchFQDNs.clear();
                }
            }
        } catch (IOException e) {
            log.error("IO-Fehler bei der Abfrage der Infoblox (endpoint={}, message={})", endpoint, e.getMessage(), e);
            throw new InvalidInputException("Fehler bei der Abfrage der Infoblox!");
        }
        throw new IllegalArgumentException("Alle 999 möglichen Hostnamen wurden bereits vergeben!");
    }

    /**
     * Calculates and validates a DNS entry for a loadbalancer by checking availability in Infoblox.
     * The DNS entry is constructed as dnsName.muenchen.de and validated against existing entries.
     *
     * @param dnsName              the desired DNS name (without domain suffix)
     * @param applicationServiceId the ID of the application service to associate with the DNS entry
     * @return the full DNS entry (dnsName.muenchen.de) if available
     * @throws InvalidInputException if any input is invalid, required configurations are missing, or the DNS entry already exists
     */
    public String calculateDnsEntry(final String dnsName, final Long applicationServiceId) throws InvalidInputException {
        // Validate input
        String normalizedDnsName;
        try {
            normalizedDnsName = ServernameUtils.normalizeAndValidateDnsName(dnsName);
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException(e.getMessage());
        }

        if (applicationServiceId == null) {
            throw new NoSuchElementException("Ein Anwendungsservice muss ausgewählt sein!");
        }

        final Appservice appservice = appserviceService.getAppservice(applicationServiceId);
        if (appservice == null) {
            throw new NoSuchElementException("Der Anwendungsservice existiert nicht oder Sie haben nicht die erforderlichen Rechte!");
        }

        final String domain = "muenchen.de";
        final String fullDnsEntry = normalizedDnsName + "." + domain;

        if (infobloxProperties.isSkipSearch()) {
            return normalizedDnsName + "." + domain + ".skip.infoblox";
        }

        final InfobloxConfigWithDecryptedPassword infobloxConfig = infobloxConfigService.findDefaultInfoblox();
        if (infobloxConfig == null) {
            throw new InvalidInputException("Die Infoblox Zugangsdaten konnten nicht ermittelt werden!");
        }

        if (infobloxConfig.getApiUsername() == null || infobloxConfig.getApiUsername().isBlank() ||
                infobloxConfig.getApiPassword() == null || infobloxConfig.getApiPassword().isBlank() ||
                infobloxConfig.getApiEndpoint() == null || infobloxConfig.getApiEndpoint().isBlank()) {
            throw new InvalidInputException("Ungültige Infoblox Zugangsdaten!");
        }

        final Set<String> blockedFQDNs = findHostnamesForActiveServerInstallations();
        final String endpoint = normalizeEndpoint(infobloxConfig.getApiEndpoint()) + "search?_max_results=5000&_return_type=json&_return_fields=name&fqdn~=";

        try (var httpClient = httpClientFactory.createHttpClientWithBasicAuthentication(infobloxConfig.getApiUsername(), infobloxConfig.getApiPassword())) {
            try {
                final Set<String> infobloxEntries = searchFqdn(httpClient, endpoint, fullDnsEntry, blockedFQDNs);
                if (infobloxEntries != null && infobloxEntries.contains(fullDnsEntry)) {
                    throw new InvalidInputException("Der DNS-Eintrag '" + fullDnsEntry + "' existiert bereits in Infoblox!");
                }
                return fullDnsEntry;
            } catch (InvalidInputException e) {
                throw e;
            } catch (Exception e) {
                log.error("Fehler bei der Abfrage der Infoblox (endpoint={}, dnsEntry={}, message={})", endpoint, fullDnsEntry, e.getMessage(), e);
                throw new InvalidInputException("Fehler bei der Abfrage der Infoblox!");
            }
        } catch (IOException e) {
            log.error("IO-Fehler bei der Abfrage der Infoblox (endpoint={}, message={})", endpoint, e.getMessage(), e);
            throw new InvalidInputException("Fehler bei der Abfrage der Infoblox!");
        }
    }

    /**
     * Searches for fully qualified domain names (FQDNs) by querying a specified endpoint.
     * The method adds the results to an initial set of blocked FQDNs and handles retry logic
     * in case of failures. If the input search query is invalid, an exception is thrown.
     *
     * @param httpClient The HTTP client used to perform the requests.
     * @param endpoint The base URL of the endpoint to which the search query will be appended.
     * @param searchFQDNs The search query for finding FQDNs. This must not be null or blank.
     * @param blockedFQDNs The initial set of FQDNs that are blocked. Results will be added to this set.
     * @return A set of FQDNs, combining the blocked FQDNs and the results from the search query.
     * @throws Exception If an error occurs while processing the request.
     */
    protected Set<String> searchFqdn(final CloseableHttpClient httpClient, final String endpoint, final String searchFQDNs, final Set<String> blockedFQDNs) throws Exception {
        final Set<String> fqdns = new HashSet<>(blockedFQDNs);
        if (searchFQDNs == null || searchFQDNs.isBlank()) {
            throw new InvalidInputException("Der Hostname darf nicht leer sein!");
        }

        //System.setProperty("socksProxyHost", "localhost");
        //System.setProperty("socksProxyPort", String.valueOf(10080));
        //System.setProperty("socksProxyVersion", "5");
        final String encodedSearchFQDNs = URLEncoder.encode(searchFQDNs, StandardCharsets.UTF_8);
        for (int error = 0; error <= 3; error++) {
            try {
                JsonInfobloxSearch[] results = JsonUtils.httpGet(httpClient, endpoint + encodedSearchFQDNs, JsonInfobloxSearch[].class);
                if (results != null) {
                    for (JsonInfobloxSearch entry : results) {
                        if (entry.getName() != null && !entry.getName().isBlank()) {
                            fqdns.add(entry.getName());
                        }
                    }
                }
                return fqdns;
            } catch (Exception e) {
                log.warn("Fehler bei der Abfrage der Infoblox (Versuch {} von 4): {}", error + 1, e.getMessage());
                sleeper.sleep(200L * (error + 1));
            }
        }
        return fqdns;
    }

    /**
     * Ensures that the given API endpoint string ends with a forward slash ('/').
     * If the string does not already end with a slash, one will be appended.
     *
     * @param apiEndpoint the API endpoint string to normalize. It must not be null.
     * @return the normalized API endpoint string that ends with a forward slash.
     */
    private String normalizeEndpoint(String apiEndpoint) {
        return apiEndpoint.endsWith("/") ? apiEndpoint : apiEndpoint + "/";
    }

    /**
     * Builds and returns a fully qualified hostname string based on the provided parameters.
     *
     * @param prefix the prefix to be used in the hostname
     * @param application the name of the application to include in the hostname
     * @param serverType the type of server to identify in the hostname
     * @param environment the environment (e.g., production, development) to include in the hostname
     * @param serverNumber the specific server number to append to the hostname
     * @param domain the domain to be appended to the hostname
     * @return the constructed fully qualified hostname string
     */
    private String buildHostname(String prefix, String application, String serverType, String environment, int serverNumber, String domain) {
        return prefix + application + serverType + environment + ServernameUtils.formatCustomNumber(serverNumber) + "." + domain;
    }

    // Moved here to avoid circular dependencies with jobService needing InfobloxService to calc fqdn
    public Set<String> findHostnamesForActiveServerInstallations() {
        List<String> hostnames = jobRepository.findHostnamesForActiveServerInstallations();
        return hostnames.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }


}
