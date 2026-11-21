package io.elasticore.springboot3.security;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** @signin(refresh=password): what the filter the generator subclasses does to a session. */
public class PasswordRefreshFilterTest {

    @AfterEach
    void unbind() {
        RequestContextHolder.resetRequestAttributes();
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clear();
    }

    /** A filter whose row is whatever the test says, counting how often it was read. */
    private static final class Rows extends PasswordRefreshFilter {
        final AtomicInteger reads = new AtomicInteger();
        Supplier<Optional<Row>> row;
        Object idSeen;

        Rows(Supplier<Optional<Row>> row) {
            this.row = row;
        }

        @Override
        protected Optional<Row> signedInRow() {
            reads.incrementAndGet();
            idSeen = new SessionCurrentUser().id();
            return row.get();
        }
    }

    private static Optional<PasswordRefreshFilter.Row> row(String hash, String... roles) {
        return Optional.of(PasswordRefreshFilter.row(java.util.Arrays.asList(roles), hash));
    }

    private static MockHttpServletRequest signedIn(Object id, String... roles) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        SessionCurrentUser.signIn(request, id, roles);
        return request;
    }

    /** The next request of the same session. */
    private static MockHttpServletRequest next(MockHttpServletRequest before) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(before.getSession(false));
        return request;
    }

    private static MockHttpServletResponse run(PasswordRefreshFilter filter, MockHttpServletRequest request)
            throws Exception {
        return run(filter, request, new MockFilterChain());
    }

    private static MockHttpServletResponse run(
            PasswordRefreshFilter filter, MockHttpServletRequest request, MockFilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private static Object stampOf(MockHttpServletRequest request) {
        return request.getSession(false) == null
                ? null
                : request.getSession(false).getAttribute(PasswordRefreshFilter.STAMP);
    }

    @Test
    void theStampIsTheFirstSixteenHexOfTheSha256OfTheHash() throws Exception {
        String hash = "pbkdf2$310000$c2FsdA$aGFzaA";
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(hash.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) hex.append(String.format("%02x", b & 0xff));
        assertEquals(hex.substring(0, 16), PasswordRefreshFilter.stamp(hash));
        assertEquals(16, PasswordRefreshFilter.stamp(hash).length());
        assertNull(PasswordRefreshFilter.stamp(null));
    }

    @Test
    void aSessionWithoutAStampTakesTheRowsOnItsFirstRequestInTheOneRead() throws Exception {
        MockHttpServletRequest request = signedIn(7L, "USER");
        Rows filter = new Rows(() -> row("hash-1", "USER"));

        run(filter, request);

        assertEquals(PasswordRefreshFilter.stamp("hash-1"), stampOf(request));
        assertEquals(1, filter.reads.get(), "the stamp is read in the read the roles are");
        assertEquals(7L, filter.idSeen);
        // The hash itself is nowhere in the session.
        for (String name : Collections.list(request.getSession(false).getAttributeNames())) {
            assertFalse(
                    String.valueOf(request.getSession(false).getAttribute(name)).contains("hash-1"), name);
        }
    }

    @Test
    void theSamePasswordKeepsTheSessionAndADifferentOneEndsIt() throws Exception {
        MockHttpServletRequest first = signedIn(7L, "USER");
        Rows filter = new Rows(() -> row("hash-1", "USER"));
        run(filter, first);

        MockHttpServletRequest second = next(first);
        run(filter, second);
        assertNotNull(second.getSession(false));
        assertEquals(1 + 1, filter.reads.get(), "one read per signed-in request");

        filter.row = () -> row("hash-2", "USER");
        MockHttpServletRequest third = next(second);
        MockHttpSession session = (MockHttpSession) third.getSession(false);
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res) {
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes((HttpServletRequest) req));
                assertNull(new SessionCurrentUser().id(), "the rest of the request sees a visitor");
            }
        };
        run(filter, third, chain);
        assertTrue(session.isInvalid(), "a session opened before the change ends");
        assertNull(third.getSession(false));
    }

    @Test
    void noPasswordBeforeAndOneSetIsNotAChange() throws Exception {
        MockHttpServletRequest first = signedIn(7L, "USER");
        Rows filter = new Rows(() -> row(null, "USER"));
        run(filter, first);
        assertEquals("-", stampOf(first), "stamped: the row had no password");

        filter.row = () -> row("hash-1", "USER");
        MockHttpServletRequest second = next(first);
        run(filter, second);
        assertNotNull(second.getSession(false), "the session stays");
        assertEquals(PasswordRefreshFilter.stamp("hash-1"), stampOf(second));
    }

    @Test
    void aPasswordRemovedEndsTheSession() throws Exception {
        MockHttpServletRequest first = signedIn(7L, "USER");
        Rows filter = new Rows(() -> row("hash-1", "USER"));
        run(filter, first);
        filter.row = () -> row(null, "USER");
        MockHttpServletRequest second = next(first);
        run(filter, second);
        assertNull(second.getSession(false));
    }

    @Test
    void aRowThatIsGoneEndsTheSessionAndARoleIsTakenAsWithRefreshRequest() throws Exception {
        MockHttpServletRequest first = signedIn(7L, "USER");
        Rows filter = new Rows(() -> row("hash-1", "ADMIN"));
        run(filter, first);
        assertEquals(
                new LinkedHashSet<String>(Collections.singleton("ADMIN")),
                first.getSession(false).getAttribute(SessionCurrentUser.ROLES));

        filter.row = Optional::empty;
        MockHttpServletRequest second = next(first);
        run(filter, second);
        assertNull(second.getSession(false));
    }

    /** What a write of the row's password through the generated update does, as the service calls it. */
    private static MockFilterChain writing(final Object id, final String hash, final int status) {
        return new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res) {
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes((HttpServletRequest) req));
                PasswordRefreshFilter.written(id, hash);
                ((HttpServletResponse) res).setStatus(status);
                RequestContextHolder.resetRequestAttributes();
            }
        };
    }

    @Test
    void theSessionThatChangesItsOwnPasswordTakesTheNewStamp() throws Exception {
        MockHttpServletRequest first = signedIn(7L, "USER");
        Rows filter = new Rows(() -> row("hash-1", "USER"));
        run(filter, first);

        MockHttpServletRequest change = next(first);
        run(filter, change, writing(7L, "hash-2", 200));
        assertEquals(PasswordRefreshFilter.stamp("hash-2"), stampOf(change));
        assertEquals(2, filter.reads.get(), "no second read for the new stamp");

        filter.row = () -> row("hash-2", "USER");
        MockHttpServletRequest after = next(change);
        run(filter, after);
        assertNotNull(after.getSession(false), "it keeps going");
    }

    @Test
    void aWriteAnsweredWithAnErrorGivesNoNewStamp() throws Exception {
        MockHttpServletRequest first = signedIn(7L, "USER");
        Rows filter = new Rows(() -> row("hash-1", "USER"));
        run(filter, first);
        MockHttpServletRequest change = next(first);
        run(filter, change, writing(7L, "hash-2", 409));
        assertEquals(PasswordRefreshFilter.stamp("hash-1"), stampOf(change));
    }

    @Test
    void anotherRowsPasswordLeavesThisSessionsStamp() throws Exception {
        MockHttpServletRequest admin = signedIn(1L, "ADMIN");
        Rows filter = new Rows(() -> row("admin-hash", "ADMIN"));
        run(filter, admin);
        MockHttpServletRequest change = next(admin);
        run(filter, change, writing(7L, "member-hash-2", 200));
        assertEquals(PasswordRefreshFilter.stamp("admin-hash"), stampOf(change));
        assertNotNull(change.getSession(false));
    }

    @Test
    void aKeyComparesAsTextWhateverItsType() throws Exception {
        MockHttpServletRequest first = signedIn(7L, "USER");
        Rows filter = new Rows(() -> row("hash-1", "USER"));
        run(filter, first);
        MockHttpServletRequest change = next(first);
        run(filter, change, writing(Integer.valueOf(7), "hash-2", 200));
        assertEquals(PasswordRefreshFilter.stamp("hash-2"), stampOf(change));
    }

    @Test
    void aSessionTheRequestSignedInIsStampedAtItsEnd() throws Exception {
        Rows filter = new Rows(() -> row("hash-1", "USER"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockFilterChain signIn = new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res) {
                SessionCurrentUser.signIn((HttpServletRequest) req, 7L, "USER");
            }
        };
        run(filter, request, signIn);
        assertEquals(PasswordRefreshFilter.stamp("hash-1"), stampOf(request));
        assertEquals(1, filter.reads.get());
        assertEquals(7L, filter.idSeen, "read by the key the new session holds");
    }

    @Test
    void aVisitorIsNotRead() throws Exception {
        Rows filter = new Rows(() -> row("hash-1", "USER"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        run(filter, request);
        assertNull(request.getSession(false));
        assertEquals(0, filter.reads.get());
    }

    @Test
    void inATransactionTheNewStampWaitsForTheCommit() {
        MockHttpServletRequest request = signedIn(7L, "USER");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        TransactionSynchronizationManager.initSynchronization();
        PasswordRefreshFilter.written(7L, "hash-2");
        assertNull(request.getAttribute(PasswordRefreshFilter.WRITTEN), "nothing before the commit");

        assertNull(stampOf(request));

        TransactionSynchronizationUtils.triggerAfterCommit();
        @SuppressWarnings("unchecked")
        Map<String, String> written = (Map<String, String>) request.getAttribute(PasswordRefreshFilter.WRITTEN);
        assertEquals(PasswordRefreshFilter.stamp("hash-2"), written.get("7"));
        // Committed: the session holds the new stamp at once, before the answer is written, so a
        // request it sends as soon as the answer arrives meets the new one.
        assertEquals(PasswordRefreshFilter.stamp("hash-2"), stampOf(request));
    }

    @Test
    void aCommitOfAnotherRowsPasswordLeavesThisSessionsStamp() {
        MockHttpServletRequest request = signedIn(1L, "ADMIN");
        request.getSession(false).setAttribute(PasswordRefreshFilter.STAMP, "admin-stamp");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        TransactionSynchronizationManager.initSynchronization();
        PasswordRefreshFilter.written(7L, "member-hash");
        TransactionSynchronizationUtils.triggerAfterCommit();
        assertEquals("admin-stamp", stampOf(request));
    }

    @Test
    void aSessionAnotherRequestEndedMeanwhileIsAVisitorsNotAnError() throws Exception {
        MockHttpServletRequest first = signedIn(7L, "USER");
        Rows filter = new Rows(() -> row("hash-1", "USER"));
        run(filter, first);

        // Ended before this request reads it.
        MockHttpServletRequest ended = next(first);
        ended.getSession(false).invalidate();
        run(filter, ended);
        assertEquals(1, filter.reads.get(), "an ended session is not read");

        // Ended while this request reads the row.
        MockHttpServletRequest second = signedIn(8L, "USER");
        run(filter, second);
        MockHttpServletRequest during = next(second);
        filter.row = () -> {
            during.getSession(false).invalidate();
            return row("hash-1", "USER");
        };
        run(filter, during);
        assertNull(during.getSession(false));
    }

    @Test
    void anyErrorStatusGivesNoNewStamp() throws Exception {
        MockHttpServletRequest first = signedIn(7L, "USER");
        Rows filter = new Rows(() -> row("hash-1", "USER"));
        run(filter, first);
        MockHttpServletRequest change = next(first);
        run(filter, change, writing(7L, "hash-2", 400));
        assertEquals(PasswordRefreshFilter.stamp("hash-1"), stampOf(change));
        MockHttpServletRequest redirected = next(change);
        run(filter, redirected, writing(7L, "hash-3", 399));
        assertEquals(PasswordRefreshFilter.stamp("hash-3"), stampOf(redirected));
    }

    @Test
    void aPasswordWrittenWithNoKeyIsNotNoted() {
        MockHttpServletRequest request = signedIn(7L, "USER");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        PasswordRefreshFilter.written(null, "hash-2");
        assertNull(request.getAttribute(PasswordRefreshFilter.WRITTEN));
    }

    @Test
    void aReadThatFailsAfterTheAnswerLeavesTheSessionToBeStampedNextTime() throws Exception {
        Rows filter = new Rows(() -> {
            throw new IllegalStateException("the database is gone");
        });
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockFilterChain signIn = new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res) {
                SessionCurrentUser.signIn((HttpServletRequest) req, 7L, "USER");
            }
        };
        run(filter, request, signIn);
        assertNotNull(request.getSession(false), "the sign-in stands");
        assertNull(stampOf(request));

        filter.row = () -> row("hash-1", "USER");
        MockHttpServletRequest next = next(request);
        run(filter, next);
        assertEquals(PasswordRefreshFilter.stamp("hash-1"), stampOf(next));
    }

    @Test
    void aSubclassThatAnswersNullIsAGoneRow() throws Exception {
        MockHttpServletRequest request = signedIn(7L, "USER");
        run(new Rows(() -> null), request);
        assertNull(request.getSession(false));
    }

    @Test
    void theRowKeepsItsRolesWithoutNulls() {
        PasswordRefreshFilter.Row row = PasswordRefreshFilter.row(java.util.Arrays.asList("USER", null), "hash-1");
        assertEquals(Collections.singleton("USER"), row.roles());
        assertEquals(PasswordRefreshFilter.stamp("hash-1"), row.stamp());
        assertEquals(
                Collections.emptySet(), PasswordRefreshFilter.row(null, null).roles());
        assertNull(PasswordRefreshFilter.row(null, null).stamp());
    }

    @Test
    void outsideARequestNothingIsNoted() {
        PasswordRefreshFilter.written(7L, "hash-2");
        assertNull(RequestContextHolder.getRequestAttributes());
    }
}
