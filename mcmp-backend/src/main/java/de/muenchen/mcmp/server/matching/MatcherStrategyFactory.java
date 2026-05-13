package de.muenchen.mcmp.server.matching;

import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.server.ServerService;
import de.muenchen.mcmp.server.ServerUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * Factory for creating specialized server matching strategies.
 *
 * <p>This factory class provides a centralized way to create various types of server matchers
 * that can be used to find servers based on different identification criteria. It acts as
 * an abstraction layer between the matching logic and the underlying cache and service layers.</p>
 *
 * <p><strong>Supported Matching Strategies:</strong></p>
 * <ul>
 *   <li><strong>IP Address Matching:</strong> Direct database lookups for dynamic IP addresses</li>
 *   <li><strong>MAC Address Matching:</strong> Direct database lookups for network hardware identification</li>
 *   <li><strong>UUID Matching:</strong> Cached lookups for server unique identifiers</li>
 *   <li><strong>Instance UUID Matching:</strong> Cached lookups for virtual machine instance identifiers</li>
 *   <li><strong>Foreman ID Matching:</strong> Cached lookups for Red Hat Satellite/Foreman system IDs</li>
 *   <li><strong>ServiceNow System ID Matching:</strong> Cached lookups for ITSM system identifiers</li>
 *   <li><strong>Serial Number Matching:</strong> Specialized matching with VMware serial number conversion</li>
 * </ul>
 *
 * <p><strong>Caching Strategy:</strong></p>
 * <p>The factory leverages a two-tier caching approach:</p>
 * <ul>
 *   <li><strong>Cached Lookups:</strong> Static identifiers (UUIDs, system IDs) are cached for fast retrieval</li>
 *   <li><strong>Direct Lookups:</strong> Dynamic identifiers (IP, MAC addresses) bypass cache due to frequent changes</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * // Create matchers for different data types
 * ServerMatcher<ForemanHostDTO> foremanMatcher = factory.createUuidMatcher(ForemanHostDTO::getUuid);
 * ServerMatcher<SnowCIDTO> snowMatcher = factory.createInstanceSysIdMatcher(SnowCIDTO::getSysId);
 *
 * // Use matchers in import processes
 * factory.loadCacheForImport();
 * try {
 *     Server server = foremanMatcher.match(foremanHost);
 *     // Process matched server
 * } finally {
 *     factory.clearCacheAfterImport();
 * }
 * }</pre>
 *
 * <p><strong>Thread Safety:</strong></p>
 * <p>This factory is thread-safe. The underlying cache and service dependencies
 * handle concurrent access appropriately.</p>
 *
 * <p><strong>Performance Considerations:</strong></p>
 * <ul>
 *   <li>Load cache before bulk operations for optimal performance</li>
 *   <li>Clear cache after bulk operations to free memory</li>
 *   <li>Cached lookups are significantly faster than database queries</li>
 *   <li>Serial number processing includes VMware-specific optimizations</li>
 * </ul>
 *
 * @author System
 * @version 1.2
 * @since 1.0
 * @see ServerMatcher
 * @see ServerCache
 * @see GenericMatcherStrategy
 * @see ServerUtils#convertSerialToUUID(String)
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class MatcherStrategyFactory {

    /**
     * Service for direct database operations when cache is not available.
     */
    private final ServerService serverService;

    /**
     * Cache for optimized server lookups with static identifiers.
     */
    private final ServerCache serverCache;

    // Factory Methods for Different Matching Strategies

    /**
     * Creates a matcher for IP address-based server identification.
     *
     * <p>IP addresses are not cached due to their dynamic nature and frequent changes.
     * Each lookup will result in a direct database query.</p>
     *
     * <p><strong>Use Cases:</strong></p>
     * <ul>
     *   <li>Network monitoring systems reporting server status by IP</li>
     *   <li>Log analysis where only IP addresses are available</li>
     *   <li>Network discovery tools</li>
     * </ul>
     *
     * <p><strong>Performance Note:</strong></p>
     * <p>Consider batching IP address lookups when processing large datasets
     * to minimize database query overhead.</p>
     *
     * @param <T> the type of data object containing IP address information
     * @param extractor function to extract IP address from the data object
     * @return a configured server matcher for IP address lookup
     * @throws IllegalArgumentException if extractor is null
     */
    public <T> ServerMatcher<T> createIpAddressMatcher(final Function<T, String> extractor) {
        if (extractor == null) {
            throw new IllegalArgumentException("IP address extractor cannot be null");
        }
        return createMatcher(extractor, "IP address", serverCache::findServersByIpAddress);
    }

    /**
     * Creates a matcher for MAC address-based server identification.
     *
     * <p>MAC addresses are not cached due to their potential for change during
     * network card replacements or virtual machine migrations.</p>
     *
     * <p><strong>Use Cases:</strong></p>
     * <ul>
     *   <li>Network asset management systems</li>
     *   <li>Wake-on-LAN functionality</li>
     *   <li>Network access control systems</li>
     *   <li>DHCP reservation management</li>
     * </ul>
     *
     * <p><strong>Format Considerations:</strong></p>
     * <p>MAC addresses should be in standard format (e.g., "AA:BB:CC:DD:EE:FF").
     * The matcher does not perform format normalization.</p>
     *
     * @param <T> the type of data object containing MAC address information
     * @param extractor function to extract MAC address from the data object
     * @return a configured server matcher for MAC address lookup
     * @throws IllegalArgumentException if extractor is null
     */
    public <T> ServerMatcher<T> createMacAddressMatcher(final Function<T, String> extractor) {
        if (extractor == null) {
            throw new IllegalArgumentException("MAC address extractor cannot be null");
        }
        return createMatcher(extractor, "MAC address", serverCache::findServersByMacAddress);
    }

    /**
     * Creates a matcher for UUID-based server identification with caching.
     *
     * <p>UUIDs are cached for optimal performance as they are immutable server identifiers.
     * This matcher provides the fastest lookup performance for UUID-based matching.</p>
     *
     * <p><strong>Use Cases:</strong></p>
     * <ul>
     *   <li>Primary server identification in most systems</li>
     *   <li>Cross-system server correlation</li>
     *   <li>Audit trail matching</li>
     *   <li>Configuration management database (CMDB) synchronization</li>
     * </ul>
     *
     * <p><strong>UUID Format:</strong></p>
     * <p>Supports standard UUID format (8-4-4-4-12 hexadecimal digits separated by hyphens).</p>
     *
     * @param <T> the type of data object containing UUID information
     * @param extractor function to extract UUID from the data object
     * @return a configured server matcher for cached UUID lookup
     * @throws IllegalArgumentException if extractor is null
     */
    public <T> ServerMatcher<T> createUuidMatcher(final Function<T, String> extractor) {
        if (extractor == null) {
            throw new IllegalArgumentException("UUID extractor cannot be null");
        }
        return createMatcher(extractor, "UUID (cached)", serverCache::findServersByUuid);
    }

    /**
     * Creates a matcher for instance UUID-based server identification with caching.
     *
     * <p>Instance UUIDs are typically used in virtualized environments to identify
     * virtual machine instances. Multiple servers can share the same instance UUID
     * in certain virtualization scenarios.</p>
     *
     * <p><strong>Use Cases:</strong></p>
     * <ul>
     *   <li>VMware vCenter integration</li>
     *   <li>Hyper-V system management</li>
     *   <li>OpenStack nova instance tracking</li>
     *   <li>Container orchestration platform integration</li>
     * </ul>
     *
     * <p><strong>Virtualization Context:</strong></p>
     * <p>In some virtualization scenarios, multiple server records may reference
     * the same instance UUID (e.g., clustered VMs or template-based deployments).</p>
     *
     * @param <T> the type of data object containing instance UUID information
     * @param extractor function to extract instance UUID from the data object
     * @return a configured server matcher for cached instance UUID lookup
     * @throws IllegalArgumentException if extractor is null
     */
    public <T> ServerMatcher<T> createInstanceUuidMatcher(final Function<T, String> extractor) {
        if (extractor == null) {
            throw new IllegalArgumentException("Instance UUID extractor cannot be null");
        }
        return createMatcher(extractor, "Instance UUID (cached)", serverCache::findServersByInstanceUuid);
    }

    /**
     * Creates a matcher for Foreman ID-based server identification with caching.
     *
     * <p>Foreman IDs are used by Red Hat Satellite/Foreman systems for host management.
     * These are typically numeric identifiers that remain stable throughout the
     * server's lifecycle in the management system.</p>
     *
     * <p><strong>Use Cases:</strong></p>
     * <ul>
     *   <li>Red Hat Satellite host synchronization</li>
     *   <li>Puppet Enterprise integration</li>
     *   <li>Configuration management correlation</li>
     *   <li>Patch management system integration</li>
     * </ul>
     *
     * <p><strong>Data Format:</strong></p>
     * <p>Foreman IDs are converted to string format for lookup. The extractor
     * should return the numeric ID as a string.</p>
     *
     * @param <T> the type of data object containing Foreman ID information
     * @param extractor function to extract Foreman ID from the data object
     * @return a configured server matcher for cached Foreman ID lookup
     * @throws IllegalArgumentException if extractor is null
     */
    public <T> ServerMatcher<T> createForemanIdMatcher(final Function<T, String> extractor) {
        if (extractor == null) {
            throw new IllegalArgumentException("Foreman ID extractor cannot be null");
        }
        return createMatcher(extractor, "Foreman ID (cached)", serverCache::findServersByForemanId);
    }

    /**
     * Creates a matcher for ServiceNow instance system ID-based identification with caching.
     *
     * <p>Instance system IDs are used by ServiceNow ITSM systems to track
     * virtual machine instances and their lifecycle within the service management context.</p>
     *
     * <p><strong>Use Cases:</strong></p>
     * <ul>
     *   <li>ServiceNow CMDB synchronization for VM instances</li>
     *   <li>ITSM incident correlation with virtual infrastructure</li>
     *   <li>Change management for virtualized workloads</li>
     *   <li>Service catalog automation</li>
     * </ul>
     *
     * <p><strong>ServiceNow Context:</strong></p>
     * <p>These IDs typically come from ServiceNow's cmdb_ci_vmware_instance
     * or similar configuration item types.</p>
     *
     * @param <T> the type of data object containing instance system ID information
     * @param extractor function to extract instance system ID from the data object
     * @return a configured server matcher for cached instance system ID lookup
     * @throws IllegalArgumentException if extractor is null
     */
    public <T> ServerMatcher<T> createInstanceSysIdMatcher(final Function<T, String> extractor) {
        if (extractor == null) {
            throw new IllegalArgumentException("Instance system ID extractor cannot be null");
        }
        return createMatcher(extractor, "Instance SysID (cached)", serverCache::findServersByInstanceSysId);
    }

    /**
     * Creates a matcher for ServiceNow server system ID-based identification with caching.
     *
     * <p>Server system IDs are used by ServiceNow ITSM systems to track
     * physical and virtual servers within the service management context.</p>
     *
     * <p><strong>Use Cases:</strong></p>
     * <ul>
     *   <li>ServiceNow CMDB synchronization for server assets</li>
     *   <li>ITSM incident and problem management</li>
     *   <li>Asset lifecycle management</li>
     *   <li>Compliance and audit reporting</li>
     * </ul>
     *
     * <p><strong>ServiceNow Context:</strong></p>
     * <p>These IDs typically come from ServiceNow's cmdb_ci_win_server,
     * cmdb_ci_linux_server, or similar configuration item types.</p>
     *
     * @param <T> the type of data object containing server system ID information
     * @param extractor function to extract server system ID from the data object
     * @return a configured server matcher for cached server system ID lookup
     * @throws IllegalArgumentException if extractor is null
     */
    public <T> ServerMatcher<T> createServerSysIdMatcher(final Function<T, String> extractor) {
        if (extractor == null) {
            throw new IllegalArgumentException("Server system ID extractor cannot be null");
        }
        return createMatcher(extractor, "Server SysID (cached)", serverCache::findServersByServerSysId);
    }

    /**
     * Creates a matcher for serial number-based server identification with VMware optimization.
     *
     * <p>This matcher includes special processing for VMware serial numbers, which follow
     * a specific format and can be converted to UUIDs for more reliable matching.
     * Non-VMware serial numbers are processed without conversion.</p>
     *
     * <p><strong>VMware Serial Number Processing:</strong></p>
     * <p>VMware serial numbers typically follow the format:</p>
     * <pre>VMware-XX XX XX XX XX XX XX XX-XX XX XX XX XX XX XX XX</pre>
     * <p>These are automatically converted to standard UUID format for lookup.</p>
     *
     * <p><strong>Use Cases:</strong></p>
     * <ul>
     *   <li>Hardware asset discovery and correlation</li>
     *   <li>VMware vCenter integration with serial number mapping</li>
     *   <li>Physical server identification in data centers</li>
     *   <li>Warranty and support contract correlation</li>
     * </ul>
     *
     * <p><strong>Processing Logic:</strong></p>
     * <ol>
     *   <li>Extract serial number using provided extractor</li>
     *   <li>Check if serial number starts with "VMware-"</li>
     *   <li>If VMware format: convert to UUID and lookup by UUID</li>
     *   <li>If not VMware format: lookup by original serial number</li>
     * </ol>
     *
     * @param <T> the type of data object containing serial number information
     * @param extractor function to extract serial number from the data object
     * @return a configured server matcher for serial number lookup with VMware optimization
     * @throws IllegalArgumentException if extractor is null
     * @see ServerUtils#convertSerialToUUID(String)
     */
    public <T> ServerMatcher<T> createSerialNumberMatcher(final Function<T, String> extractor) {
        if (extractor == null) {
            throw new IllegalArgumentException("Serial number extractor cannot be null");
        }
        return createMatcher(extractor, "Serial number", serverCache::findServersByUuid, this::processSerialNumber);
    }

    // Cache Management Delegation

    /**
     * Loads the server cache for optimized import operations.
     *
     * <p>This method delegates to the underlying cache to load all servers
     * into memory for fast lookup during bulk import or synchronization operations.</p>
     *
     * <p><strong>Best Practices:</strong></p>
     * <ul>
     *   <li>Call this method before starting bulk import operations</li>
     *   <li>Ensure sufficient memory is available for the cache</li>
     *   <li>Monitor cache loading time for performance optimization</li>
     *   <li>Always pair with {@link #clearCacheAfterImport()} to free memory</li>
     * </ul>
     *
     * <p><strong>Performance Impact:</strong></p>
     * <p>Cache loading may take significant time for large server datasets but
     * dramatically improves lookup performance during bulk operations.</p>
     *
     * @throws RuntimeException if cache loading fails due to database errors
     * @see ServerCache#loadCache()
     */
    public void loadCacheForImport() {
        log.debug("Initiating cache loading for import operation");
        serverCache.loadCache();
    }

    /**
     * Clears the server cache after import operations to free memory.
     *
     * <p>This method should be called after completing bulk import or synchronization
     * operations to free memory used by the cache. The cache will automatically
     * fall back to database queries for subsequent lookups.</p>
     *
     * <p><strong>Usage Pattern:</strong></p>
     * <pre>{@code
     * try {
     *     factory.loadCacheForImport();
     *     // Perform bulk import operations
     *     processBulkData();
     * } finally {
     *     factory.clearCacheAfterImport();
     * }
     * }</pre>
     *
     * @see ServerCache#clearCache()
     */
    public void clearCacheAfterImport() {
        log.debug("Clearing cache after import operation completion");
        serverCache.clearCache();
    }

    // Private Factory Helper Methods

    /**
     * Creates a generic matcher with the specified configuration.
     *
     * @param <T> the type of data object to match
     * @param extractor function to extract identifier from data object
     * @param type description of the identifier type for logging
     * @param finder function to find servers by identifier
     * @return configured generic matcher strategy
     */
    private <T> ServerMatcher<T> createMatcher(final Function<T, String> extractor,
                                               final String type,
                                               final Function<String, List<Server>> finder) {
        return new GenericMatcherStrategy<>(serverService, extractor, type, finder);
    }

    /**
     * Creates a generic matcher with identifier processing.
     *
     * @param <T> the type of data object to match
     * @param extractor function to extract identifier from data object
     * @param type description of the identifier type for logging
     * @param finder function to find servers by processed identifier
     * @param processor function to process/transform identifier before lookup
     * @return configured generic matcher strategy with processing
     */
    private <T> ServerMatcher<T> createMatcher(final Function<T, String> extractor,
                                               final String type,
                                               final Function<String, List<Server>> finder,
                                               final Function<String, String> processor) {
        return new GenericMatcherStrategy<>(serverService, extractor, type, finder, processor);
    }

    /**
     * Processes serial numbers with special handling for VMware formats.
     *
     * <p>This method implements the VMware serial number conversion logic,
     * transforming VMware-specific serial numbers into UUIDs for more reliable
     * server matching. Non-VMware serial numbers are returned unchanged.</p>
     *
     * <p><strong>VMware Serial Format:</strong></p>
     * <p>VMware serial numbers follow the pattern:</p>
     * <pre>VMware-56 4d 2c 5f 8a 92 1a 2f-87 3e 9c 1d 4b 5a 6e 7f</pre>
     * <p>Which converts to UUID:</p>
     * <pre>564d2c5f-8a92-1a2f-873e-9c1d4b5a6e7f</pre>
     *
     * <p><strong>Error Handling:</strong></p>
     * <p>If VMware serial number conversion fails, the method returns null,
     * which will result in no servers being found for that identifier.</p>
     *
     * @param identifier the serial number to process
     * @return processed identifier (UUID for VMware serials, original for others), or null if conversion fails
     * @see ServerUtils#convertSerialToUUID(String)
     */
    private String processSerialNumber(final String identifier) {
        if (identifier == null) {
            return null;
        }

        if (identifier.startsWith("VMware-")) {
            final String uuid = ServerUtils.convertSerialToUUID(identifier);
            if (uuid != null) {
                log.debug("Converted serial number to UUID: {} -> {}", identifier, uuid);
                return uuid;
            } else {
                log.warn("Could not convert serial number to UUID: {}", identifier);
                return null;
            }
        }

        // Non-VMware serial numbers are returned as-is
        return identifier;
    }
}