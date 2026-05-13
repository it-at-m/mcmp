package de.muenchen.mcmp.changelog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChangelogRepository extends JpaRepository<Changelog, Long> {

    Page<Changelog> findAllByIsPublishedTrue(Pageable pageable);
}
