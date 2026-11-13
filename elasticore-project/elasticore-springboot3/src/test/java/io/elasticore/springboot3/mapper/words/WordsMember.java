package io.elasticore.springboot3.mapper.words;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;

@Entity
public class WordsMember {
    @Id
    public Long id;

    public String nickname;

    @OneToMany(mappedBy = "author")
    public List<WordsPost> posts = new ArrayList<WordsPost>();

    @ManyToOne
    public WordsMember mentor;
}
