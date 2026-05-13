package de.muenchen.mcmp.snowConfig;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SnowConfigRepository extends JpaRepository<SnowConfig, Long> {
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO config_snow (
          api_description,
          api_client_auth_url,
          api_client_id,
          api_client_secret_encrypted,
          api_endpoint,
          enabled,
          proxy,
          use_proxy,
          created_at,
          updated_at
        ) VALUES (
          :description,
          :apiClientAuthUrl,
          :apiClientId,
          pgp_sym_encrypt(:clientSecret, :key),
          :endpoint,
          :enabled,
          :proxy,
          :useProxy,
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
        )
        """, nativeQuery = true)
    void insertConfig(
            @Param("description") String description,
            @Param("apiClientAuthUrl") String apiClientAuthUrl,
            @Param("apiClientId") String apiClientId,
            @Param("clientSecret") String clientSecret,
            @Param("key") String key,
            @Param("endpoint") String endpoint,
            @Param("enabled") boolean enabled,
            @Param("proxy") String proxy,
            @Param("useProxy") boolean useProxy
    );

    @Modifying
    @Transactional
    @Query(value = """
    UPDATE config_snow
    SET api_client_secret_encrypted = pgp_sym_encrypt(:clientSecret, :key),
        updated_at = CURRENT_TIMESTAMP
    WHERE id = :id
    """, nativeQuery = true)
    void updateSecret(
            @Param("id") Long id,
            @Param("clientSecret") String clientSecret,
            @Param("key") String key
    );


    @Query(value = """
            SELECT api_description, api_endpoint, enabled, proxy, use_proxy, api_client_auth_url, api_client_id, pgp_sym_decrypt(api_client_secret_encrypted, :key) AS api_client_secret
            FROM cmp.config_snow
            WHERE is_default = true
            """, nativeQuery = true)
    SnowConfigWithDecryptedPassword findDefaultServiceNowConfig(@Param("key") String key);
}

