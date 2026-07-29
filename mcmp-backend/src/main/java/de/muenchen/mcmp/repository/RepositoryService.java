package de.muenchen.mcmp.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RepositoryService {

    private final RepositoryRepository repositoryRepository;
    private final RepositoryMapper repositoryMapper;

    @Transactional(readOnly = true)
    public List<Repository> findAll() {
        return repositoryRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<RepositoryDTO> findByServerId(final Long serverId) {
        return repositoryMapper.toDTOs(repositoryRepository.findAllByServersId(serverId));
    }

    @Transactional(readOnly = true)
    public Optional<Repository> findById(final Long id) {
        return repositoryRepository.findById(id);
    }

    @Transactional
    public Repository save(final Repository repository) {
        return repositoryRepository.save(repository);
    }

    @Transactional
    public void deleteById(final Long id) {
        repositoryRepository.deleteById(id);
    }
}