package de.muenchen.mcmp.repository;

import de.muenchen.mcmp.types.EnvironmentType;

import java.util.Collection;

/**
 * Which servers may be attached to which Paketshop repositories, based on the environment of their Application Services.
 * Production (P) servers may only use production repositories and test (C/K) servers may not use production
 * repositories. C and K are interchangeable.
 */
public final class PaketshopEnvironmentRule {

    private PaketshopEnvironmentRule() {
    }

    public static boolean isCompatible(final EnvironmentType serverEnvironment, final EnvironmentType repositoryEnvironment) {
        return !(isProduction(serverEnvironment) && isTest(repositoryEnvironment))
                && !(isTest(serverEnvironment) && isProduction(repositoryEnvironment));
    }

    /**
     * @return true if production (P) items must be excluded because one of the given counterpart environments is C/K
     */
    public static boolean excludesProduction(final Collection<EnvironmentType> counterpartEnvironments) {
        return counterpartEnvironments.stream().anyMatch(PaketshopEnvironmentRule::isTest);
    }

    /**
     * @return true if test (C/K) items must be excluded because one of the given counterpart environments is P
     */
    public static boolean excludesTest(final Collection<EnvironmentType> counterpartEnvironments) {
        return counterpartEnvironments.stream().anyMatch(PaketshopEnvironmentRule::isProduction);
    }

    private static boolean isProduction(final EnvironmentType environment) {
        return environment == EnvironmentType.P;
    }

    private static boolean isTest(final EnvironmentType environment) {
        return environment == EnvironmentType.C || environment == EnvironmentType.K;
    }
}
