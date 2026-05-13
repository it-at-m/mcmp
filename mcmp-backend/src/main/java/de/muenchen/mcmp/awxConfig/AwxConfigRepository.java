package de.muenchen.mcmp.awxConfig;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AwxConfigRepository extends JpaRepository<AwxConfig, Long> {
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO config_awx (
          api_description,
          api_username,
          api_password_encrypted,
          api_endpoint,
          enabled,
          created_at,
          updated_at
        ) VALUES (
          :description,
          :username,
          pgp_sym_encrypt(:password, :key),
          :endpoint,
          :enabled,
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
        )
        """, nativeQuery = true)
    void insertConfig(
            @Param("description") String description,
            @Param("username") String username,
            @Param("password") String password,
            @Param("key") String key,
            @Param("endpoint") String endpoint,
            @Param("enabled") boolean enabled
    );

    @Modifying
    @Transactional
    @Query(value = """
    UPDATE config_awx
    SET api_password_encrypted = pgp_sym_encrypt(:password, :key),
        updated_at = CURRENT_TIMESTAMP
    WHERE id = :id
    """, nativeQuery = true)
    void updatePassword(
            @Param("id") Long id,
            @Param("password") String password,
            @Param("key") String key
    );

    @Query(value = """
            SELECT id, api_description, api_username,
                   pgp_sym_decrypt(api_password_encrypted, :key) AS api_password_encrypted,
                   api_endpoint, enabled, created_at, updated_at, version
            FROM cmp.config_awx
            WHERE id = :id
            """, nativeQuery = true)
    AwxConfig findByIdDecrypted(@Param("id") Long id, @Param("key") String key);
}

