package io.elasticore.springboot3.mapper.words;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class WordsPost {
    @Id
    public Long id;

    public String title;

    public String body;

    @ManyToOne
    public WordsMember author;
}
