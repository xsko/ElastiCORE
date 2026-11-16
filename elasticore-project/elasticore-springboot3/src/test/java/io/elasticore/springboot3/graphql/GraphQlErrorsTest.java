package io.elasticore.springboot3.graphql;

import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import io.elasticore.springboot3.http.PortCallException;
import io.elasticore.springboot3.security.ForbiddenException;
import io.elasticore.springboot3.security.NotSignedInException;
import io.elasticore.springboot3.validation.BadRequestException;
import io.elasticore.springboot3.validation.InvariantViolationException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Each refusal of the generated services, as the GraphQL error the REST status stands for. */
class GraphQlErrorsTest {

    private final GraphQlErrors errors = new GraphQlErrors();
    private final DataFetchingEnvironment env = Mockito.mock(DataFetchingEnvironment.class, Mockito.RETURNS_DEEP_STUBS);

    private GraphQLError resolve(Throwable ex) {
        return errors.resolveToSingleError(ex, env);
    }

    @Test
    void eachRefusalHasItsStatus() {
        assertThat(resolve(new ForbiddenException("member.read", "not yours")).getExtensions())
                .containsEntry("status", 403)
                .containsEntry("code", "member.read");
        assertThat(resolve(new NotSignedInException("sign in")).getErrorType().toString())
                .isEqualTo("UNAUTHORIZED");
        assertThat(resolve(new BadRequestException("bad")).getExtensions()).containsEntry("status", 400);
        assertThat(resolve(new DuplicateKeyException("taken")).getErrorType().toString())
                .isEqualTo("CONFLICT");
        assertThat(resolve(new PortCallException("down")).getExtensions()).containsEntry("status", 502);
    }

    @Test
    void anInvariantIsAConflictANotFoundOrUnprocessable() {
        assertThat(resolve(new InvariantViolationException("state.ship", "cannot ship"))
                        .getExtensions())
                .containsEntry("status", 409);
        assertThat(resolve(new InvariantViolationException("reference.customer", "no customer"))
                        .getExtensions())
                .containsEntry("status", 404);
        assertThat(resolve(new InvariantViolationException("price.positive", "a price"))
                        .getExtensions())
                .containsEntry("status", 422);
        assertThat(resolve(new InvariantViolationException("no code"))
                        .getErrorType()
                        .toString())
                .isEqualTo("UNPROCESSABLE");
    }

    static class Form {
        @NotNull
        String name;
    }

    @Test
    void aBrokenConstraintSaysWhichProperty() {
        var violations =
                Validation.buildDefaultValidatorFactory().getValidator().validate(new Form());
        GraphQLError e = resolve(new ConstraintViolationException("invalid", violations));

        assertThat(e.getErrorType().toString()).isEqualTo("BAD_REQUEST");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list =
                (List<Map<String, Object>>) e.getExtensions().get("errors");
        assertThat(list).hasSize(1);
        assertThat(list.get(0)).containsEntry("field", "name");
    }

    @Test
    void anythingElseIsLeftToTheNextResolver() {
        assertThat(resolve(new IllegalStateException("boom"))).isNull();
    }

    @Test
    void aRefusalWithoutAMessageIsNamedByItsKind() {
        assertThat(resolve(new BadRequestException(null)).getMessage()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void theConfigurationTurnsFieldBindingOn() throws Exception {
        org.springframework.graphql.data.method.annotation.support.AnnotatedControllerConfigurer configurer =
                new org.springframework.graphql.data.method.annotation.support.AnnotatedControllerConfigurer();
        Object same = ElastiCoreGraphQlConfiguration.elastiCoreGraphQlFieldBinding()
                .postProcessBeforeInitialization(configurer, "annotatedControllerConfigurer");
        assertThat(same).isSameAs(configurer);
        java.lang.reflect.Method read = null;
        for (Class<?> c = configurer.getClass(); c != null && read == null; c = c.getSuperclass()) {
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (m.getName().equals("isFallBackOnDirectFieldAccess")) read = m;
            }
        }
        assertThat(read).isNotNull();
        read.setAccessible(true);
        assertThat(read.invoke(configurer)).isEqualTo(true);
        // Any other bean is passed through.
        Object other = new Object();
        assertThat(ElastiCoreGraphQlConfiguration.elastiCoreGraphQlFieldBinding()
                        .postProcessBeforeInitialization(other, "other"))
                .isSameAs(other);
        // And a configurer without the switch is left as it is.
        ElastiCoreGraphQlConfiguration.fallBackOnFields(new Object());
    }
}
