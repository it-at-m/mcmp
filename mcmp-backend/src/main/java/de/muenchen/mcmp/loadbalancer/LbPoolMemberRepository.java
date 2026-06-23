package de.muenchen.mcmp.loadbalancer;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LbPoolMemberRepository extends JpaRepository<LbPoolMember, Long> {}
