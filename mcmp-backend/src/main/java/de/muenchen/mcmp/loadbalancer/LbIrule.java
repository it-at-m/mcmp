package de.muenchen.mcmp.loadbalancer;

import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * A shared irule definition (name + script content). The same irule is often attached to many
 * virtual servers, so it's stored once here and linked via {@code lb_virtual_server_has_irules}
 * instead of duplicating the content per virtual server. Uniqueness on (name, content) is
 * enforced in the DB via a hash-based index (uq_lb_irule_name_content) rather than a plain
 * unique constraint, since irule scripts can exceed the btree indexable row size.
 */
@Entity
@Table(name = "lb_irule", schema = "cmp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
public class LbIrule extends AbstractEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "content")
    private String content;
}
