package de.muenchen.mcmp.clients.snow;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SnowServiceTest {

    private static Stream<Arguments> provideVsSuffixTestData() {
        return Stream.of(
                Arguments.of("/sproxy/sproxy/sproxy.example.org_tcp_80_vs-1-", "/sproxy/sproxy/sproxy.example.org_tcp_80_vs"),
                Arguments.of("/test/name_VS_suffix", "/test/name_VS"),
                Arguments.of("/prefix/test_vs_content_vs-suffix-123", "/prefix/test_vs_content_vs"),
                Arguments.of("/sproxy/no-vs-here/name", "/sproxy/no-vs-here/name"),
                Arguments.of("", ""),
                Arguments.of(null, null),
                Arguments.of("_vs", "_vs"),
                Arguments.of("test_vs", "test_vs"),
                Arguments.of("/kita/kitafinder_k/test35.example.org_https_vs-1-", "/kita/kitafinder_k/test35.example.org_https_vs"),
                Arguments.of("/kita/kitafinder_k/test35.example.org_https_vs-2-", "/kita/kitafinder_k/test35.example.org_https_vs"),
                Arguments.of("/kita/kitafinder_k/test35.example.org_https_vs-Redirect-", "/kita/kitafinder_k/test35.example.org_https_vs"),
                Arguments.of("/kita/kitafinder_k/test35.example.org_https_vs-Redirect--1-","/kita/kitafinder_k/test35.example.org_https_vs"),
                Arguments.of("/kita/kitafinder_k/test35.example.org_https_vs","/kita/kitafinder_k/test35.example.org_https_vs")
        );
    }

    @ParameterizedTest(name = "{index} => input=''{0}'', expected=''{1}''")
    @MethodSource("provideVsSuffixTestData")
    public void testStripVsSuffix(String input, String expected) {
        assertEquals(expected, SnowService.stripVsSuffix(input));
    }
}