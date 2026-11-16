package io.elasticore.springboot3.mongo;

import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * The numbers of a {@code long @sequence} key in a MongoDB domain, which has
 * no identity column to draw them from: one counter document per collection
 * in {@value #COLLECTION}, raised by one atomically (findAndModify with $inc).
 * The first number is 1, as a database's identity starts.
 *
 * The generated service calls it before it inserts a document, and for each
 * member the document embeds that has no key yet.
 */
public final class MongoSequences {

    /** The collection the counters are kept in, one document per sequence: {_id: name, value: n}. */
    public static final String COLLECTION = "elasticore_sequences";

    private MongoSequences() {}

    /**
     * The next number of the named sequence. Two first calls at once may both
     * try to create the counter; the one that loses reads it again.
     */
    public static long next(MongoOperations mongo, String name) {
        if (mongo == null) throw new IllegalStateException("No MongoOperations to draw sequence " + name + " from");
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("A sequence needs a name");
        DuplicateKeyException lost = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                Document counter = mongo.findAndModify(
                        Query.query(Criteria.where("_id").is(name)),
                        new Update().inc("value", 1L),
                        FindAndModifyOptions.options().returnNew(true).upsert(true),
                        Document.class,
                        COLLECTION);
                Object value = counter == null ? null : counter.get("value");
                if (!(value instanceof Number number)) {
                    throw new IllegalStateException(
                            "Sequence " + name + " in " + COLLECTION + " holds no number: " + value);
                }
                return number.longValue();
            } catch (DuplicateKeyException e) {
                lost = e;
            }
        }
        throw lost;
    }
}
