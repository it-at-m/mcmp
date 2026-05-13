package de.muenchen.mcmp.cloud;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.parameters.P;

import java.util.List;
import java.util.Optional;

public interface CloudRepository extends JpaRepository<Cloud, Long> {

    Optional<Cloud> findByApiEndpoint(String apiEndpoint);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO cloud (
          name,
          fqdn,
          server_gui,
          cloud_type,
          api_description,
          api_username,
          api_password_encrypted,
          api_endpoint,
          enabled,
          locked,
          config_infoblox_id,
          config_baas_id,
          green_it_enabled,
          created_at,
          updated_at
        ) VALUES (
          :name,
          :fqdn,
          :serverGui,
          CAST(:cloudType AS cloud_type),
          :description,
          :username,
          pgp_sym_encrypt(:password, :key),
          :endpoint,
          :enabled,
          :locked,
          :configInfobloxId,
          :configBaasId,
          :greenItEnabled,
          CURRENT_TIMESTAMP,
          CURRENT_TIMESTAMP
        )
        """, nativeQuery = true)
    void insertConfig(
            @Param("name") String name,
            @Param("fqdn") String fqdn,
            @Param("serverGui") String serverGui,
            @Param("cloudType") String cloudType,
            @Param("description") String description,
            @Param("username") String username,
            @Param("password") String password,
            @Param("key") String key,
            @Param("endpoint") String endpoint,
            @Param("enabled") boolean enabled,
            @Param("locked") boolean locked,
            @Param("configInfobloxId") Long configInfobloxId,
            @Param("configBaasId") Long configBaasId,
            @Param("greenItEnabled") boolean greenItEnabled
    );

    @Modifying
    @Transactional
    @Query(value = """
    UPDATE cloud
    SET api_password_encrypted = pgp_sym_encrypt(:password, :key),
        updated_at = CURRENT_TIMESTAMP
    WHERE id = :id
    """, nativeQuery = true)
    void updatePassword(
            @Param("id") Long id,
            @Param("password") String password,
            @Param("key") String key
    );
}

