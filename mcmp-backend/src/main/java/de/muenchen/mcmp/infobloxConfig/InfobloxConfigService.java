package de.muenchen.mcmp.infobloxConfig;

import de.muenchen.mcmp.configuration.EncryptionProperties;
import lombok.AllArgsConstructor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class InfobloxConfigService {

    private final InfobloxConfigRepository repository;
    private final InfobloxConfigMapper infobloxConfigMapper;
    private final EncryptionProperties encryptionProperties;

    // get all infoblox config entries
    public List<InfobloxConfigDTO> getInfobloxConfigs() {
        return infobloxConfigMapper.toDTOs(repository.findAll());
    }

    // create infoblox entry
    public void createInfobloxConfigEntry(final InfobloxConfigDTO infobloxConfigDTO) {
        // repository.save(infobloxConfigMapper.toEntity(infobloxConfigDTO));
        repository.insertConfig(
                infobloxConfigDTO.apiDescription(),
                infobloxConfigDTO.apiUsername(),
                infobloxConfigDTO.apiPassword(),
                encryptionProperties.getPassphrase(),
                infobloxConfigDTO.apiEndpoint()
        );
    }

    // update infoblox entry
    public void updateInfobloxConfigEntry(final InfobloxConfigDTO infobloxConfigDTO) {
        if (infobloxConfigDTO.apiPassword() != null){
            repository.updatePassword(infobloxConfigDTO.id(), infobloxConfigDTO.apiPassword(), encryptionProperties.getPassphrase());
        }

        final InfobloxConfig infobloxConfigToUpdate = repository.findById(infobloxConfigDTO.id()).orElseThrow(
                () -> new IllegalArgumentException("Infoblox-Konfiguration mit ID " + infobloxConfigDTO.id() + " existiert nicht."));
        final InfobloxConfig updatedInfobloxConfig = infobloxConfigMapper.toEntity(infobloxConfigDTO);
        infobloxConfigToUpdate.setApiDescription(updatedInfobloxConfig.getApiDescription());
        infobloxConfigToUpdate.setApiUsername(updatedInfobloxConfig.getApiUsername());
        infobloxConfigToUpdate.setApiEndpoint(updatedInfobloxConfig.getApiEndpoint());
        repository.save(infobloxConfigToUpdate);
    }

    // delete infoblox entry
    public void deleteInfobloxConfigEntry(Long id) {
        repository.deleteById(id);
    }


    public Optional<InfobloxConfigDTO> findConfigByApiEndpoint(String apiEndpoint) {
        try {
            URI uri = new URI(apiEndpoint);
            String hostname = uri.getScheme() + "://" + uri.getHost();
            Optional<InfobloxConfig> config = repository.findByHostname(hostname);
            return config.map(infobloxConfigMapper::toDTO);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Ungültiger API-Endpoint: " + apiEndpoint, e);
        }
    }

    public List<InfobloxConfig> findAll() {
        return repository.findAll();
    }

    public InfobloxConfigWithDecryptedPassword findByIdDecrypted(final Long id) {
        return repository.findByIdDecrypted(id, encryptionProperties.getPassphrase());
    }

    public InfobloxConfigWithDecryptedPassword findDefaultInfoblox() {
        return repository.findDefaultInfoblox(encryptionProperties.getPassphrase());
    }
}
