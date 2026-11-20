package io.elasticore.springboot3.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.util.ClassUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * {@code @signin(refresh=password)}: what {@link SessionRefreshFilter} does, and
 * a session opened before a password change ends. The generator writes a
 * subclass of this filter for the entity that signs in.
 *
 * The session holds a stamp of the password hash, never the hash: the first 16
 * hex characters of the SHA-256 of the stored hash ({@link #stamp}). The row is
 * read once per signed-in request, as with refresh=request, and that one read
 * answers the roles and the stamp:
 *
 * <ul>
 *   <li>no row, or one kept under @deleted: the session ends;</li>
 *   <li>a different role: the session takes it;</li>
 *   <li>a session without a stamp (signed in by the application's own code, or
 *       before this filter was there) takes the row's;</li>
 *   <li>a session stamped while the row had no password (another provider) takes
 *       the row's when one is set: that is not a change;</li>
 *   <li>any other difference - the password changed, or was removed - ends the
 *       session, and the request goes on as a visitor's.</li>
 * </ul>
 *
 * The session that changes its own row's password keeps going: the service
 * reports the hash it is about to store ({@link #written}). In a transaction
 * the session takes the new stamp when it commits; without one, once the
 * request is answered without an error. A session the request signed in has
 * no stamp yet; it takes the row's at the end of that request.
 */
public abstract class PasswordRefreshFilter extends OncePerRequestFilter {

    /** The session attribute that holds the stamp. */
    public static final String STAMP = "elasticore.user.stamp";

    /** The stamp of a row without a password. */
    static final String NONE = "-";

    /** The request attribute that holds the passwords written in this request, by the row's key. */
    static final String WRITTEN = PasswordRefreshFilter.class.getName() + ".written";

    private static final boolean TRANSACTIONS = ClassUtils.isPresent(
            "org.springframework.transaction.support.TransactionSynchronizationManager",
            PasswordRefreshFilter.class.getClassLoader());

    /** The row the session names, as one read answers it: its roles and the stamp of its password. */
    public static final class Row {
        private final Set<String> roles;
        private final String stamp;

        Row(Set<String> roles, String stamp) {
            this.roles = roles;
            this.stamp = stamp;
        }

        public Set<String> roles() {
            return roles;
        }

        /** The stamp of the password the row holds, or null when it holds none. */
        public String stamp() {
            return stamp;
        }
    }

    /**
     * The row the session names, read again now, or empty when that row is
     * gone. The generated subclass answers it through the service's
     * signedInRow(), the read me() makes.
     */
    protected abstract Optional<Row> signedInRow();

    /** A row as the session refresh reads it: its roles, and the stored password hash, which is only stamped. */
    public static Row row(Collection<String> roles, String passwordHash) {
        Set<String> held = new LinkedHashSet<String>();
        if (roles != null) {
            for (String role : roles) if (role != null) held.add(role);
        }
        return new Row(held, stamp(passwordHash));
    }

    /** The first 16 hex characters of the SHA-256 of the hash, or null for no hash. */
    public static String stamp(String passwordHash) {
        if (passwordHash == null) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(passwordHash.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8; i++) hex.append(String.format("%02x", digest[i] & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /**
     * The service is about to store this hash as the password of the row with
     * this key. If the session of the request is that row's, it keeps going
     * with the new stamp - when the transaction commits, or without one, once
     * the request is answered without an error. Outside a
     * request nothing is noted. The generated update calls it; the
     * application's own code that writes a password may too.
     */
    public static void written(Object id, String passwordHash) {
        if (id == null || RequestContextHolder.getRequestAttributes() == null) return;
        final String key = String.valueOf(id);
        final String stamp = stamp(passwordHash);
        // Committed, the session takes the new stamp at once: a request of the same session sent as
        // soon as the answer arrives must not meet the old one. Without a transaction the write may
        // still fail, so the filter gives it after the answer, if that is not an error.
        if (TRANSACTIONS && Transactions.afterCommit(() -> note(key, stamp, true))) return;
        note(key, stamp, false);
    }

    @SuppressWarnings("unchecked")
    private static void note(String key, String stamp, boolean committed) {
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request == null) return;
        String held = stamp == null ? NONE : stamp;
        Map<String, String> written =
                (Map<String, String>) request.getAttribute(WRITTEN, RequestAttributes.SCOPE_REQUEST);
        if (written == null) {
            written = new LinkedHashMap<String, String>();
            request.setAttribute(WRITTEN, written, RequestAttributes.SCOPE_REQUEST);
        }
        written.put(key, held);
        if (!committed) return;
        try {
            Object id = request.getAttribute(SessionCurrentUser.ID, RequestAttributes.SCOPE_SESSION);
            if (id != null && key.equals(String.valueOf(id)))
                request.setAttribute(STAMP, held, RequestAttributes.SCOPE_SESSION);
        } catch (IllegalStateException e) {
            // The session ended meanwhile: nothing to stamp.
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (attribute(request.getSession(false), SessionCurrentUser.ID) != null) {
            refresh(request, response);
        }
        chain.doFilter(request, response);
        afterwards(request, response);
    }

    /**
     * A session's attribute, or null when there is no session or it has just ended - a request of
     * the same session running beside this one may have ended it.
     */
    private static Object attribute(HttpSession session, String name) {
        if (session == null) return null;
        try {
            return session.getAttribute(name);
        } catch (IllegalStateException ended) {
            return null;
        }
    }

    private void refresh(HttpServletRequest request, HttpServletResponse response) {
        Optional<Row> row = read(request, response);
        try {
            if (!row.isPresent()) {
                SessionCurrentUser.signOut(request);
                return;
            }
            SessionCurrentUser.refresh(request, row.get().roles());
            HttpSession session = request.getSession(false);
            if (session == null) return;
            Object held = session.getAttribute(STAMP);
            String now = row.get().stamp() == null ? NONE : row.get().stamp();
            if (held == null || NONE.equals(held)) {
                // Not stamped yet, or stamped without a password: the row's stamp is the session's.
                if (!now.equals(held)) session.setAttribute(STAMP, now);
            } else if (!held.equals(now)) {
                SessionCurrentUser.signOut(request);
            }
        } catch (IllegalStateException ended) {
            // Ended by a request of the same session meanwhile: this one goes on as a visitor's.
        }
    }

    /**
     * After the request: the session that wrote its own row's password takes
     * the new stamp; a session the request signed in takes the row's.
     */
    @SuppressWarnings("unchecked")
    private void afterwards(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        Object id = attribute(session, SessionCurrentUser.ID);
        if (id == null) return;
        Map<String, String> written = (Map<String, String>) request.getAttribute(WRITTEN);
        String mine = written == null ? null : written.get(String.valueOf(id));
        try {
            if (mine != null && response.getStatus() < 400) {
                session.setAttribute(STAMP, mine);
                return;
            }
            if (session.getAttribute(STAMP) != null) return;
            Optional<Row> row = read(request, response);
            if (row.isPresent())
                session.setAttribute(
                        STAMP, row.get().stamp() == null ? NONE : row.get().stamp());
        } catch (RuntimeException e) {
            // The answer is given; a session that ended meanwhile needs no stamp, and one whose row
            // could not be read is stamped by its next request instead.
        }
    }

    private Optional<Row> read(HttpServletRequest request, HttpServletResponse response) {
        // As SessionRefreshFilter: the read asks CurrentUser, which reads the session through RequestContextHolder.
        RequestAttributes outer = RequestContextHolder.getRequestAttributes();
        if (outer == null) RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        try {
            Optional<Row> row = signedInRow();
            return row == null ? Optional.<Row>empty() : row;
        } finally {
            if (outer == null) RequestContextHolder.resetRequestAttributes();
        }
    }

    /** Spring's transaction synchronization, in a class of its own so it is loaded only where spring-tx is. */
    private static final class Transactions {
        static boolean afterCommit(final Runnable then) {
            if (!org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
                return false;
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            then.run();
                        }
                    });
            return true;
        }
    }
}
