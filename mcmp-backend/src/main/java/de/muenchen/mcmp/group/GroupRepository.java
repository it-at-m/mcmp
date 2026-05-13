package de.muenchen.mcmp.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface GroupRepository extends JpaRepository<Group, Long> {

    Group findBySysId(final String sysId);

    @Query("SELECT g FROM Group g WHERE g.sysId NOT IN :sysIds")
    List<Group> findGroupsNotInSysIds(@Param("sysIds") Set<String> sysIds);
}
