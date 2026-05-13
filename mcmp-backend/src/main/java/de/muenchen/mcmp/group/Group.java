package de.muenchen.mcmp.group;

import de.muenchen.mcmp.appservice.Appservice;
import de.muenchen.mcmp.common.AbstractEntity;
import de.muenchen.mcmp.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
@Table(name = "\"group\"")
@DynamicUpdate
public class Group extends AbstractEntity {

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "sys_id", length = 100, nullable = false, unique = true)
    private String sysId;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "manager_id", nullable = true)
    @ToString.Exclude
    private User manager;

    @OneToMany(mappedBy = "changeGroup")
    @ToString.Exclude
    private Set<Appservice> appservices = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(name = "group_membership",
            joinColumns = @JoinColumn(name = "group_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    @ToString.Exclude
    private Set<User> users = new LinkedHashSet<>();

}