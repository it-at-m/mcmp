package de.muenchen.mcmp.action;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ActionRepository extends JpaRepository<Action, Long> {
    Action findByIdentifier(String identifier);

    Action [] findByAwxConfig_Id(Long id);

    Action [] findBySnowConfig_Id(Long id);
}
