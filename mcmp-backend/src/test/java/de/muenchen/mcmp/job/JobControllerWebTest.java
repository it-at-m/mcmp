package de.muenchen.mcmp.job;

import de.muenchen.mcmp.appservice.Appservice;
import de.muenchen.mcmp.appservice.AppserviceDTO;
import de.muenchen.mcmp.appservice.AppserviceService;
import de.muenchen.mcmp.config.app.MaintenanceModeConfiguration;
import de.muenchen.mcmp.config.app.MaintenanceModeInterceptor;
import de.muenchen.mcmp.configuration.nfcconverter.NfcRequestFilter;
import de.muenchen.mcmp.errorlog.ErrorLogService;
import de.muenchen.mcmp.loadbalancer.LoadbalancerService;
import de.muenchen.mcmp.loadbalancer.UnifiedLoadbalancer;
import de.muenchen.mcmp.loadbalancer.UnifiedLoadbalancerMemberDTO;
import de.muenchen.mcmp.loadbalancer.UnifiedLoadbalancerPoolDTO;
import de.muenchen.mcmp.mountPoint.MountPointDTO;
import de.muenchen.mcmp.mountPoint.MountPointService;
import de.muenchen.mcmp.network.NetworkService;
import de.muenchen.mcmp.security.RequestBodyCachingFilter;
import de.muenchen.mcmp.security.RequestResponseLoggingFilter;
import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.server.ServerFullDTO;
import de.muenchen.mcmp.server.ServerService;
import de.muenchen.mcmp.snapshot.SnapshotService;
import de.muenchen.mcmp.storage.UnifiedStorageService;
import de.muenchen.mcmp.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * This class tests the {@code /job/create/*} endpoints of {@link JobController}.
 * The tests work at the HTTP level. The tests check for input-validation errors,
 * for example missing null checks or missing type checks. The tests also check
 * for authorization errors. Fuzzing found these errors. Each test name refers to
 * the error that the test prevents.
 */
@WebMvcTest(controllers = JobController.class, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
        classes = {MaintenanceModeConfiguration.class, MaintenanceModeInterceptor.class, RequestResponseLoggingFilter.class,
                RequestBodyCachingFilter.class, NfcRequestFilter.class}))
@Import(JobControllerWebTest.TestSecurityConfig.class)
@WithMockUser
class JobControllerWebTest {

    private static final long SERVER_ID = 1001L; // This ID is an example. All services are mock objects. No test uses a real database.
    private static final long APPSERVICE_ID = 2002L;
    private static final long NETWORK_GROUP_ID = 3003L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobService jobService;
    @MockitoBean
    private ServerService serverService;
    @MockitoBean
    private AppserviceService appserviceService;
    @MockitoBean
    private NetworkService networkService;
    @MockitoBean
    private MountPointService mountPointService;
    @MockitoBean
    private SnapshotService snapshotService;
    @MockitoBean
    private UnifiedStorageService unifiedStorageService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private LoadbalancerService loadbalancerService;
    @MockitoBean
    private ErrorLogService errorLogService;

