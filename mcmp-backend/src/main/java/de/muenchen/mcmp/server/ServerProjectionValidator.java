package de.muenchen.mcmp.server;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The ServerProjectionValidator class is used to validate the consistency of getter methods
 * between the Server entity and its corresponding ServerWithPermissions projection.
 * It performs a comparison of method names to ensure that the projection aligns
 * with the entity, while excluding certain methods that are explicitly ignored during validation.
 * <p>
 * The class logs warnings if inconsistencies are detected, such as missing or additional
 * getter methods in the projection compared to the entity.
 * <p>
 * This validation is executed during the initialization phase of the application.
 * <p>
 * Annotations:
 * - {@code @Component}: Indicates that this class is a Spring component and eligible for
 * component-scanning and dependency injection.
 * - {@code @Slf4j}: Provides a logger instance for logging messages.
 * <p>
 * Methods:
 * - {@code validateProjection()}: Compares getter methods between the Server entity and
 * the ServerWithPermissions projection. Logs warnings if any discrepancies are found.
 */
@Component
@Slf4j
public class ServerProjectionValidator {

    /**
     * Validates the consistency of getter methods between the Server entity and the
     * ServerWithPermissions projection. This method ensures that the getter methods present
     * in the Server entity are accurately reflected in the projection, excluding specific methods
     * that are deliberately ignored during the validation process.
     * <p>
     * During execution, this method:
     * - Retrieves all public methods from the Server entity and ServerWithPermissions projection.
     * - Filters the methods to only include parameterless getters (methods starting with "get").
     * - Excludes methods inherited from the Object class.
     * - Excludes specific getters that are explicitly ignored as part of the validation criteria.
     * - Compares the sets of filtered getter method names from the entity and projection.
     * - Logs warnings if any discrepancies are detected, such as missing or additional getter methods.
     * <p>
     * The validation is performed automatically during the application's initialization phase,
     * leveraging the {@code @PostConstruct} annotation.
     * <p>
     * Logging:
     * - Warnings are logged if the methods in the projection deviate from the methods in the entity.
     * - Logs indicate missing or additional methods in the projection compared to the entity.
     * <p>
     * This ensures that the projection remains in sync with the entity, maintaining the integrity
     * of the application's data model and preventing potential issues arising from method mismatches.
     */
    @PostConstruct
    public void validateProjection() {
        Method[] serverMethods = Server.class.getMethods();
        Method[] projectionMethods = ServerWithPermissions.class.getMethods();
        Set<String> serverGetters = Arrays.stream(serverMethods)
                .filter(m -> m.getName().startsWith("get") && m.getParameterCount() == 0)
                .filter(m -> !m.getDeclaringClass().equals(Object.class)) // Object-Methoden ausschließen
                .map(Method::getName)
                .collect(Collectors.toSet());
        Set<String> projectionGetters = Arrays.stream(projectionMethods)
                .filter(m -> m.getName().startsWith("get") && m.getParameterCount() == 0)
                .filter(m -> !m.getDeclaringClass().equals(Object.class)) // Object-Methoden ausschließen
                .map(Method::getName)
                .collect(Collectors.toSet());
        projectionGetters.remove("getCanEdit");
        projectionGetters.remove("getCloudId");
        projectionGetters.remove("getCanEdit");
        projectionGetters.remove("getCloudId");
        projectionGetters.remove("getCloudFqdn");
        projectionGetters.remove("getCloudServerGui");
        projectionGetters.remove("getHasTempAdminPrivileges");
        projectionGetters.remove("getHasTempRootPrivileges");
        projectionGetters.remove("getTempPrivilegesExpiresAt");
        projectionGetters.remove("getRunningGreenItCount");
        projectionGetters.remove("getRunningJobsCount");
        projectionGetters.remove("getCloudType");
        projectionGetters.remove("getCloudName");
        projectionGetters.remove("getNumberOfAssignedAppservices");
        serverGetters.remove("getCloud");
        serverGetters.remove("getAppservices");
        if (!serverGetters.equals(projectionGetters)) {
            log.warn("ServerWithPermissions Projection is not synchronized with Server Entity!");
            log.warn("Missing in Projection: {}", serverGetters.stream().filter(g -> !projectionGetters.contains(g)).collect(Collectors.toSet()));
            log.warn("Additional in Projection: {}", projectionGetters.stream().filter(g -> !serverGetters.contains(g)).collect(Collectors.toSet()));
        }
    }
}
