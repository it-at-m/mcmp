package de.muenchen.mcmp.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.muenchen.mcmp.action.Action;
import de.muenchen.mcmp.action.ActionRepository;
import de.muenchen.mcmp.appservice.Appservice;
import de.muenchen.mcmp.appservice.AppserviceRepository;
import de.muenchen.mcmp.awxConfig.AwxConfig;
import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.user.User;
import de.muenchen.mcmp.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JobServiceTest {

    @Test
    public void testMergeJsonStrings() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        JobService jobService = new JobService(
                null, null, null, null, null, null, null, null, null, null, null, null, null
        );

        // Erstes JSON-Objekt (awxExtraVars)
        Map<String, Object> dbAwxExtraVars = new HashMap<>();
        dbAwxExtraVars.put("foo", "bar");
        dbAwxExtraVars.put("number", 42);
        dbAwxExtraVars.put("items", Arrays.asList("a", "b", "c"));
        dbAwxExtraVars.put("itemSingular", Collections.singletonList("d"));
        String dbAwxExtraVarsString = jobService.serializeParams(dbAwxExtraVars);

        // Zweites JSON-Objekt (inputExtraVars)
        Map<String, Object> serviceGeneratedAwxExtraVars = new HashMap<>();
        serviceGeneratedAwxExtraVars.put("baz", "qux");
        serviceGeneratedAwxExtraVars.put("number", 99); // Überschreibt das Feld "number"
        serviceGeneratedAwxExtraVars.put("extraItems", Arrays.asList(1, 2, 3));
        String serviceGeneratedAwxExtraVarsString = jobService.serializeParams(serviceGeneratedAwxExtraVars);

        // Merge aufrufen
        String merged = jobService.mergeJsonStrings(dbAwxExtraVarsString, serviceGeneratedAwxExtraVarsString);

        // Erwartetes Ergebnis
        Map<String, Object> expected = new HashMap<>();
        expected.put("foo", "bar");
        expected.put("number", 99); // Wert aus inputExtraVars überschreibt awxExtraVars
        expected.put("baz", "qux");
        expected.put("items", Arrays.asList("a", "b", "c"));
        expected.put("extraItems", Arrays.asList(1, 2, 3));
        expected.put("itemSingular", Collections.singletonList("d"));

        String expectedJson = jobService.serializeParams(expected);

        // Vergleiche die JSON-Bäume
        assertEquals(objectMapper.readTree(expectedJson), objectMapper.readTree(merged));
    }

    @Test
    public void testChechmkDowntimeEndtimeDateConversion() {
        JobService jobService = new JobService(
                null, null, null, null, null, null, null, null, null, null, null, null, null
        );

        // Testfall 1: Gültiges Datum
        String input1 = "15.09.2025 14:30:00";
        String expected1 = "15.09.2025 16:35:00";
        String result1 = jobService.getEndDateFromStartDateAndDurationInMinutes(input1, 125);
        assertEquals(expected1, result1);

        // Testfall 2: Jahrwechsel
        String input2 = "31.12.2024 23:59:00";
        String expected2 = "01.01.2025 00:59:00";
        String result2 = jobService.getEndDateFromStartDateAndDurationInMinutes(input2, 60);
        assertEquals(expected2, result2);

        // Testfall 3: Ungültiges Datum
        String input3 = "invalid-date";
        int duration3 = 30;
        assertThrows(RuntimeException.class, () -> jobService.getEndDateFromStartDateAndDurationInMinutes(input3, duration3));

        // Testfall 4: Negative Dauer
        String input4 = "15.09.2025 14:30:00";
        int duration4 = -30;
        assertNull(jobService.getEndDateFromStartDateAndDurationInMinutes(input4, duration4));

        // Testfall 5: Null-Eingabe
        assertNull(jobService.getEndDateFromStartDateAndDurationInMinutes(null, 1));
    }

    @AfterEach
    public void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Regression test: loadbalancerF5 used to forward the entire raw client-supplied
     * awxExtraVars map (minus "appservice") verbatim into the AWX job's extra vars,
     * so any unvalidated extra key the client sent reached the AWX playbook unfiltered.
     * The params map is now built from an explicit allow-list of known/validated fields.
     */
    @Test
    public void testLoadbalancerF5_stripsUnvalidatedFields() {
        JobRepository jobRepository = mock(JobRepository.class);
        ActionRepository actionRepository = mock(ActionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        AppserviceRepository appserviceRepository = mock(AppserviceRepository.class);
        ActionToJobMapper actionToJobMapper = mock(ActionToJobMapper.class);

        JobService jobService = new JobService(
                jobRepository, actionRepository, userRepository, null, appserviceRepository,
                null, null, null, null, null, null, null, actionToJobMapper
        );

        Appservice appservice = new Appservice();
        appservice.setId(2002L);
        appservice.setName("AWX-TEST");
        appservice.setNumber("SNSVC0001");
        appservice.setUsedFor("Test");
        appservice.setCswEnforced(false);
        when(appserviceRepository.findById(2002L)).thenReturn(Optional.of(appservice));

        Action action = new Action();
        action.setIdentifier("LOADBALANCER_F5");
        action.setEnabled(true);
        AwxConfig awxConfig = new AwxConfig();
        awxConfig.setEnabled(true);
        action.setAwxConfig(awxConfig);
        when(actionRepository.findByIdentifier("LOADBALANCER_F5")).thenReturn(action);

        User user = new User();
        user.setUsername("tester");
        when(userRepository.findByUsername("tester")).thenReturn(user);

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("tester", null, Collections.emptyList()));

        Map<String, Object> appserviceMap = new HashMap<>();
        appserviceMap.put("id", 2002L);

        Map<String, Object> listener = new HashMap<>();
        listener.put("port", 443);

        Map<String, Object> member = new HashMap<>();
        member.put("ip", "10.0.0.1");
        member.put("port", 443);

        Map<String, Object> serverPool = new HashMap<>();
        serverPool.put("member", new ArrayList<>(List.of(member)));

        Map<String, Object> awxExtraVars = new HashMap<>();
        awxExtraVars.put("appservice", appserviceMap);
        awxExtraVars.put("dns", "lb.srv.muenchen.de");
        awxExtraVars.put("listener", new ArrayList<>(List.of(listener)));
        awxExtraVars.put("server_pools", new ArrayList<>(List.of(serverPool)));
        awxExtraVars.put("malicious_override", "should_be_stripped");

        jobService.loadbalancerF5(awxExtraVars, "LOADBALANCER_F5");

        ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(jobCaptor.capture());
        String persistedExtraVars = jobCaptor.getValue().getAwxExtraVars();

        assertFalse(persistedExtraVars.contains("malicious_override"),
                "unvalidated extra field must not reach the persisted AWX extra vars");
        assertTrue(persistedExtraVars.contains("\"dns\""));
        assertTrue(persistedExtraVars.contains("\"listener\""));
        assertTrue(persistedExtraVars.contains("\"server_pools\""));
        assertTrue(persistedExtraVars.contains("\"requester_username\""));
        assertFalse(persistedExtraVars.contains("\"appservice\" :"), "raw appservice object must not leak into extra vars");
    }

    /**
     * Regression test for the 7-arg {@code createJob(..., scheduleTime, awxJobTags,
     * awxSkipTags)} overload used to silently drop {@code scheduleTime} when delegating to the
     * 8-arg overload (passed a hardcoded {@code null} instead of forwarding it), so scheduled
     * actions that go through this overload (e.g. Linux/Windows server deletion) ran immediately
     * instead of at the planned time. Exercises the exact overload that broke, and asserts the
     * schedule actually reaches the persisted {@link Job}.
     */
    @Test
    public void testCreateJob_scheduleTimeOverload_isForwardedToPersistedJob() {
        JobRepository jobRepository = mock(JobRepository.class);
        ActionRepository actionRepository = mock(ActionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ActionToJobMapper actionToJobMapper = mock(ActionToJobMapper.class);

        JobService jobService = new JobService(
                jobRepository, actionRepository, userRepository, null, null,
                null, null, null, null, null, null, null, actionToJobMapper
        );

        Server server = new Server();
        server.setId(1001L);
        server.setFqdn("test.srv.muenchen.de");

        Action action = new Action();
        action.setIdentifier("LINUX_DELETE_SERVER");
        action.setEnabled(true);
        action.setChangeRequired(false);
        AwxConfig awxConfig = new AwxConfig();
        awxConfig.setEnabled(true);
        action.setAwxConfig(awxConfig);
        when(actionRepository.findByIdentifier("LINUX_DELETE_SERVER")).thenReturn(action);

        User user = new User();
        user.setUsername("tester");
        when(userRepository.findByUsername("tester")).thenReturn(user);

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("tester", null, Collections.emptyList()));

        Instant scheduleTime = Instant.parse("2026-09-01T10:00:00Z");

        jobService.createJob("LINUX_DELETE_SERVER", server, new HashMap<>(), new HashMap<>(), scheduleTime, null, null);

        ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(jobCaptor.capture());
        Job savedJob = jobCaptor.getValue();

        assertEquals(scheduleTime, savedJob.getChangeStartDate(),
                "scheduled execution time must be forwarded to the persisted job");
        assertEquals(scheduleTime.plusSeconds(1), savedJob.getChangeEndDate());
    }
}