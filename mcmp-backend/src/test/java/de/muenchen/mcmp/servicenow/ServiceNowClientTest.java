package de.muenchen.mcmp.servicenow;

import de.muenchen.mcmp.configuration.EncryptionProperties;
import de.muenchen.mcmp.servicenow.json.ServiceNowUserResponse;
import de.muenchen.mcmp.servicenow.json.ServiceNowUserResult;
import de.muenchen.mcmp.snowConfig.SnowConfigRepository;
import de.muenchen.mcmp.snowConfig.SnowConfigWithDecryptedPassword;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceNowClientTest {

    @Mock
    private EncryptionProperties encryptionProperties;

    @Mock
    private SnowConfigRepository snowConfigRepository;

    @Mock
    private ServiceNowProperties serviceNowProperties;

    @Mock
    private SnowConfigWithDecryptedPassword config;

    @Mock
    private RestClient restClient;

    private ServiceNowClient serviceNowClient;

    private static final String TEST_USERNAME = "john.doe";
    private static final String TEST_SYS_ID = "abc123";
    private static final String TEST_ACCESS_TOKEN = "test-token-123";
    private static final String TEST_API_ENDPOINT = "https://servicenow.example.org/api/now";
    private static final String TEST_AUTH_URL = "https://servicenow.example.org/oauth_token.do";
    private static final String TEST_CLIENT_ID = "test-client-id";
    private static final String TEST_CLIENT_SECRET = "test-client-secret";
    private static final String TEST_PASSPHRASE = "test-passphrase";

    @BeforeEach
    void setUp() {
        serviceNowClient = new ServiceNowClient(encryptionProperties, snowConfigRepository, serviceNowProperties);
    }

    private void setupDefaultConfigLenient() {
        lenient().when(encryptionProperties.getPassphrase()).thenReturn(TEST_PASSPHRASE);
        lenient().when(snowConfigRepository.findDefaultServiceNowConfig(TEST_PASSPHRASE)).thenReturn(config);
        lenient().when(config.getEnabled()).thenReturn(true);
        lenient().when(config.getApiEndpoint()).thenReturn(TEST_API_ENDPOINT);
        lenient().when(config.getApiClientAuthUrl()).thenReturn(TEST_AUTH_URL);
        lenient().when(config.getApiClientId()).thenReturn(TEST_CLIENT_ID);
        lenient().when(config.getApiClientSecret()).thenReturn(TEST_CLIENT_SECRET);
        lenient().when(config.getUseProxy()).thenReturn(false);
    }

    @Test
    void testGetSysIdByUsername_Success() {
        // Arrange
        setupDefaultConfigLenient();
        ServiceNowUserResult userResult = new ServiceNowUserResult(TEST_SYS_ID, TEST_USERNAME);
        ServiceNowUserResponse userResponse = new ServiceNowUserResponse(List.of(userResult));

        ServiceNowClient spyClient = spy(serviceNowClient);
        doReturn(restClient).when(spyClient).buildRestClient(any());
        doReturn(TEST_ACCESS_TOKEN).when(spyClient).fetchAccessToken(any(), any());
        doReturn(userResponse).when(spyClient).fetchUser(any(), any(), any());

        // Act
        Optional<String> result = spyClient.getSysIdByUsername(TEST_USERNAME);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(TEST_SYS_ID, result.get());
        verify(spyClient).buildRestClient(config);
        verify(spyClient).fetchAccessToken(config, restClient);
        verify(spyClient).fetchUser(restClient, TEST_ACCESS_TOKEN, TEST_USERNAME);
    }

    @Test
    void testGetSysIdByUsername_UserNotFound() {
        // Arrange
        setupDefaultConfigLenient();
        ServiceNowUserResponse userResponse = new ServiceNowUserResponse(Collections.emptyList());

        ServiceNowClient spyClient = spy(serviceNowClient);
        doReturn(restClient).when(spyClient).buildRestClient(any());
        doReturn(TEST_ACCESS_TOKEN).when(spyClient).fetchAccessToken(any(), any());
        doReturn(userResponse).when(spyClient).fetchUser(any(), any(), any());

        // Act
        Optional<String> result = spyClient.getSysIdByUsername(TEST_USERNAME);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void testGetSysIdByUsername_NullUsername() {
        // Act & Assert
        assertThrows(NullPointerException.class, () -> serviceNowClient.getSysIdByUsername(null));
    }

    @Test
    void testGetSysIdByUsername_EmptyUsername() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> serviceNowClient.getSysIdByUsername(""));
    }

    @Test
    void testGetSysIdByUsername_BlankUsername() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> serviceNowClient.getSysIdByUsername("   "));
    }

    @Test
    void testGetSysIdByUsername_ConfigNotFound() {
        // Arrange
        when(encryptionProperties.getPassphrase()).thenReturn(TEST_PASSPHRASE);
        when(snowConfigRepository.findDefaultServiceNowConfig(TEST_PASSPHRASE)).thenReturn(null);

        // Act & Assert
        ServiceNowClientException exception = assertThrows(ServiceNowClientException.class,
                () -> serviceNowClient.getSysIdByUsername(TEST_USERNAME));
        assertTrue(exception.getMessage().contains("not available"));
    }

    @Test
    void testGetSysIdByUsername_ConfigDisabled() {
        // Arrange
        when(encryptionProperties.getPassphrase()).thenReturn(TEST_PASSPHRASE);
        when(snowConfigRepository.findDefaultServiceNowConfig(TEST_PASSPHRASE)).thenReturn(config);
        when(config.getEnabled()).thenReturn(false);

        // Act & Assert
        ServiceNowClientException exception = assertThrows(ServiceNowClientException.class,
                () -> serviceNowClient.getSysIdByUsername(TEST_USERNAME));
        assertTrue(exception.getMessage().contains("disabled"));
    }

    @Test
    void testGetSysIdByUsername_TokenResponseNull() {
        // Arrange
        setupDefaultConfigLenient();
        ServiceNowClient spyClient = spy(serviceNowClient);
        doReturn(restClient).when(spyClient).buildRestClient(any());
        doThrow(new ServiceNowClientException("Error retrieving access token."))
                .when(spyClient).fetchAccessToken(any(), any());

        // Act & Assert
        ServiceNowClientException exception = assertThrows(ServiceNowClientException.class,
                () -> spyClient.getSysIdByUsername(TEST_USERNAME));
        assertTrue(exception.getMessage().contains("access token"));
    }

    @Test
    void testGetSysIdByUsername_HttpError4xx() {
        // Arrange
        setupDefaultConfigLenient();
        ServiceNowClient spyClient = spy(serviceNowClient);
        doReturn(restClient).when(spyClient).buildRestClient(any());
        doReturn(TEST_ACCESS_TOKEN).when(spyClient).fetchAccessToken(any(), any());

        RestClientResponseException mockException = new RestClientResponseException(
                "Not Found",
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                null,
                "User not found".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);

        doThrow(mockException).when(spyClient).fetchUser(any(), any(), any());

        // Act & Assert
        ServiceNowClientException exception = assertThrows(ServiceNowClientException.class,
                () -> spyClient.getSysIdByUsername(TEST_USERNAME));
        assertTrue(exception.getMessage().contains("Error in ServiceNow API request"));
    }

    @Test
    void testGetSysIdByUsername_HttpError5xx() {
        // Arrange
        setupDefaultConfigLenient();
        ServiceNowClient spyClient = spy(serviceNowClient);
        doReturn(restClient).when(spyClient).buildRestClient(any());
        doReturn(TEST_ACCESS_TOKEN).when(spyClient).fetchAccessToken(any(), any());

        RestClientResponseException mockException = new RestClientResponseException(
                "Internal Server Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                null,
                "Server error".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);

        doThrow(mockException).when(spyClient).fetchUser(any(), any(), any());

        // Act & Assert
        ServiceNowClientException exception = assertThrows(ServiceNowClientException.class,
                () -> spyClient.getSysIdByUsername(TEST_USERNAME));
        assertTrue(exception.getMessage().contains("Error in ServiceNow API request"));
    }

    @Test
    void testGetSysIdByUsername_UnexpectedException() {
        // Arrange
        when(encryptionProperties.getPassphrase()).thenReturn(TEST_PASSPHRASE);
        when(snowConfigRepository.findDefaultServiceNowConfig(TEST_PASSPHRASE))
                .thenThrow(new RuntimeException("Database connection failed"));

        // Act & Assert
        ServiceNowClientException exception = assertThrows(ServiceNowClientException.class,
                () -> serviceNowClient.getSysIdByUsername(TEST_USERNAME));
        assertTrue(exception.getMessage().contains("Error retrieving sys_id"));
    }

    @Test
    void testGetSysIdByUsername_WithProxy() {
        // Arrange
        setupDefaultConfigLenient();
        when(config.getUseProxy()).thenReturn(true);
        when(config.getProxy()).thenReturn("http://proxy.example.com:8080");

        ServiceNowUserResult userResult = new ServiceNowUserResult(TEST_SYS_ID, TEST_USERNAME);
        ServiceNowUserResponse userResponse = new ServiceNowUserResponse(List.of(userResult));

        ServiceNowClient spyClient = spy(serviceNowClient);
        // Don't mock buildRestClient - let it execute the real implementation
        doReturn(TEST_ACCESS_TOKEN).when(spyClient).fetchAccessToken(any(), any());
        doReturn(userResponse).when(spyClient).fetchUser(any(), any(), any());

        // Act
        Optional<String> result = spyClient.getSysIdByUsername(TEST_USERNAME);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(TEST_SYS_ID, result.get());
        verify(config).getUseProxy();
        verify(config).getProxy();
    }

    @Test
    void testGetSysIdByUsername_ResponseNull() {
        // Arrange
        setupDefaultConfigLenient();
        ServiceNowClient spyClient = spy(serviceNowClient);
        doReturn(restClient).when(spyClient).buildRestClient(any());
        doReturn(TEST_ACCESS_TOKEN).when(spyClient).fetchAccessToken(any(), any());
        doReturn(null).when(spyClient).fetchUser(any(), any(), any());

        // Act
        Optional<String> result = spyClient.getSysIdByUsername(TEST_USERNAME);

        // Assert
        assertFalse(result.isPresent());
    }
}