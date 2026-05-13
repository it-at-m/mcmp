package de.muenchen.mcmp.temporaryPrivileges;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
@Slf4j
public class TemporaryPrivilegeService {

    private final TemporaryPrivilegeRepository repository;

    public List<TemporaryPrivilege> findAll() {
        return repository.findAll();
    }

    public void deleteAll(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            log.debug("No IDs provided for deletion");
            return;
        }

        repository.deleteAllById(ids);
        log.info("Deleted {} temporary privileges", ids.size());
    }

    public void deleteAll() {
        repository.deleteAll();
        log.debug("Deleted all temporary privileges");
    }

    public TemporaryPrivilege save(TemporaryPrivilege temporaryPrivilege) {
        if (temporaryPrivilege == null) {
            log.debug("Cannot save null temporary privilege");
            return null;
        }

        TemporaryPrivilege saved = repository.save(temporaryPrivilege);
        log.debug("Saved temporary privilege with ID {}", saved.getId());
        return saved;
    }
}
