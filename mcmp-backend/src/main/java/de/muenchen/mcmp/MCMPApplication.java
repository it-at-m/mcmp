package de.muenchen.mcmp;

import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Entry point for the MCMP application.
 * <p>
 * This class serves as the primary configuration and bootstrap for the MCMP application.
 * It utilizes Spring Boot's {@code @SpringBootApplication} annotation to enable component scanning,
 * auto-configuration, and property support.
 * <p>
 * Key features enabled in this application include:
 * - Configuration property scanning using {@code @ConfigurationPropertiesScan}.
 * - Proxy-based transaction management enabled via {@code @EnableTransactionManagement}.
 * - Support for asynchronous method execution with {@code @EnableAsync}.
 * - Scheduled task execution support provided by {@code @EnableScheduling}.
 * <p>
 * A static initializer is included to configure SLF4J/Logback as the logging framework
 * by bridging java.util.logging (used by Tomcat/Catalina) to SLF4J.
 * <p>
 * Note: This class is not intended to act as a utility class and is explicitly marked
 * with the {@code @SuppressWarnings("PMD.UseUtilityClass")} annotation to avoid incorrect
 * linting for utility-oriented constructs.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableTransactionManagement(mode = AdviceMode.PROXY, proxyTargetClass = true)
@EnableAsync
@EnableScheduling
@SuppressWarnings("PMD.UseUtilityClass")
public class MCMPApplication {

    static {
        // Bridge java.util.logging (Tomcat/Catalina) to SLF4J/Logback
        // This must run before Spring Boot initializes
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }

    public static void main(final String[] args) {
        SpringApplication.run(MCMPApplication.class, args);
    }
}
