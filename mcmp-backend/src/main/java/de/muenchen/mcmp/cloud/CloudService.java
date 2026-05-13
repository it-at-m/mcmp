package de.muenchen.mcmp.cloud;

import de.muenchen.mcmp.configuration.EncryptionProperties;
import de.muenchen.mcmp.types.CloudType;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class CloudService {

    private final CloudRepository cloudRepository;
    private final CloudMapper cloudMapper;
    private final EncryptionProperties encryptionProperties;

    // get all cloud config entries
    public List<CloudDTO> getClouds() {
        return cloudMapper.toDTOs(cloudRepository.findAll());
    }

    // create cloud entry
    public void createCloudEntry(final CloudDTO cloudDTO) {
        cloudRepository.insertConfig(
                cloudDTO.name(),
                cloudDTO.fqdn(),
                cloudDTO.serverGui(),
                cloudDTO.cloudType(),
                cloudDTO.apiDescription(),
                cloudDTO.apiUsername(),
                cloudDTO.apiPassword(),
                encryptionProperties.getPassphrase(),
                cloudDTO.apiEndpoint(),
                cloudDTO.enabled(),
                cloudDTO.locked(),
                cloudDTO.configInfobloxId(),
                cloudDTO.configBaasId(),
                cloudDTO.greenItEnabled()
        );
    }

    // update cloud entry
    public void updateCloudEntry(final CloudDTO cloudDTO)
    {
        if (cloudDTO.apiPassword() != null){
            cloudRepository.updatePassword(cloudDTO.id(), cloudDTO.apiPassword(), encryptionProperties.getPassphrase());
        }
        Cloud cloudToUpdate = cloudRepository.findById(cloudDTO.id()).orElseThrow(
                () -> new IllegalArgumentException("AWX-Konfiguration mit ID " + cloudDTO.id() + " existiert nicht."));
        Cloud updatedCloud =  cloudMapper.toEntity(cloudDTO);
        updatedCloud.setVersion(cloudToUpdate.getVersion());
        updatedCloud.setId(cloudToUpdate.getId());
        updatedCloud.setApiPasswordEncrypted(cloudToUpdate.getApiPasswordEncrypted());

        cloudRepository.save(updatedCloud);
    }

    // delete cloud entry
    public void  deleteCloudEntry(Long id)
    {
            cloudRepository.deleteById(id);
    }

    public Cloud findById(Long id) {
        return cloudRepository.findById(id).orElse(null);
    }

    public Cloud findByApiEndpoint(final String apiEndpoint) {
        return cloudRepository.findByApiEndpoint(apiEndpoint).orElse(null);
    }
}
