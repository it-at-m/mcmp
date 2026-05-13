package de.muenchen.mcmp.snowConfig;

import de.muenchen.mcmp.action.ActionRepository;
import de.muenchen.mcmp.configuration.EncryptionProperties;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class SnowConfigService {

    private final SnowConfigRepository snowConfigRepository;
    private final ActionRepository actionRepository;
    private final SnowConfigMapper snowConfigMapper;
    private final EncryptionProperties encryptionProperties;

    // get all snow config entries
    public List<SnowConfigDTO> getSnowConfigs() {
        return snowConfigMapper.toDTOs(snowConfigRepository.findAll());
    }

//    // get one snow config entry
//    public List<SnowConfigDTO> getSnowConfigById(Long Id)
//    {
//        return snowConfigMapper.toDTOs(repository.findBySnowConfigId(Id));
//    }

    // create snow entry
    public void createSnowConfigEntry(final SnowConfigDTO snowConfigDTO) {
        //repository.save(snowConfigMapper.toEntity(snowConfigDTO));
        snowConfigRepository.insertConfig(
                snowConfigDTO.apiDescription(),
                snowConfigDTO.apiClientAuthUrl(),
                snowConfigDTO.apiClientId(),
                snowConfigDTO.apiClientSecret(),
                encryptionProperties.getPassphrase(),
                snowConfigDTO.apiEndpoint(),
                snowConfigDTO.enabled(),
                snowConfigDTO.proxy(),
                snowConfigDTO.useProxy()
        );
    }

    // update snow entry
    public void updateSnowConfigEntry(final SnowConfigDTO snowConfigDTO)
    {
        if (snowConfigDTO.apiClientSecret() != null){
            snowConfigRepository.updateSecret(snowConfigDTO.id(), snowConfigDTO.apiClientSecret(), encryptionProperties.getPassphrase());
        }
        SnowConfig snowConfigToUpdate = snowConfigRepository.findById(snowConfigDTO.id()).orElseThrow(
                () -> new IllegalArgumentException("SNOW-Konfiguration mit ID " + snowConfigDTO.id() + " existiert nicht."));
        SnowConfig updatedSnowConfig =  snowConfigMapper.toEntity(snowConfigDTO);
        snowConfigToUpdate.setApiDescription(updatedSnowConfig.getApiDescription());
        snowConfigToUpdate.setApiEndpoint(updatedSnowConfig.getApiEndpoint());
        snowConfigToUpdate.setEnabled(updatedSnowConfig.isEnabled());
        snowConfigToUpdate.setProxy(updatedSnowConfig.getProxy());
        snowConfigToUpdate.setApiClientAuthUrl(updatedSnowConfig.getApiClientAuthUrl());
        snowConfigToUpdate.setApiClientId(updatedSnowConfig.getApiClientId());
        snowConfigToUpdate.setUseProxy(updatedSnowConfig.isUseProxy());

        if (!snowConfigToUpdate.isEnabled() && actionRepository.findBySnowConfig_Id(snowConfigToUpdate.getId()).length != 0){
            snowConfigToUpdate.setEnabled(true);
            throw new IllegalArgumentException("Can not be disabled because it is linked to an action.");
        }

        snowConfigRepository.save(snowConfigToUpdate);
    }

    // delete snow entry
    public void  deleteSnowConfigEntry(Long id)
    {
        if (actionRepository.findBySnowConfig_Id(id).length == 0){
            snowConfigRepository.deleteById(id);
        }
        else {
            throw new IllegalArgumentException("Can not be deleted because it is linked to an action.");
        }
    }
}
