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
import org.springframework.dao.DataIntegrityViolationException;
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

    /**
     * What the database or MongoDB refused names its SQL, index and the value that broke
     * it - an email, say. The error says the kind of refusal and nothing of that.
     */
    @Test
    void aStoreRefusalNamesNoValue() {
        DataIntegrityViolationException sql = new DataIntegrityViolationException(
                "could not execute statement [Unique index or primary key violation: \"PUBLIC.UK_EMAIL ON"
                        + " PUBLIC.CUSTOMER(EMAIL) VALUES ( /* 1 */ 'kim@a.com' )\"; SQL statement: insert into"
                        + " customer (email) values (?)]",
                new java.sql.SQLIntegrityConstraintViolationException("Unique index violation 'kim@a.com'", "23505"));
        GraphQLError e = resolve(sql);
        assertThat(e.getErrorType().toString()).isEqualTo("CONFLICT");
        assertThat(e.getExtensions()).containsEntry("status", 409).containsEntry("code", "unique");
        assertThat(e.getMessage()).isEqualTo("A unique value is already taken.");

        DuplicateKeyException mongo = new DuplicateKeyException(
                "Write operation error on server localhost:27017. Write error: WriteError{code=11000, message='E11000"
                        + " duplicate key error collection: club.member index: email dup key: { email: \"kim@a.com\" }'}",
                new IllegalStateException("E11000 duplicate key error ... kim@a.com"));
        assertThat(resolve(mongo).getMessage()).isEqualTo("A unique value is already taken.");
        assertThat(resolve(mongo).getExtensions()).containsEntry("code", "unique");

        DataIntegrityViolationException referenced = new DataIntegrityViolationException(
                "could not execute statement [FK_ORDER_CUSTOMER: PUBLIC.ORDERS FOREIGN KEY(CUSTOMER_ID) (CAST(7 AS BIGINT))]",
                new java.sql.SQLIntegrityConstraintViolationException("Referential integrity 7", "23503"));
        assertThat(resolve(referenced).getMessage()).doesNotContain("FK_ORDER").doesNotContain("7");
        assertThat(resolve(referenced).getExtensions())
                .containsEntry("code", "referenced")
                .containsEntry("status", 409);

        // MyBatis: Spring's JDBC translator's DuplicateKeyException, the SQL its cause.
        DuplicateKeyException mybatis = new DuplicateKeyException(
                "### Error updating database. Cause: Duplicate entry 'kim@a.com' for key 'uk_email' ### SQL: INSERT INTO"
                        + " member (email) VALUES (?)",
                new java.sql.SQLIntegrityConstraintViolationException("Duplicate entry 'kim@a.com'", "23000", 1062));
        assertThat(resolve(mybatis).getMessage()).isEqualTo("A unique value is already taken.");
        assertThat(resolve(mybatis).getExtensions()).containsEntry("code", "unique");

        // A stale version: the ORM's text names the entity and its key.
        org.springframework.dao.OptimisticLockingFailureException stale =
                new org.springframework.dao.OptimisticLockingFailureException("Row was updated or deleted by another"
                        + " transaction (or unsaved-value mapping was incorrect): [io.acme.shop.Customer#5]");
        assertThat(resolve(stale).getMessage())
                .isEqualTo("The row was changed after it was read. Read it again and resend.");
        assertThat(resolve(stale).getExtensions())
                .containsEntry("code", "stale")
                .containsEntry("status", 409);

        // A driver without a SQL state (MongoDB's write error, say): the kind it may be, and no text.
        assertThat(resolve(new DataIntegrityViolationException("Write error ... 'kim@a.com'"))
                        .getExtensions())
                .containsEntry("code", "conflict");
    }

    /** The service's own refusal of a taken identifier says so, as it always did. */
    @Test
    void theServicesOwnDuplicateKeepsItsMessage() {
        GraphQLError e = resolve(new DuplicateKeyException("Customer 5 already exists; update it instead"));
        assertThat(e.getMessage()).isEqualTo("Customer 5 already exists; update it instead");
        assertThat(e.getExtensions()).doesNotContainKey("code");
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
