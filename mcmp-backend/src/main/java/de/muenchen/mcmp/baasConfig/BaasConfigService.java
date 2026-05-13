package de.muenchen.mcmp.baasConfig;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class BaasConfigService {

    private final BaasConfigRepository baasConfigRepository;
    private final BaasConfigMapper baasConfigMapper;

    // get all baas config entries
    public List<BaasConfigDTO> getBaasConfigs() {
        return baasConfigMapper.toDTOs(baasConfigRepository.findAll());
    }

    // create baas config entry
    public BaasConfigDTO createBaasConfigEntry(final BaasConfigDTO baasConfigDTO) {
        return baasConfigMapper.toDTO(baasConfigRepository.save(baasConfigMapper.toEntity(baasConfigDTO)));
    }

    // update baas config entry
    public BaasConfigDTO updateBaasConfigEntry(final BaasConfigDTO baasConfigDTO) {
        BaasConfig baasConfigToUpdate = baasConfigRepository.findById(baasConfigDTO.id()).orElseThrow(
                () -> new IllegalArgumentException("Baas-Konfiguration mit ID " + baasConfigDTO.id() + " existiert nicht."));
        BaasConfig updatedBaasConfig =  baasConfigMapper.toEntity(baasConfigDTO);
        updatedBaasConfig.setVersion(baasConfigToUpdate.getVersion());
        updatedBaasConfig.setId(baasConfigToUpdate.getId());

        return baasConfigMapper.toDTO(baasConfigRepository.save(updatedBaasConfig));
    }

    // delete baas config entry
    public void deleteBaasConfigEntry(Long id) {
        baasConfigRepository.deleteById(id);
    }
}
