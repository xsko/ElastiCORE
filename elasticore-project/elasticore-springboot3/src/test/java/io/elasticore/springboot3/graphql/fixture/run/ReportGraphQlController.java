package io.elasticore.springboot3.graphql.fixture.run;

import io.elasticore.springboot3.graphql.GraphQlDomain;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.Optional;

/** A top-level generated-style controller, as the schema writer finds one among compiled classes. */
@Controller
@GraphQlDomain("report")
public class ReportGraphQlController {

    /** A value answered: one property. */
    public static class Line {
        private String text;

        public String getText() {
            return text;
        }
    }

    @QueryMapping
    public Optional<Line> report(@Argument("year") int year) {
        return Optional.empty();
    }

    @QueryMapping
    public Map<String, Object> reportExtras(@Argument("keys") String[] keys) {
        return null;
    }
}
