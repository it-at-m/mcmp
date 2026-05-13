package de.muenchen.mcmp.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class JobServiceTest {

    @Test
    public void testMergeJsonStrings() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        JobService jobService = new JobService(
                null, null, null, null, null, null, null, null, null, null, null, null
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
                null, null, null, null, null, null, null, null, null, null, null, null
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
}