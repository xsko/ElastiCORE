package io.elasticore.springboot3.mongo;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoSequencesTest {

    /** The findAndModify calls a fake MongoOperations received. */
    private final List<Object[]> calls = new ArrayList<Object[]>();

    /**
     * A MongoOperations whose findAndModify(query, update, options, type, collection)
     * answers each call with the next of the given answers - a Document to return,
     * or a RuntimeException to throw. A proxy rather than a mock: every call is recorded as made.
     */
    private MongoOperations fake(Object... answers) {
        final Iterator<Object> next = Arrays.asList(answers).iterator();
        return (MongoOperations) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {MongoOperations.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("findAndModify") || args.length != 5) {
                        throw new UnsupportedOperationException(method.toString());
                    }
                    calls.add(args);
                    Object answer = next.next();
                    if (answer instanceof RuntimeException) throw (RuntimeException) answer;
                    return answer;
                });
    }

    @Test
    void theCounterIsRaisedByOneAndCreatedWhenMissing() {
        assertEquals(5L, MongoSequences.next(fake(new Document("value", 5)), "purchase_order"));
        assertEquals(1, calls.size());
        Object[] call = calls.get(0);
        assertEquals(new Document("_id", "purchase_order"), ((Query) call[0]).getQueryObject());
        assertEquals(new Document("$inc", new Document("value", 1L)), ((Update) call[1]).getUpdateObject());
        assertTrue(((FindAndModifyOptions) call[2]).isReturnNew());
        assertTrue(((FindAndModifyOptions) call[2]).isUpsert());
        assertEquals(Document.class, call[3]);
        assertEquals(MongoSequences.COLLECTION, call[4]);
    }

    @Test
    void theOneThatLostTheFirstUpsertReadsAgain() {
        assertEquals(2L, MongoSequences.next(fake(new DuplicateKeyException("raced"), new Document("value", 2L)), "x"));
        assertEquals(2, calls.size());
    }

    @Test
    void aCounterThatKeepsFailingIsSaid() {
        DuplicateKeyException raced = new DuplicateKeyException("raced");
        MongoOperations mongo = fake(raced, raced, raced, new Document("value", 9));
        assertSame(raced, assertThrows(DuplicateKeyException.class, () -> MongoSequences.next(mongo, "x")));
        assertEquals(3, calls.size());
    }

    @Test
    void aCounterWithoutANumberIsRefused() {
        assertThrows(IllegalStateException.class, () -> MongoSequences.next(fake(new Document("value", "one")), "x"));
        assertThrows(IllegalStateException.class, () -> MongoSequences.next(fake((Object) null), "x"));
    }

    @Test
    void aSequenceNeedsMongoAndAName() {
        assertThrows(IllegalStateException.class, () -> MongoSequences.next(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> MongoSequences.next(fake(), ""));
        assertThrows(IllegalArgumentException.class, () -> MongoSequences.next(fake(), null));
        assertEquals(0, calls.size());
    }
}
