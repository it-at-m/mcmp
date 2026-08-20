package de.muenchen.mcmp.job;

import de.muenchen.mcmp.appservice.AppserviceService;
import de.muenchen.mcmp.config.app.MaintenanceModeConfiguration;
import de.muenchen.mcmp.config.app.MaintenanceModeInterceptor;
import de.muenchen.mcmp.configuration.nfcconverter.NfcRequestFilter;
import de.muenchen.mcmp.errorlog.ErrorLogService;
import de.muenchen.mcmp.loadbalancer.LoadbalancerService;
import de.muenchen.mcmp.mountPoint.MountPointService;
import de.muenchen.mcmp.network.NetworkService;
import de.muenchen.mcmp.security.RequestBodyCachingFilter;
import de.muenchen.mcmp.security.RequestResponseLoggingFilter;
import de.muenchen.mcmp.server.ServerService;
import de.muenchen.mcmp.snapshot.SnapshotService;
import de.muenchen.mcmp.storage.UnifiedStorageService;
import de.muenchen.mcmp.user.User;
import de.muenchen.mcmp.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * This class tests the read and query {@code /job/*} endpoints. The tests check
 * the role-based access rules from {@code @PreAuthorize}
 * ({@link de.muenchen.mcmp.security.HasUserOrSpecialRole}, {@link de.muenchen.mcmp.security.HasSpecialRole},
 * {@link de.muenchen.mcmp.security.IsAdmin}). The tests also check the page and
 * itemsPerPage limit rules.
 */
@WebMvcTest(controllers = JobController.class, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
        classes = {MaintenanceModeConfiguration.class, MaintenanceModeInterceptor.class, RequestResponseLoggingFilter.class,
                RequestBodyCachingFilter.class, NfcRequestFilter.class}))
@Import(JobControllerWebTest.TestSecurityConfig.class)
class JobControllerReadEndpointsTest {

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

    // -----------------------------------------------------------------------------------------------------------------
    // Tests for @PreAuthorize access rules
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void hierarchy_plainUser_isAllowed_hasUserOrSpecialRole() throws Exception {
        when(jobService.getJobHierarchy(1L)).thenReturn(List.of());
        mockMvc.perform(get("/job/1/hierarchy")).andExpect(status().isOk());
    }

    @Test
    void hierarchy_unauthenticated_isRejected() throws Exception {
        // The minimal test filter chain has no AuthenticationEntryPoint. So this is
        // Spring Security's default rejection for anonymous access, not the app's 401.
        // The app's 401 comes from the OAuth2 resource server config. This test does
        // not load that config.
        mockMvc.perform(get("/job/1/hierarchy")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_READONLY")
    void search_plainReadonlyUser_isRejected_hasSpecialRole() throws Exception {
        // @HasSpecialRole does not allow the role ROLE_READONLY. Unlike @HasUserOrSpecialRole,
        // @HasSpecialRole also does not fall back to the plain role ROLE_USER. The request
        // must not reach JobService.
        mockMvc.perform(get("/job/search")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void search_admin_isAllowed_hasSpecialRole() throws Exception {
        when(jobService.findAllJobsByRole(anyInt(), anyInt(), any(), anyBoolean(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());
        mockMvc.perform(get("/job/search")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_LINUX")
    void search_linuxRole_isAllowed_hasSpecialRole() throws Exception {
        // This test shows that hasAnyRole(...) matches every role in the @HasSpecialRole
        // list, not only the admin role.
        when(jobService.findAllJobsByRole(anyInt(), anyInt(), any(), anyBoolean(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());
        mockMvc.perform(get("/job/search")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void statistics_plainUser_isRejected_isAdmin() throws Exception {
        mockMvc.perform(get("/job/statistics").param("startDate", "2026-01-01").param("endDate", "2026-01-31"))
                .andExpect(status().isForbidden());
        verifyNoJobServiceStatisticsCall();
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void statistics_admin_isAllowed_isAdmin() throws Exception {
        when(jobService.getJobStatistics(any(), any())).thenReturn(List.of());
        mockMvc.perform(get("/job/statistics").param("startDate", "2026-01-01").param("endDate", "2026-01-31"))
                .andExpect(status().isOk());
    }

    private void verifyNoJobServiceStatisticsCall() {
        org.mockito.Mockito.verify(jobService, org.mockito.Mockito.never()).getJobStatistics(any(), any());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Tests for the page and itemsPerPage limit rules
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void search_negativePage_isClampedToOne() throws Exception {
        when(jobService.findAllJobsByRole(anyInt(), anyInt(), any(), anyBoolean(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/job/search").param("page", "-5")).andExpect(status().isOk());

        verify(jobService).findAllJobsByRole(eq(1), eq(10), any(), eq(false), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void search_itemsPerPageOutOfBounds_isClampedToTen() throws Exception {
        when(jobService.findAllJobsByRole(anyInt(), anyInt(), any(), anyBoolean(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/job/search").param("itemsPerPage", "999")).andExpect(status().isOk());

        verify(jobService).findAllJobsByRole(eq(1), eq(10), any(), eq(false), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void jobsByServerId_itemsPerPageWithinBounds_isPassedThroughUnchanged() throws Exception {
        when(jobService.findAllJobsByRole(anyInt(), anyInt(), any(), anyBoolean(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/job/server/1001").param("page", "3").param("itemsPerPage", "25")).andExpect(status().isOk());

        verify(jobService).findAllJobsByRole(eq(3), eq(25), any(), eq(false), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), eq(1001L), isNull(), isNull(), isNull(), isNull());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // The /user endpoint finds the current user before it sends the request to JobService.
    // The endpoint sends a 403 response if the user record is not found.
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "tester", authorities = "ROLE_USER")
    void jobsByUser_knownUser_succeeds() throws Exception {
        User user = new User();
        user.setId(42L);
        user.setUsername("tester");
        when(userService.findByUsername("tester")).thenReturn(Optional.of(user));
        when(jobService.findAllJobsByRole(anyInt(), anyInt(), any(), anyBoolean(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/job/user")).andExpect(status().isOk());

        verify(jobService).findAllJobsByRole(eq(1), eq(10), any(), eq(false), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), eq(42L), isNull(), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    @WithMockUser(username = "ghost", authorities = "ROLE_USER")
    void jobsByUser_unknownUser_isRejected() throws Exception {
        when(userService.findByUsername("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(get("/job/user")).andExpect(status().isForbidden());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // notification endpoints
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "tester", authorities = "ROLE_USER")
    void notificationCount_succeeds() throws Exception {
        when(jobService.getJobNotificationsByUsername("tester")).thenReturn(3L);
        mockMvc.perform(get("/job/user/notification")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "tester", authorities = "ROLE_USER")
    void resetNotifications_succeeds() throws Exception {
        mockMvc.perform(put("/job/user/notification")).andExpect(status().isOk());
        verify(jobService).resetJobNotificationsByUsername("tester");
    }
}
