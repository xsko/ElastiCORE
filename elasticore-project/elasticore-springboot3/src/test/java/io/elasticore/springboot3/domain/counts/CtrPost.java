package io.elasticore.springboot3.domain.counts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class CtrPost {
    @Id
    public Long id;

    @Column(updatable = false)
    public Long items = 0L;
}
