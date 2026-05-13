package de.muenchen.mcmp.infobloxConfig;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InfobloxConfigRepository extends JpaRepository<InfobloxConfig, Long> {
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO config_infoblox (
              api_description,
              api_username,
              api_password_encrypted,
              api_endpoint,
              created_at,
              updated_at
            ) VALUES (
              :description,
              :username,
              pgp_sym_encrypt(:password, :key),
              :endpoint,
              CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP
            )
            """, nativeQuery = true)
    void insertConfig(
            @Param("description") String description,
            @Param("username") String username,
            @Param("password") String password,
            @Param("key") String key,
            @Param("endpoint") String endpoint
    );

    @Modifying
    @Transactional
    @Query(value = """
    UPDATE config_infoblox
    SET api_password_encrypted = pgp_sym_encrypt(:password, :key),
        updated_at = CURRENT_TIMESTAMP
    WHERE id = :id
    """, nativeQuery = true)
    void updatePassword(
            @Param("id") Long id,
            @Param("password") String password,
            @Param("key") String key
    );

    @Query("SELECT ic FROM InfobloxConfig ic WHERE ic.apiEndpoint LIKE :hostnamePattern%")
    Optional<InfobloxConfig> findByHostname(@Param("hostnamePattern") String hostnamePattern);

    @Query(value = """
            SELECT api_username, pgp_sym_decrypt(api_password_encrypted, :key) AS api_password, api_endpoint
            FROM cmp.config_infoblox
            WHERE id = :id
            """, nativeQuery = true)
    InfobloxConfigWithDecryptedPassword findByIdDecrypted(@Param("id") Long id, @Param("key") String key);

    @Query(value = """
            SELECT api_username, pgp_sym_decrypt(api_password_encrypted, :key) AS api_password, api_endpoint
            FROM cmp.config_infoblox
            WHERE is_default = true
            """, nativeQuery = true)
    InfobloxConfigWithDecryptedPassword findDefaultInfoblox(@Param("key") String key);


}

