package de.muenchen.mcmp.job;

import de.muenchen.mcmp.appservice.AppserviceDTO;
import de.muenchen.mcmp.appservice.AppserviceService;
import de.muenchen.mcmp.cloud.Cloud;
import de.muenchen.mcmp.errorlog.ErrorLogService;
import de.muenchen.mcmp.loadbalancer.LoadbalancerService;
import de.muenchen.mcmp.loadbalancer.UnifiedLoadbalancer;
import de.muenchen.mcmp.loadbalancer.UnifiedLoadbalancerPoolDTO;
import de.muenchen.mcmp.mountPoint.MountPointService;
import de.muenchen.mcmp.network.NetworkService;
import de.muenchen.mcmp.ontap.OntapCifsShareAclListDto;
import de.muenchen.mcmp.ontap.OntapExportPolicyListDto;
import de.muenchen.mcmp.ontap.OntapExportPolicyRuleListDto;
import de.muenchen.mcmp.server.ServerFullDTO;
import de.muenchen.mcmp.server.ServerService;
import de.muenchen.mcmp.snapshot.SnapshotService;
import de.muenchen.mcmp.storage.StorageCategory;
import de.muenchen.mcmp.storage.StorageType;
import de.muenchen.mcmp.storage.UnifiedStorageItemDto;
import de.muenchen.mcmp.storage.UnifiedStorageService;
import de.muenchen.mcmp.types.CloudType;
import de.muenchen.mcmp.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * This class checks that every {@code /job/create/*} endpoint still accepts
 * a minimal, valid payload. Each test sends this payload and checks that the
 * request reaches {@link JobService} with an HTTP 200 response.
 */
@WebMvcTest(controllers = JobController.class, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
        classes = {de.muenchen.mcmp.config.app.MaintenanceModeConfiguration.class, de.muenchen.mcmp.config.app.MaintenanceModeInterceptor.class,
                de.muenchen.mcmp.security.RequestResponseLoggingFilter.class, de.muenchen.mcmp.security.RequestBodyCachingFilter.class,
                de.muenchen.mcmp.configuration.nfcconverter.NfcRequestFilter.class}))
@Import(JobControllerWebTest.TestSecurityConfig.class)
@WithMockUser
class JobControllerValidRequestsTest {

    private static final long SERVER_ID = 1001L; // These IDs are examples.
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

    private static final String VALID_FQDN = "{\"prefix\":\"\",\"application\":\"test\",\"domain\":\"srv.muenchen.de\",\"customNumber\":1}";

    private void allowServerEdit() {
        when(serverService.canUserEditServer(SERVER_ID)).thenReturn(true);
    }

    private void allowRhelAppserviceAndNetwork() {
        when(appserviceService.getVisibleAppservice(APPSERVICE_ID)).thenReturn(
                new AppserviceDTO(APPSERVICE_ID, "AWX-TEST", "SNSVC0001", null, "Standard", null,
                        null, null, null, null, null, null, null, false, List.of()));
        when(appserviceService.canUserEditAppservice(APPSERVICE_ID)).thenReturn(true);
        when(networkService.isAllowedNetworkGroupForAppservice(eq(NETWORK_GROUP_ID), eq(APPSERVICE_ID), anyBoolean())).thenReturn(true);
    }

    private ServerFullDTO serverWithCloudType(final CloudType cloudType) {
        final Cloud cloud = new Cloud();
        cloud.setCloudType(cloudType);
        return ServerFullDTO.builder().cloud(cloud).build();
    }

