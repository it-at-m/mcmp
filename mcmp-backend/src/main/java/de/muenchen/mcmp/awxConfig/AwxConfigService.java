package de.muenchen.mcmp.awxConfig;

import de.muenchen.mcmp.action.ActionRepository;
import de.muenchen.mcmp.configuration.EncryptionProperties;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class AwxConfigService {

    private final AwxConfigRepository awxConfigRepository;
    private final ActionRepository actionRepository;
    private final AwxConfigMapper awxConfigMapper;
    private final EncryptionProperties encryptionProperties;

    // get all awx config entries
    public List<AwxConfigDTO> getAwxConfigs() {
        return awxConfigMapper.toDTOs(awxConfigRepository.findAll());
    }

//    // get one awx config entry
//    public List<AwxConfigDTO> getAwxConfigById(Long Id)
//    {
//        return awxConfigMapper.toDTOs(repository.findByAwxConfigId(Id));
//    }

    public AwxConfigDTO getAwxDecrypted(final Long id) {
        final AwxConfig awxConfig = awxConfigRepository.findByIdDecrypted(id, encryptionProperties.getPassphrase());
        return awxConfigMapper.toDTODecryptedPassword(awxConfig);
    }

    // create awx entry
    public void createAwxConfigEntry(final AwxConfigDTO awxConfigDTO) {
        //awxConfigRepository.save(awxConfigMapper.toEntity(awxConfigDTO));
        awxConfigRepository.insertConfig(
                awxConfigDTO.apiDescription(),
                awxConfigDTO.apiUsername(),
                awxConfigDTO.apiPassword(),
                encryptionProperties.getPassphrase(),
                awxConfigDTO.apiEndpoint(),
                awxConfigDTO.enabled()
        );
    }

    // update awx entry
    public void updateAwxConfigEntry(final AwxConfigDTO awxConfigDTO)
    {
        if (awxConfigDTO.apiPassword() != null){
            awxConfigRepository.updatePassword(awxConfigDTO.id(), awxConfigDTO.apiPassword(), encryptionProperties.getPassphrase());
        }
        AwxConfig awxConfigToUpdate = awxConfigRepository.findById(awxConfigDTO.id()).orElseThrow(
                () -> new IllegalArgumentException("AWX-Konfiguration mit ID " + awxConfigDTO.id() + " existiert nicht."));
        AwxConfig updatedAwxConfig =  awxConfigMapper.toEntity(awxConfigDTO);
        updatedAwxConfig.setVersion(awxConfigToUpdate.getVersion());
        updatedAwxConfig.setId(awxConfigToUpdate.getId());
        updatedAwxConfig.setApiPasswordEncrypted(awxConfigToUpdate.getApiPasswordEncrypted());

        // Can be disabled for maintenance
//        if (awxConfigToUpdate.isEnabled() && actionRepository.findByAwxConfig_Id(awxConfigToUpdate.getId()).length != 0){
//            updatedAwxConfig.setEnabled(awxConfigToUpdate.isEnabled());
//            throw new IllegalArgumentException("Can not be disabled because it is linked to an action.");
//        }

        awxConfigRepository.save(updatedAwxConfig);
    }

    // delete awx entry
    public void  deleteAwxConfigEntry(Long id)
    {

        if (actionRepository.findByAwxConfig_Id(id).length == 0){
            awxConfigRepository.deleteById(id);
        }
        else {
            throw new IllegalArgumentException("Can not be deleted because it is linked to an action.");
        }
    }
}