    @TestConfiguration
    @EnableMethodSecurity(securedEnabled = true)
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(final HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(requests -> requests.anyRequest().authenticated());
            return http.build();
        }
    }

    private static final String VALID_RHEL10_FQDN = """
            {"prefix":"","serverType":"lx","application":"test","domain":"srv.muenchen.de","customNumber":1}""";

    private void stubRhel10AppserviceAndNetworkChecksPass() {
        when(appserviceService.getVisibleAppservice(APPSERVICE_ID)).thenReturn(
                new AppserviceDTO(APPSERVICE_ID, "AWX-TEST", "SNSVC0001", null, "Standard", null,
                        null, null, null, null, null, null, null, false, List.of()));
        when(appserviceService.canUserEditAppservice(APPSERVICE_ID)).thenReturn(true);
        when(networkService.isAllowedNetworkGroupForAppservice(eq(NETWORK_GROUP_ID), eq(APPSERVICE_ID), anyBoolean())).thenReturn(true);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Tests for validation and null-safety, not for security
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void rhel10Server_validPayload_succeeds() throws Exception {
        stubRhel10AppserviceAndNetworkChecksPass();

        mockMvc.perform(post("/job/create/LINUX_RHEL10_SERVER").param("serverId", "-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fqdn":%s,"categoryType":"Standard","serverType":null,"ram":2,"cpu":1,
                                 "network_group_id":3003,"application_service_id":2002,"db_params":null,
                                 "non_postgres_reason":"","middleware_user":false}""".formatted(VALID_RHEL10_FQDN)))
                .andExpect(status().isOk());

        verify(jobService).linuxRhelServer(any(), eq("Standard"), any(), eq(2), eq(1), eq(NETWORK_GROUP_ID),
                eq(APPSERVICE_ID), any(), any(), eq(false), eq("LINUX_RHEL10_SERVER"));
    }

    @Test
    void rhel10Server_middlewareUserNonBoolean_isRejected() throws Exception {
        stubRhel10AppserviceAndNetworkChecksPass();

        mockMvc.perform(post("/job/create/LINUX_RHEL10_SERVER").param("serverId", "-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fqdn":%s,"categoryType":"Standard","serverType":null,"ram":2,"cpu":1,
                                 "network_group_id":3003,"application_service_id":2002,"db_params":null,
                                 "non_postgres_reason":"","middleware_user":"yes"}""".formatted(VALID_RHEL10_FQDN)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(jobService);
    }

    @Test
    void rhel10Server_customNumberWrongType_isRejected() throws Exception {
        mockMvc.perform(post("/job/create/LINUX_RHEL10_SERVER").param("serverId", "-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fqdn":{"prefix":"","application":"test","domain":"srv.muenchen.de","customNumber":"1"},
                                 "categoryType":"Standard","serverType":null,"ram":2,"cpu":1,
                                 "network_group_id":3003,"application_service_id":2002,"middleware_user":false}"""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(jobService);
    }

    @Test
    void rhel10Server_nonStandardCategoryWithNullServerType_isRejected() throws Exception {
        stubRhel10AppserviceAndNetworkChecksPass();

        mockMvc.perform(post("/job/create/LINUX_RHEL10_SERVER").param("serverId", "-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fqdn":%s,"categoryType":"BogusCategory","serverType":null,"ram":2,"cpu":1,
                                 "network_group_id":3003,"application_service_id":2002,"middleware_user":false}""".formatted(VALID_RHEL10_FQDN)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(jobService);
    }

    @Test
    void startServer_malformedScheduleTime_isRejected() throws Exception {
        when(serverService.canUserEditServer(SERVER_ID)).thenReturn(true);

        mockMvc.perform(post("/job/create/START_SERVER").param("serverId", String.valueOf(SERVER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduleTime\":\"not-a-date\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(jobService);
    }

    @Test
    void changeCpuRam_missingSchedulePatchnight_doesNotThrow() throws Exception {
        when(serverService.canUserEditServer(SERVER_ID)).thenReturn(true);
        when(serverService.getServerById(SERVER_ID)).thenReturn(ServerFullDTO.builder().build());

        mockMvc.perform(post("/job/create/CHANGE_CPU_RAM").param("serverId", String.valueOf(SERVER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpu\":2,\"ram\":4}"))
                .andExpect(status().isOk());

        verify(jobService).changeCpuRam(eq(SERVER_ID), eq("CHANGE_CPU_RAM"), eq(2), eq(4), isNull(), eq(false));
    }

    @Test
    void windowsPartitionChange_nonexistentPartition_isRejected() throws Exception {
        when(serverService.canUserEditServer(SERVER_ID)).thenReturn(true);
        when(mountPointService.getMountPointByServerIdAndPath(SERVER_ID, "Z:")).thenReturn(null);

        mockMvc.perform(post("/job/create/WINDOWS_PARTITION_CHANGE").param("serverId", String.valueOf(SERVER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partition\":\"Z:\",\"newSize\":10}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(jobService);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Tests for security
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void mountpointChange_reservedPathViaVolumeGroup_isBlockedByEditableFlag() throws Exception {
        when(serverService.canUserEditServer(SERVER_ID)).thenReturn(true);
        when(snapshotService.getSnapshotsByServerId(SERVER_ID)).thenReturn(Collections.emptyList());
        when(mountPointService.getMountPointByServerIdAndPath(SERVER_ID, "/boot"))
                .thenReturn(MountPointDTO.builder().diskPath("/boot").editable(false).capacityInBytes(1024L * 1024 * 1024).build());

        mockMvc.perform(post("/job/create/LINUX_MOUNTPOINT_CHANGE").param("serverId", String.valueOf(SERVER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mountPoint\":\"/boot\",\"newSize\":2000,\"volumeGroup\":\"vg00\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(jobService);
    }

    @Test
    void mountpointChange_newMountpointViaVolumeGroup_stillWorks() throws Exception {
        when(serverService.canUserEditServer(SERVER_ID)).thenReturn(true);
        when(snapshotService.getSnapshotsByServerId(SERVER_ID)).thenReturn(Collections.emptyList());
        when(mountPointService.getMountPointByServerIdAndPath(SERVER_ID, "/data/newvol")).thenReturn(null);

        mockMvc.perform(post("/job/create/LINUX_MOUNTPOINT_CHANGE").param("serverId", String.valueOf(SERVER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mountPoint\":\"/data/newvol\",\"newSize\":50,\"volumeGroup\":\"vg00\"}"))
                .andExpect(status().isOk());

        verify(jobService).linuxMountpointChange(eq(SERVER_ID), eq("LINUX_MOUNTPOINT_CHANGE"), eq("/data/newvol"), eq(50), eq("newvol"), eq("vg00"));
    }

    @Test
    void windowsPartitionChange_nonEditablePartition_isBlocked() throws Exception {
        when(serverService.canUserEditServer(SERVER_ID)).thenReturn(true);
        when(mountPointService.getMountPointByServerIdAndPath(SERVER_ID, "C:"))
                .thenReturn(MountPointDTO.builder().diskPath("C:").editable(false).capacityInBytes(1024L * 1024 * 1024).build());

        mockMvc.perform(post("/job/create/WINDOWS_PARTITION_CHANGE").param("serverId", String.valueOf(SERVER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partition\":\"C:\",\"newSize\":100}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(jobService);
    }

    @Test
    void loadbalancerPoolMembers_addedMemberRequiresEditNotJustView_isBlocked() throws Exception {
        when(loadbalancerService.canUserEditLoadbalancer(anyLong())).thenReturn(true);
        when(loadbalancerService.getLoadbalancerById(anyLong())).thenReturn(
                UnifiedLoadbalancer.builder()
                        .wafEnabled(false)
                        .pools(List.of(UnifiedLoadbalancerPoolDTO.builder()
                                .name("pool1")
                                .members(List.of())
                                .build()))
                        .build());
        when(serverService.canUserEditServer(SERVER_ID)).thenReturn(false);
        when(serverService.canUserViewServer(SERVER_ID)).thenReturn(true); // The user can only view the server.

        mockMvc.perform(post("/job/create/LOADBALANCER_F5_CHANGE_POOL_MEMBERS").param("serverId", "-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lb_virtual_server_id":123,"pool_name":"pool1",
                                 "added":[{"server_id":1001,"port":443}]}"""))
                .andExpect(status().isForbidden());

        verify(serverService).canUserEditServer(SERVER_ID);
        verifyNoInteractions(jobService);
    }

    @Test
    void loadbalancerPoolMembers_wafEnabled_isBlocked() throws Exception {
        when(loadbalancerService.canUserEditLoadbalancer(anyLong())).thenReturn(true);
        when(loadbalancerService.getLoadbalancerById(anyLong())).thenReturn(
                UnifiedLoadbalancer.builder()
                        .wafEnabled(true)
                        .pools(List.of(UnifiedLoadbalancerPoolDTO.builder()
                                .name("pool1")
                                .members(List.of())
                                .build()))
                        .build());

        mockMvc.perform(post("/job/create/LOADBALANCER_F5_CHANGE_POOL_MEMBERS").param("serverId", "-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lb_virtual_server_id":123,"pool_name":"pool1",
                                 "removed":[{"ip":"10.0.0.1","port":443}]}"""))
                .andExpect(status().isForbidden());

        verifyNoInteractions(jobService);
    }

    @Test
    void loadbalancerPoolMembers_capPool_isBlocked() throws Exception {
        when(loadbalancerService.canUserEditLoadbalancer(anyLong())).thenReturn(true);
        when(loadbalancerService.getLoadbalancerById(anyLong())).thenReturn(
                UnifiedLoadbalancer.builder()
                        .wafEnabled(false)
                        .pools(List.of(UnifiedLoadbalancerPoolDTO.builder()
                                .name("pool1")
                                .members(List.of(UnifiedLoadbalancerMemberDTO.builder()
                                        .ip("10.0.0.1")
                                        .port(32201)
                                        .serverId(null)
                                        .build()))
                                .build()))
                        .build());

        mockMvc.perform(post("/job/create/LOADBALANCER_F5_CHANGE_POOL_MEMBERS").param("serverId", "-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lb_virtual_server_id":123,"pool_name":"pool1",
                                 "removed":[{"ip":"10.0.0.1","port":32201}]}"""))
                .andExpect(status().isForbidden());

        verifyNoInteractions(jobService);
    }

    @Test
    void loadbalancerF5_create_appserviceOwnershipIsVisibilityBased_byDesign() throws Exception {
        Appservice appservice = new Appservice();
        appservice.setId(APPSERVICE_ID);
        when(appserviceService.getAppservice(APPSERVICE_ID)).thenReturn(appservice);
        when(serverService.findServersByIpAddress("10.165.27.42")).thenReturn(List.of(server()));
        when(serverService.canUserEditServer(SERVER_ID)).thenReturn(true);

        mockMvc.perform(post("/job/create/LOADBALANCER_F5").param("serverId", String.valueOf(SERVER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"appservice":{"id":2002},"dns":"lb-test.srv.muenchen.de",
                                 "listener":[{"port":443,"listener_type":"tcp","serverside_tls":true}],
                                 "server_pools":[{"loadbalancing_mode":"round-robin",
                                   "monitors":[{"type":"https","path":"/health","method":"GET"}],
                                   "member":[{"ip":"10.165.27.42","name":"member1","ports":[443]}]}]}"""))
                .andExpect(status().isOk());

        verify(jobService).loadbalancerF5(any(), eq("LOADBALANCER_F5"));
        verify(appserviceService, never()).canUserEditAppservice(APPSERVICE_ID);
    }

    private static Server server() {
        final Server server = new Server();
        server.setId(JobControllerWebTest.SERVER_ID);
        return server;
    }
}