    private UnifiedStorageItemDto storageItem(final StorageCategory category) {
        return UnifiedStorageItemDto.builder()
                .uuid("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
                .storageCategory(category)
                .size(100L * 1024 * 1024 * 1024)
                .spaceLogicalUsed(0L)
                .spaceSnapshotUsed(0L)
                .spaceLogicalUsedByAfs(0L)
                .nfs_export_policy(OntapExportPolicyListDto.builder()
                        .ontapExportPolicyRules(Set.of(OntapExportPolicyRuleListDto.builder()
                                .clients(List.of("test.srv.muenchen.de"))
                                .build()))
                        .build())
                .cifs_share_acl_list(List.of(OntapCifsShareAclListDto.builder()
                        .userOrGroup("DOMAIN\\group1")
                        .permission("read")
                        .build()))
                .build();
    }

    private void perform(final String job, final String body) throws Exception {
        mockMvc.perform(post("/job/create/" + job).param("serverId", String.valueOf(JobControllerValidRequestsTest.SERVER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // VM operation jobs
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void startServer_validPayload_succeeds() throws Exception {
        allowServerEdit();
        perform("START_SERVER", "{}");
    }

    @Test
    void stopServer_validPayload_succeeds() throws Exception {
        allowServerEdit();
        perform("STOP_SERVER", "{}");
    }

    @Test
    void restartServer_validPayload_succeeds() throws Exception {
        allowServerEdit();
        perform("RESTART_SERVER", "{}");
    }

    @Test
    void createSnapshot_validPayload_succeeds() throws Exception {
        allowServerEdit();
        perform("CREATE_SNAPSHOT", "{\"duration\":5}");
    }

    @Test
    void deleteSnapshot_validPayload_succeeds() throws Exception {
        allowServerEdit();
        when(serverService.getServerById(SERVER_ID)).thenReturn(serverWithCloudType(CloudType.VMWARE));
        perform("DELETE_SNAPSHOT", "{\"snapshotId\":123}");
    }

    @Test
    void revertSnapshot_validPayload_succeeds() throws Exception {
        allowServerEdit();
        when(serverService.getServerById(SERVER_ID)).thenReturn(serverWithCloudType(CloudType.VMWARE));
        perform("REVERT_SNAPSHOT", "{\"snapshotId\":123}");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // CheckMK jobs
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void checkmkSetDowntime_validPayload_succeeds() throws Exception {
        allowServerEdit();
        perform("CHECKMK_SET_DOWNTIME", "{\"startDate\":\"19.08.2026 10:00:00\",\"duration\":30}");
    }

    @Test
    void checkmkServiceDiscovery_validPayload_succeeds() throws Exception {
        allowServerEdit();
        perform("CHECKMK_SERVICE_DISCOVERY", "{\"action\":\"new\"}");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Linux jobs
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void linuxPatchnightTimeChange_validPayload_succeeds() throws Exception {
        allowServerEdit();
        perform("LINUX_PATCHNIGHT_TIME_CHANGE", "{\"time\":\"1500\"}");
    }

    @Test
    void linuxDeleteServer_validPayload_succeeds() throws Exception {
        allowServerEdit();
        perform("LINUX_DELETE_SERVER", "{}");
    }

    @Test
    void linuxTempRoot_validPayload_succeeds() throws Exception {
        allowServerEdit();
        perform("LINUX_TEMP_ROOT", "{}");
    }

    @Test
    void linuxMountpointChange_validPayload_succeeds() throws Exception {
        allowServerEdit();
        when(snapshotService.getSnapshotsByServerId(SERVER_ID)).thenReturn(List.of());
        when(mountPointService.getMountPointByServerIdAndPath(eq(SERVER_ID), anyString())).thenReturn(null);
        perform("LINUX_MOUNTPOINT_CHANGE", "{\"mountPoint\":\"/data/vol1\",\"newSize\":50,\"volumeGroup\":\"vg00\"}");
    }

    @Test
    void linuxRhel9Server_validPayload_succeeds() throws Exception {
        allowRhelAppserviceAndNetwork();
        perform("LINUX_RHEL9_SERVER", "{\"fqdn\":" + VALID_FQDN + ",\"categoryType\":\"Standard\",\"serverType\":null,"
                + "\"ram\":2,\"cpu\":1,\"network_group_id\":3003,\"application_service_id\":2002,"
                + "\"db_params\":null,\"non_postgres_reason\":\"\"}");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Windows jobs
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void windowsDeleteServer_validPayload_succeeds() throws Exception {
        allowServerEdit();
        perform("WINDOWS_DELETE_SERVER", "{}");
    }

    @Test
    void windowsTempAdmin_validPayload_succeeds() throws Exception {
        allowServerEdit();
        perform("WINDOWS_TEMP_ADMIN", "{}");
    }

    @Test
    void windowsMaintenanceMode_validPayload_succeeds() throws Exception {
        allowServerEdit();
        perform("WINDOWS_MAINTENANCE_MODE", "{\"wartungsmodus_ende\":\"19.08.2026 10:00:00\"}");
    }

    @Test
    void windowsMaintenanceModeEnd_validRequest_succeeds() throws Exception {
        allowServerEdit();
        mockMvc.perform(post("/job/create/WINDOWS_MAINTENANCE_MODE_END").param("serverId", String.valueOf(SERVER_ID)))
                .andExpect(status().isOk());
    }

    @Test
    void windowsServer2025_validPayload_succeeds() throws Exception {
        allowRhelAppserviceAndNetwork();
        perform("WINDOWS_SERVER_2025", "{\"fqdn\":" + VALID_FQDN + ",\"categoryType\":\"Standard\",\"serverType\":null,"
                + "\"ram\":2,\"cpu\":1,\"disks\":[{\"drive_number\":0,\"size\":100}],"
                + "\"network_group_id\":3003,\"application_service_id\":2002,\"osVersion\":\"Windows Server 2025\"}");
    }

    @Test
    void windowsServer2022_validPayload_succeeds() throws Exception {
        allowRhelAppserviceAndNetwork();
        perform("WINDOWS_SERVER_2022", "{\"fqdn\":" + VALID_FQDN + ",\"categoryType\":\"Standard\",\"serverType\":null,"
                + "\"ram\":2,\"cpu\":1,\"disks\":[{\"drive_number\":0,\"size\":100}],"
                + "\"network_group_id\":3003,\"application_service_id\":2002,\"osVersion\":\"Windows Server 2022\"}");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Database and Ansible jobs
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void dbOracleCreateBackup_validPayload_succeeds() throws Exception {
        allowServerEdit();
        when(serverService.getServerById(SERVER_ID)).thenReturn(ServerFullDTO.builder().roleOracle(true).build());
        perform("DB_ORACLE_CREATE_BACKUP", "{\"flag\":\"volle_Sicherung\"}");
    }

    @Test
    void ansibleUserAdd_validPayload_succeeds() throws Exception {
        allowServerEdit();
        perform("ANSIBLE_USER_ADD", "{\"account_name\":\"svc-ans-test\",\"server_ids\":[1001]}");
    }

    @Test
    void ansibleUserRemove_validPayload_succeeds() throws Exception {
        perform("ANSIBLE_USER_REMOVE", "{\"account_name\":\"svc-ans-test\"}");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Loadbalancer jobs
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void loadbalancerF5ChangePoolMembers_validPayload_succeeds() throws Exception {
        when(loadbalancerService.canUserEditLoadbalancer(anyLong())).thenReturn(true);
        when(loadbalancerService.getLoadbalancerById(anyLong())).thenReturn(
                UnifiedLoadbalancer.builder()
                        .wafEnabled(false)
                        .pools(List.of(UnifiedLoadbalancerPoolDTO.builder()
                                .name("pool1")
                                .members(List.of())
                                .build()))
                        .build());
        perform("LOADBALANCER_F5_CHANGE_POOL_MEMBERS",
                "{\"lb_virtual_server_id\":123,\"pool_name\":\"pool1\",\"removed\":[{\"ip\":\"10.0.0.1\",\"port\":443}]}");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Storage jobs
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void storageModifyNfs_validPayload_succeeds() throws Exception {
        when(unifiedStorageService.getUnifiedStorageItem(anyString(), eq(StorageType.NFS)))
                .thenReturn(storageItem(StorageCategory.NFS_STANDARD_SHARE));
        when(unifiedStorageService.canUserEditStorage(anyString(), eq(StorageType.NFS))).thenReturn(true);
        perform("STORAGE_MODIFY_NFS", "{\"uuid\":\"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\",\"new_size\":100,\"new_snapshot_percent\":10}");
    }

    @Test
    void storageModifyCifs_validPayload_succeeds() throws Exception {
        when(unifiedStorageService.getUnifiedStorageItem(anyString(), eq(StorageType.CIFS)))
                .thenReturn(storageItem(StorageCategory.CIFS_STANDARD_SHARE));
        when(unifiedStorageService.canUserEditStorage(anyString(), eq(StorageType.CIFS))).thenReturn(true);
        perform("STORAGE_MODIFY_CIFS", "{\"uuid\":\"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\",\"new_size\":100,\"new_snapshot_percent\":10}");
    }

    @Test
    void storageCreateSnapshotNfs_validPayload_succeeds() throws Exception {
        when(unifiedStorageService.getUnifiedStorageItem(anyString(), eq(StorageType.NFS)))
                .thenReturn(storageItem(StorageCategory.NFS_STANDARD_SHARE));
        when(unifiedStorageService.canUserEditStorage(anyString(), eq(StorageType.NFS))).thenReturn(true);
        perform("STORAGE_CREATE_SNAPSHOT_NFS", "{\"uuid\":\"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\",\"description\":\"snap1\"}");
    }

    @Test
    void storageCreateSnapshotCifs_validPayload_succeeds() throws Exception {
        when(unifiedStorageService.getUnifiedStorageItem(anyString(), eq(StorageType.CIFS)))
                .thenReturn(storageItem(StorageCategory.CIFS_STANDARD_SHARE));
        when(unifiedStorageService.canUserEditStorage(anyString(), eq(StorageType.CIFS))).thenReturn(true);
        perform("STORAGE_CREATE_SNAPSHOT_CIFS", "{\"uuid\":\"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\",\"description\":\"snap1\"}");
    }

    @Test
    void storageDeleteSnapshotNfs_validPayload_succeeds() throws Exception {
        when(unifiedStorageService.getUnifiedStorageItem(anyString(), eq(StorageType.NFS)))
                .thenReturn(storageItem(StorageCategory.NFS_STANDARD_SHARE));
        when(unifiedStorageService.canUserEditStorage(anyString(), eq(StorageType.NFS))).thenReturn(true);
        perform("STORAGE_DELETE_SNAPSHOT_NFS", "{\"uuid\":\"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\",\"snapshotName\":\"snap-1\"}");
    }

    @Test
    void storageDeleteSnapshotCifs_validPayload_succeeds() throws Exception {
        when(unifiedStorageService.getUnifiedStorageItem(anyString(), eq(StorageType.CIFS)))
                .thenReturn(storageItem(StorageCategory.CIFS_STANDARD_SHARE));
        when(unifiedStorageService.canUserEditStorage(anyString(), eq(StorageType.CIFS))).thenReturn(true);
        perform("STORAGE_DELETE_SNAPSHOT_CIFS", "{\"uuid\":\"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\",\"snapshotName\":\"snap-1\"}");
    }

    @Test
    void storageChangeSnapshotPolicyNfs_validPayload_succeeds() throws Exception {
        when(unifiedStorageService.getUnifiedStorageItem(anyString(), eq(StorageType.NFS)))
                .thenReturn(storageItem(StorageCategory.NFS_STANDARD_SHARE));
        when(unifiedStorageService.canUserEditStorage(anyString(), eq(StorageType.NFS))).thenReturn(true);
        perform("STORAGE_CHANGE_SNAPSHOT_POLICY_NFS", "{\"uuid\":\"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\",\"newPolicy\":\"dcc-6h\"}");
    }

    @Test
    void storageChangeSnapshotPolicyCifs_validPayload_succeeds() throws Exception {
        when(unifiedStorageService.getUnifiedStorageItem(anyString(), eq(StorageType.CIFS)))
                .thenReturn(storageItem(StorageCategory.CIFS_STANDARD_SHARE));
        when(unifiedStorageService.canUserEditStorage(anyString(), eq(StorageType.CIFS))).thenReturn(true);
        perform("STORAGE_CHANGE_SNAPSHOT_POLICY_CIFS", "{\"uuid\":\"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\",\"newPolicy\":\"dcc-6h\"}");
    }

    @Test
    void storageChangeNfsExportPolicy_validPayload_succeeds() throws Exception {
        when(unifiedStorageService.getUnifiedStorageItem(anyString(), eq(StorageType.NFS)))
                .thenReturn(storageItem(StorageCategory.NFS_STANDARD_SHARE));
        when(unifiedStorageService.canUserEditStorage(anyString(), eq(StorageType.NFS))).thenReturn(true);
        perform("STORAGE_CHANGE_NFS_EXPORT_POLICY",
                "{\"uuid\":\"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\",\"fqdn\":\"test.srv.muenchen.de\",\"permission\":\"rw\"}");
    }

    @Test
    void storageChangeCifsPermissions_validPayload_succeeds() throws Exception {
        when(unifiedStorageService.getUnifiedStorageItem(anyString(), eq(StorageType.CIFS)))
                .thenReturn(storageItem(StorageCategory.CIFS_STANDARD_SHARE));
        when(unifiedStorageService.canUserEditStorage(anyString(), eq(StorageType.CIFS))).thenReturn(true);
        perform("STORAGE_CHANGE_CIFS_PERMISSIONS",
                "{\"uuid\":\"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\",\"ad\":\"DOMAIN\\\\group1\",\"permission\":\"read\"}");
    }
}
