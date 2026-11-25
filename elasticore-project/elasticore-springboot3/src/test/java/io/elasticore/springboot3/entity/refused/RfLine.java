package io.elasticore.springboot3.entity.refused;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/** A member of RfOrder. */
@Entity
public class RfLine {
    @Id
    public Long id;

    public int qty;

    @ManyToOne
    public RfOrder order;
}
