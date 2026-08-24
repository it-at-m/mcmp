package de.muenchen.mcmp.job;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This class tests the private validation and formatting methods of {@link JobService}.
 */
public class JobServiceValidationTest {

    private static final JobService JOB_SERVICE = new JobService(
            null, null, null, null, null, null, null, null, null, null, null, null
    );

    @AfterEach
    public void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static Object invokePrivate(final String methodName, final Class<?>[] paramTypes, final Object... args) {
        try {
            Method method = JobService.class.getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            return method.invoke(JOB_SERVICE, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static String escapeHtml(final String input) {
        return (String) invokePrivate("escapeHtml", new Class<?>[]{String.class}, input);
    }

    private static String nl2brEscaped() {
        return (String) invokePrivate("nl2brEscaped", new Class<?>[]{String.class}, "line1\nline2\r\nline3<b>");
    }

    private static String urlEncode() {
        return (String) invokePrivate("urlEncode", new Class<?>[]{String.class}, "Bestellung test.srv.muenchen.de");
    }

    private static boolean handleDBParams(final Map<String, Map<?, ?>> dbParams, final Map<String, Object> params) {
        return (boolean) invokePrivate("handleDBParams",
                new Class<?>[]{Map.class, Map.class}, dbParams, params);
    }

    private static String formatNonPostgresJustification(final String requestedByName,
                                                         final String reason) {
        return (String) invokePrivate("formatNonPostgresJustification",
                new Class<?>[]{String.class, String.class, String.class, Long.class, String.class, String.class,
                        String.class, String.class, String.class, String.class},
                "MySQL", "AWX-TEST", null, null, "test.srv.muenchen.de", requestedByName,
                "tester", "ITM", "tester@muenchen.de", reason);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Tests for the methods escapeHtml, nl2brEscaped, and urlEncode
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void escapeHtml_escapesAllFiveDangerousCharacters() {
        assertEquals("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; &quot;q&quot;",
                escapeHtml("<script>alert('x')</script> & \"q\""));
    }

    @Test
    public void escapeHtml_nullInput_returnsEmptyString() {
        assertEquals("", escapeHtml(null));
    }

    @Test
    public void nl2brEscaped_convertsNewlinesAfterEscaping() {
        String result = nl2brEscaped();
        assertEquals("line1<br/>line2<br/>line3&lt;b&gt;", result);
    }

    @Test
    public void urlEncode_encodesSpacesAsPercent20NotPlus() {
        // This value goes into a mailto link and a query string. Some mail clients read
        // the character '+' as a literal plus sign, not as a space.
        assertEquals("Bestellung%20test.srv.muenchen.de", urlEncode());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Tests for formatNonPostgresJustification. This method makes the HTML email body.
    // The system sends this email to admins for orders of non-Postgres databases.
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void formatNonPostgresJustification_scriptInjectionInReason_isEscapedNotExecutable() {
        String html = formatNonPostgresJustification(
                "Tester",
                "<script>alert('xss')</script>");

        assertFalse(html.contains("<script>"), "raw <script> tag must not appear in the generated email HTML");
        assertTrue(html.contains("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;"));
    }

    @Test
    public void formatNonPostgresJustification_scriptInjectionInName_isEscaped() {
        String html = formatNonPostgresJustification(
                "<img src=x onerror=alert(1)>",
                "A perfectly normal, sufficiently long justification for not using PostgreSQL here.");

        assertFalse(html.contains("<img src=x"), "raw markup in requestedByName must not appear unescaped");
        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"));
    }

    @Test
    public void formatNonPostgresJustification_multilineReason_preservesLineBreaksAsBr() {
        String html = formatNonPostgresJustification(
                "Tester",
                "First line of the justification.\nSecond line of the justification.");

        assertTrue(html.contains("First line of the justification.<br/>Second line of the justification."));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Tests for handleDBParams. This method checks the parameters for database provisioning.
    // -----------------------------------------------------------------------------------------------------------------

    private Map<String, Map<?, ?>> validPostgresDbParams() {
        Map<String, Object> mariaPostgresMysqlOracle = new HashMap<>();
        mariaPostgresMysqlOracle.put("db_type", "postgresql");
        mariaPostgresMysqlOracle.put("db_version", "17");
        mariaPostgresMysqlOracle.put("customer_db_name", "mydb01");
        mariaPostgresMysqlOracle.put("customer_db_user", "myuser01");
        mariaPostgresMysqlOracle.put("customer_db_schema", "myschema");
        Map<String, Map<?, ?>> dbParams = new HashMap<>();
        dbParams.put("mariaPostgresMysqlOracle", mariaPostgresMysqlOracle);
        return dbParams;
    }

    @Test
    public void handleDBParams_validPostgres_populatesParamsAndReturnsFalse() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("tester", null, Collections.emptyList()));
        Map<String, Object> params = new HashMap<>();

        boolean nonOss = handleDBParams(validPostgresDbParams(), params);

        assertFalse(nonOss, "postgresql is OSS, should not be flagged non-OSS");
        assertEquals("postgresql", params.get("db_type"));
        assertEquals("mydb01", params.get("customer_db_name"));
        assertEquals("tester@muenchen.de", params.get("customer_email"));
    }

    @Test
    public void handleDBParams_oracle_returnsTrueForNonOss() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("tester", null, Collections.emptyList()));
        Map<String, Object> mariaPostgresMysqlOracle = new HashMap<>();
        mariaPostgresMysqlOracle.put("db_type", "oracle");
        mariaPostgresMysqlOracle.put("db_version", "19c");
        mariaPostgresMysqlOracle.put("customer_db_name", "mydb01");
        mariaPostgresMysqlOracle.put("customer_db_user", "myuser01");
        mariaPostgresMysqlOracle.put("customer_db_charset", "AL32UTF8");
        mariaPostgresMysqlOracle.put("oracle_datasize", "100");
        Map<String, Map<?, ?>> dbParams = new HashMap<>();
        dbParams.put("mariaPostgresMysqlOracle", mariaPostgresMysqlOracle);

        boolean nonOss = handleDBParams(dbParams, new HashMap<>());

        assertTrue(nonOss, "oracle is proprietary, must be flagged non-OSS");
    }

    @Test
    public void handleDBParams_missingMariaPostgresMysqlOracleKey_throws() {
        assertThrows(IllegalArgumentException.class, () -> handleDBParams(new HashMap<>(), new HashMap<>()));
    }

    @Test
    public void handleDBParams_invalidDbType_throws() {
        Map<String, Object> inner = new HashMap<>();
        inner.put("db_type", "mongodb'; DROP TABLE users;--");
        Map<String, Map<?, ?>> dbParams = new HashMap<>();
        dbParams.put("mariaPostgresMysqlOracle", inner);

        assertThrows(IllegalArgumentException.class, () -> handleDBParams(dbParams, new HashMap<>()));
    }

    @Test
    public void handleDBParams_unsupportedDbVersion_throws() {
        Map<String, Object> inner = new HashMap<>();
        inner.put("db_type", "postgresql");
        inner.put("db_version", "9.6"); // The accepted list has only versions 16, 17, and 18.
        Map<String, Map<?, ?>> dbParams = new HashMap<>();
        dbParams.put("mariaPostgresMysqlOracle", inner);

        assertThrows(IllegalArgumentException.class, () -> handleDBParams(dbParams, new HashMap<>()));
    }

    @Test
    public void handleDBParams_dbNameWithShellMetacharacters_isRejected() {
        // The values customer_db_name and customer_db_user go to the AWX extra vars.
        // Provisioning scripts read these vars. The regex allow-list is the only barrier
        // against the injection of shell characters or SQL characters here.
        Map<String, Object> inner = new HashMap<>();
        inner.put("db_type", "postgresql");
        inner.put("db_version", "17");
        inner.put("customer_db_name", "mydb; rm -rf /");
        inner.put("customer_db_user", "myuser01");
        Map<String, Map<?, ?>> dbParams = new HashMap<>();
        dbParams.put("mariaPostgresMysqlOracle", inner);

        assertThrows(IllegalArgumentException.class, () -> handleDBParams(dbParams, new HashMap<>()));
    }

    @Test
    public void handleDBParams_oracleDatasizeOutOfBounds_isRejected() {
        Map<String, Object> inner = new HashMap<>();
        inner.put("db_type", "oracle");
        inner.put("db_version", "19c");
        inner.put("customer_db_name", "mydb01");
        inner.put("customer_db_user", "myuser01");
        inner.put("customer_db_charset", "AL32UTF8");
        inner.put("oracle_datasize", "5000"); // The maximum value is 500 GB.
        Map<String, Map<?, ?>> dbParams = new HashMap<>();
        dbParams.put("mariaPostgresMysqlOracle", inner);

        assertThrows(IllegalArgumentException.class, () -> handleDBParams(dbParams, new HashMap<>()));
    }

    @Test
    public void handleDBParams_mysqlUnsupportedCharset_isRejected() {
        Map<String, Object> inner = new HashMap<>();
        inner.put("db_type", "mysql");
        inner.put("db_version", "8.4");
        inner.put("customer_db_name", "mydb01");
        inner.put("customer_db_user", "myuser01");
        inner.put("customer_db_charset", "cp1251"); // The accepted list does not have this charset.
        Map<String, Map<?, ?>> dbParams = new HashMap<>();
        dbParams.put("mariaPostgresMysqlOracle", inner);

        assertThrows(IllegalArgumentException.class, () -> handleDBParams(dbParams, new HashMap<>()));
    }

    @Test
    public void handleDBParams_postgresCustomerAppServerDefaultsToEmptyList() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("tester", null, Collections.emptyList()));
        Map<String, Object> params = new HashMap<>();

        handleDBParams(validPostgresDbParams(), params);

        assertEquals(new ArrayList<>(), params.get("customer_app_server"));
        assertEquals(new ArrayList<>(), params.get("postgis"));
    }

    @Test
    public void handleDBParams_postgresCustomerAppServerPreservedWhenProvided() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("tester", null, Collections.emptyList()));
        Map<String, Object> mariaPostgresMysqlOracle = new HashMap<>();
        mariaPostgresMysqlOracle.put("db_type", "postgresql");
        mariaPostgresMysqlOracle.put("db_version", "17");
        mariaPostgresMysqlOracle.put("customer_db_name", "mydb01");
        mariaPostgresMysqlOracle.put("customer_db_user", "myuser01");
        mariaPostgresMysqlOracle.put("customer_db_schema", "myschema");
        mariaPostgresMysqlOracle.put("customer_app_server", List.of("appserver01"));
        Map<String, Map<?, ?>> dbParams = new HashMap<>();
        dbParams.put("mariaPostgresMysqlOracle", mariaPostgresMysqlOracle);
        Map<String, Object> params = new HashMap<>();

        handleDBParams(dbParams, params);

        assertEquals(List.of("appserver01"), params.get("customer_app_server"));
    }
}
