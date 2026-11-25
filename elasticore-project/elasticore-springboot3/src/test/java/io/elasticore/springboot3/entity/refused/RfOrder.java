package io.elasticore.springboot3.entity.refused;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Version;

import java.util.ArrayList;
import java.util.List;

/** A root with members it cascades to, as a generated aggregate root holds them. */
@Entity
public class RfOrder {
    @Id
    public Long id;

    public String memo;

    @Version
    public Long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<RfLine> lines = new ArrayList<>();
}
