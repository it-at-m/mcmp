package de.muenchen.mcmp.job;

import lombok.Getter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Applications that can be installed on a new Linux server. Each application maps to an AWX install flag
 * and may require other applications to be installed as well.
 */
public enum LinuxServerApplication {
    APACHE("Apache", "webserver_install"),
    JAVA("Java", "java_install"),
    PHP("PHP", "php_install", APACHE),
    TOMCAT("Tomcat", "tomcat_install", APACHE, JAVA);

    private final String label;
    @Getter
    private final String awxInstallParam;
    private final List<LinuxServerApplication> requires;

    LinuxServerApplication(final String label, final String awxInstallParam, final LinuxServerApplication... requires) {
        this.label = label;
        this.awxInstallParam = awxInstallParam;
        this.requires = List.of(requires);
    }

    /**
     * Parses the given labels and validates that all requirements between the applications are met.
     *
     * @throws IllegalArgumentException if the selection is empty, a label is unknown or a requirement is missing
     */
    public static Set<LinuxServerApplication> parseSelection(final List<?> labels) {
        if (labels.isEmpty()) {
            throw new IllegalArgumentException("At least one application must be selected.");
        }
        final Set<LinuxServerApplication> selection = EnumSet.noneOf(LinuxServerApplication.class);
        for (Object label : labels) {
            selection.add(Arrays.stream(values())
                    .filter(app -> app.label.equals(String.valueOf(label)))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown application: " + label)));
        }
        for (LinuxServerApplication app : selection) {
            for (LinuxServerApplication required : app.requires) {
                if (!selection.contains(required)) {
                    throw new IllegalArgumentException(app.label + " requires " + required.label + ".");
                }
            }
        }
        return selection;
    }
}
