package io.elasticore.springboot3.domain.counts;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class CtrItem {
    @Id
    public Long id;

    public Long postId;
}
