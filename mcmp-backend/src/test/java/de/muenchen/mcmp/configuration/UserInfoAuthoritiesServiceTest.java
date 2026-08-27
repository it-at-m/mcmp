package de.muenchen.mcmp.configuration;

import de.muenchen.mcmp.config.app.AppConfigCacheService;
import de.muenchen.mcmp.logging.SiemLoggingService;
import de.muenchen.mcmp.servicenow.ServiceNowService;
import de.muenchen.mcmp.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.restclient.RestTemplateBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserInfoAuthoritiesServiceTest {

    private UserInfoAuthoritiesService service;

    @BeforeEach
    void setUp() {
        final RestTemplateBuilder restTemplateBuilder = mock(RestTemplateBuilder.class);
        when(restTemplateBuilder.build()).thenReturn(new org.springframework.web.client.RestTemplate());

        final RoleConfiguration roleConfiguration = mock(RoleConfiguration.class);
        final UserService userService = mock(UserService.class);
        final SiemLoggingService siemLoggingService = mock(SiemLoggingService.class);
        final DepartmentFilterConfiguration departmentFilterConfiguration = mock(DepartmentFilterConfiguration.class);
        final ApiUserConfiguration apiUserConfiguration = mock(ApiUserConfiguration.class);
        final ServiceNowService serviceNowService = mock(ServiceNowService.class);
        final AppConfigCacheService appConfigCacheService = mock(AppConfigCacheService.class);

        service = new UserInfoAuthoritiesService(
                "http://dummy-userinfo",
                restTemplateBuilder,
                roleConfiguration,
                userService,
                siemLoggingService,
                departmentFilterConfiguration,
                apiUserConfiguration,
                serviceNowService,
                appConfigCacheService
        );
    }

    @Test
    void extractIpFromAuthorizationHeader_shouldReturnIp_whenForContainsQuotedIpWithPort() {
        final String header = "proto=https;host=mcmp.example.org;for=\"10.166.21.102:49288\"";
        final String ip = service.extractIpFromAuthorizationHeader(header);
        assertThat(ip).isEqualTo("10.166.21.102");
    }

    @Test
    void extractIpFromAuthorizationHeader_shouldReturnIPv6_whenForContainsQuotedIpWithPort() {
        final String header = "proto=http;host=\"localhost:8080\";for=\"[0:0:0:0:0:0:0:1]:59542\"";
        final String ip = service.extractIpFromAuthorizationHeader(header);
        assertThat(ip).isEqualTo("0:0:0:0:0:0:0:1");
    }

    @Test
    void extractIpFromAuthorizationHeader_shouldReturnIp_whenForContainsIpWithoutQuotes() {
        final String header = "proto=https;host=mcmp.example.org;for=10.1.2.3:49288;something=else";
        final String ip = service.extractIpFromAuthorizationHeader(header);
        assertThat(ip).isEqualTo("10.1.2.3");
    }

    @Test
    void extractIpFromAuthorizationHeader_shouldReturnNull_whenForIsMissing() {
        final String header = "proto=https;host=mcmp.example.org";
        final String ip = service.extractIpFromAuthorizationHeader(header);
        assertThat(ip).isNull();
    }

    @Test
    void extractIpFromAuthorizationHeader_shouldReturnNull_whenExtractedValueIsNotValidIp() {
        final String header = "proto=https;host=mcmp.example.org;for=\"not-an-ip:1234\"";
        final String ip = service.extractIpFromAuthorizationHeader(header);
        assertThat(ip).isNull();
    }

    @Test
    void extractIpFromAuthorizationHeader_shouldReturnNull_whenHeaderIsNull() {
        final String ip = service.extractIpFromAuthorizationHeader(null);
        assertThat(ip).isNull();
    }
}