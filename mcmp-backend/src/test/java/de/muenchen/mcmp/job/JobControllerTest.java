package de.muenchen.mcmp.job;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for DNS validation by invoking the JobController helper via reflection.
 */
public class JobControllerTest {

    private static void validateDnsEntry(String dns) {
        try {
            JobController controller = new JobController(null, null, null, null, null, null, null, null, null);
            java.lang.reflect.Method method = JobController.class.getDeclaredMethod("validateDnsEntry", String.class);
            method.setAccessible(true);
            method.invoke(controller, dns);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void validDns_shouldNotThrow() {
        assertDoesNotThrow(() -> validateDnsEntry("my-host.example.com"));
    }

    @Test
    public void missingDot_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> validateDnsEntry("abc"));
    }

    @Test
    public void startsWithHyphen_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> validateDnsEntry("-aaaa.example.com"));
    }

    @Test
    public void endsWithHyphen_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> validateDnsEntry("abc.example-.com"));
    }

    @Test
    public void firstLabelTooShort_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> validateDnsEntry("ab.example.com"));
    }

    @Test
    public void labelTooLong_shouldThrow() {
        String longLabel = "a".repeat(65);
        String dns = longLabel + ".example.com";
        assertThrows(IllegalArgumentException.class, () -> validateDnsEntry(dns));
    }

    @Test
    public void emptyLabel_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> validateDnsEntry("abc..example.com"));
    }

    @Test
    public void invalidCharacter_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> validateDnsEntry("host!.example.com"));
    }

    @Test
    public void uppercaseCharacter_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> validateDnsEntry("Host.example.com"));
    }

    @Test
    public void nullDns_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> validateDnsEntry(null));
    }

    @Test
    public void overallLengthTooLong_shouldThrow() {
        String label63 = "a".repeat(63);
        String dns = label63 + "." + label63 + "." + label63 + "." + label63 + "." + label63;
        assertThrows(IllegalArgumentException.class, () -> validateDnsEntry(dns));
    }

    @Test
    public void maxLabelLengths_shouldNotThrow() {
        String label64 = "a".repeat(64);
        String label3 = "abc";
        assertDoesNotThrow(() -> validateDnsEntry(label3 + "." + label64 + "." + label64));
    }
}
