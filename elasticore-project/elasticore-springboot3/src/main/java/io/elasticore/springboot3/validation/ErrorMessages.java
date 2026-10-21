package io.elasticore.springboot3.validation;

import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.util.ClassUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The words an API error is said in, in the caller's language.
 *
 * A message is looked up by its code in, first to last:
 * <ol>
 *   <li>{@code messages[_locale].properties} on the classpath - the
 *       application's own translations, found even where Spring Boot did not
 *       set up a MessageSource;</li>
 *   <li>the domain's generated bundle, {@code i18n/<domain>-messages.properties},
 *       and a translation placed beside it ({@code ..._ko.properties});</li>
 *   <li>the application's MessageSource, for any other set-up it has.</li>
 * </ol>
 * A file for the request's language ({@code _ko}) of any of the first two is
 * read before any of their base files, so one domain's English never hides
 * another domain's translation.
 * Nothing found: the default the code was written with. A Bean Validation
 * built-in message (Size, NotNull ...) that no bundle translates is said again
 * in the same language, with its numbers, so one answer is in one language.
 *
 * The language, for an application on Spring MVC's default locale resolver:
 * <ul>
 *   <li>the first Accept-Language entry that has a translation - a
 *       {@code _<lang>} file of messages, of the domain's bundle or of
 *       ValidationMessages - or that is the default language, or English
 *       (the generated bundles' own wording);</li>
 *   <li>otherwise the default: {@code spring.web.locale}, and where that is
 *       not set the bundles' own wording (Locale.ROOT).</li>
 * </ul>
 * The server machine's language never decides. An application that chose the
 * language some other way (a session, a cookie, a fixed or its own resolver)
 * keeps its choice; a request without a header that nobody chose a language
 * for is answered in the bundles' own wording there too.
 *
 * English without an {@code _en} file of its own is said as the bundles' own
 * wording (Locale.ROOT): any other English locale lets a bundle without an
 * {@code _en} fall back to the server machine's language.
 */
public final class ErrorMessages {

    /** Where DispatcherServlet keeps the request's locale resolver. */
    private static final String LOCALE_RESOLVER = "org.springframework.web.servlet.DispatcherServlet.LOCALE_RESOLVER";

    /** Hibernate Validator's own bundle of built-in messages. */
    private static final String PROVIDER_MESSAGES = "org.hibernate.validator.ValidationMessages";

    /** Whether a bundle has a file of its own for a locale, by loader, bundle and locale. */
    private static final Map<String, Boolean> OWN_FILE = new ConcurrentHashMap<String, Boolean>();

    private final QuietBundles source = new QuietBundles();

    /** The bundles whose {@code _<lang>} file makes a language one the application translated. */
    private final String[] translations;

    /**
     * @param application the application's MessageSource, or null
     * @param bundle      the domain's generated bundle, e.g. {@code i18n/shop-messages}
     */
    public ErrorMessages(MessageSource application, String bundle) {
        this(application, new String[]{bundle});
    }

    /**
     * @param application the application's MessageSource, or null
     * @param bundles     the domains' generated bundles, looked in in this order
     */
    public ErrorMessages(MessageSource application, String... bundles) {
        Set<String> names = new LinkedHashSet<String>();
        names.add("messages");
        if (bundles != null) {
            for (String b : bundles) {
                if (b != null && !b.isEmpty()) names.add(b);
            }
        }
        source.setBasenames(names.toArray(new String[0]));
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(false);
        if (application != null) source.setParentMessageSource(application);
        names.add("ValidationMessages");
        this.translations = names.toArray(new String[0]);
    }

    /** The message for a code, or the default as given (not formatted) when no bundle has it. */
    public String text(String code, String defaultText, Object... args) {
        if (code == null) return defaultText;
        try {
            String found = source.getMessage(code, args, null, language());
            return found != null ? found : defaultText;
        } catch (RuntimeException ex) {
            // A translation with a broken pattern must not turn a 4xx into a 500.
            return defaultText;
        }
    }

    /**
     * A field error, a binding error: Spring's own codes (NotNull.customerDTO.login ... NotNull);
     * then a built-in constraint message said again in the request's language; then its default.
     */
    public String text(MessageSourceResolvable error) {
        return textOf(error, error);
    }

    /**
     * An error by Spring's codes, with the ConstraintViolation behind it given
     * apart - a method parameter's error, whose violation Spring hands out
     * separately - for the built-in message said again.
     */
    public String textOf(MessageSourceResolvable error, Object violation) {
        Locale language = language();
        String[] codes = error.getCodes();
        if (codes != null && codes.length > 0) {
            try {
                return source.getMessage(new DefaultMessageSourceResolvable(codes, error.getArguments()), language);
            } catch (NoSuchMessageException notTranslated) {
                // Said below, in the language chosen.
            } catch (RuntimeException ex) {
                return error.getDefaultMessage();
            }
        }
        String again = BeanValidation.messageIn(violation, validationLocale(language));
        return again != null ? again : error.getDefaultMessage();
    }

    /**
     * A constraint violation a service-side check raised: its code (Size.lines),
     * with the values Spring would give it ({0} the field, then the constraint's
     * attributes by name: Size's {1} max, {2} min); then a built-in message said
     * again in the request's language; then the message as the validator said it.
     */
    public String violation(String code, Object violation, String message) {
        Locale language = language();
        if (code != null) {
            try {
                String found = source.getMessage(code, BeanValidation.argumentsOf(violation), null, language);
                if (found != null) return found;
            } catch (RuntimeException ex) {
                return message;
            }
        }
        String again = BeanValidation.messageIn(violation, validationLocale(language));
        return again != null ? again : message;
    }

    /** The message for a code; when no bundle has it, the default formatted with the values (MessageFormat). */
    public String format(String code, String defaultPattern, Object... args) {
        String found = text(code, null, args);
        if (found != null) return found;
        if (args == null || args.length == 0) return defaultPattern;
        try {
            return new MessageFormat(defaultPattern, Locale.ROOT).format(args);
        } catch (RuntimeException ex) {
            return defaultPattern;
        }
    }

    /** The language this request is answered in: Locale.ROOT for the bundles' own wording. */
    public Locale language() {
        return normalized(chosen());
    }

    private Locale chosen() {
        try {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes) {
                jakarta.servlet.http.HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
                Locale byHeader = byAcceptHeader(request);
                if (byHeader != null) return byHeader;
                String header = request.getHeader("Accept-Language");
                Locale resolved = LocaleContextHolder.getLocale();
                // No header, and the resolver answered the container's fallback
                // (the machine's locale): nobody chose it. An application that
                // chose otherwise - a session, a cookie, a fixed locale - is kept.
                if ((header == null || header.trim().isEmpty()) && resolved.equals(request.getLocale())) {
                    return Locale.ROOT;
                }
            }
        } catch (RuntimeException | LinkageError ignore) {
            // Not a servlet request (or no servlet API): the resolver's answer stands.
        }
        return LocaleContextHolder.getLocale();
    }

    /** By the header, when the application uses Spring MVC's default resolver; else null. */
    private Locale byAcceptHeader(jakarta.servlet.http.HttpServletRequest request) {
        Object resolver = request.getAttribute(LOCALE_RESOLVER);
        if (resolver == null) return null;
        Locale fallback;
        try {
            fallback = AcceptHeader.defaultOf(resolver, request);
        } catch (LinkageError noSpringMvc) {
            return null;
        }
        if (fallback == null) return null;
        String header = request.getHeader("Accept-Language");
        if (header == null || header.trim().isEmpty()) return fallback;
        List<Locale> wanted = Collections.list(request.getLocales());
        for (Locale each : wanted) {
            if (each == null || each.getLanguage().isEmpty()) continue;
            // English is the generated bundles' own wording: asked for, it is
            // there whatever the default is.
            if (each.getLanguage().equals(fallback.getLanguage()) || "en".equals(each.getLanguage())
                    || translated(each)) {
                return each;
            }
        }
        return fallback;
    }

    private Locale normalized(Locale chosen) {
        if (chosen == null) return Locale.ROOT;
        if ("en".equals(chosen.getLanguage()) && !translated(chosen)) return Locale.ROOT;
        return chosen;
    }

    /** Whether the application has a file of its own for this language: messages, a domain bundle, ValidationMessages. */
    boolean translated(Locale locale) {
        if (locale.getLanguage().isEmpty()) return false;
        for (String basename : translations) {
            if (hasOwnFile(basename, locale)) return true;
        }
        return false;
    }

    /**
     * The locale a built-in message is said again in: the language chosen when
     * the provider or the application has that language; else the bundles'
     * own wording, since a bundle without it falls back to the machine's language.
     */
    private static Locale validationLocale(Locale language) {
        if (Locale.ROOT.equals(language)) return language;
        if (hasOwnFile(PROVIDER_MESSAGES, language) || hasOwnFile("ValidationMessages", language)) return language;
        return Locale.ROOT;
    }

    /**
     * A {@code _<lang>} file of the bundle, never the base file and never the
     * machine's language: the thread's class loader, as Spring's own message
     * sources use, so an application restarted by DevTools is seen.
     */
    static boolean hasOwnFile(String basename, Locale locale) {
        ClassLoader loader = ClassUtils.getDefaultClassLoader();
        String key = System.identityHashCode(loader) + "\u0000" + basename + "\u0000" + locale;
        Boolean known = OWN_FILE.get(key);
        if (known != null) return known;
        boolean own;
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(basename, locale,
                    loader != null ? loader : ErrorMessages.class.getClassLoader(),
                    ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT));
            own = locale.getLanguage().equals(bundle.getLocale().getLanguage());
        } catch (MissingResourceException | IllegalArgumentException | UnsupportedOperationException none) {
            own = false;
        }
        OWN_FILE.put(key, own);
        return own;
    }

    /**
     * Everything that names Spring MVC, kept out of the outer class so that
     * an application without it (WebFlux) still loads ErrorMessages.
     */
    private static final class AcceptHeader {

        /**
         * The resolver's default - spring.web.locale - or Locale.ROOT where it
         * has none; null when the resolver is not Spring MVC's Accept-Language one.
         */
        static Locale defaultOf(Object resolver, jakarta.servlet.http.HttpServletRequest request) {
            if (!(resolver instanceof org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver)) return null;
            Locale answer = ((org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver) resolver)
                    .resolveLocale(new Headerless(request));
            return answer != null ? answer : Locale.ROOT;
        }
    }

    /**
     * The request as if it named no language and the container had none
     * either: what the resolver answers for it is its default.
     */
    private static final class Headerless extends jakarta.servlet.http.HttpServletRequestWrapper {

        Headerless(jakarta.servlet.http.HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getHeader(String name) {
            return "Accept-Language".equalsIgnoreCase(name) ? null : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return "Accept-Language".equalsIgnoreCase(name)
                    ? Collections.<String>emptyEnumeration() : super.getHeaders(name);
        }

        @Override
        public Locale getLocale() {
            return Locale.ROOT;
        }

        @Override
        public Enumeration<Locale> getLocales() {
            List<Locale> root = new ArrayList<Locale>();
            root.add(Locale.ROOT);
            return Collections.enumeration(root);
        }
    }

    /**
     * A bundle that is not there is remembered as not there. Spring caches
     * only the bundles it finds and logs a warning on every lookup of one it
     * does not - for an application without messages.properties, a warning
     * on every error response.
     *
     * A code is looked up in two passes: first in the bundles' files for the
     * language itself (shop-messages_ko), in the bundles' order; then as
     * Spring does, where a bundle without that file answers from its base
     * file. Spring alone takes the first bundle that has the code at all, so
     * with several domains the English base file of the domain that sorts
     * first hid another domain's translation (every domain's base file has
     * the shared codes).
     */
    private static final class QuietBundles extends ResourceBundleMessageSource {
        private final Map<String, ResourceBundle> found = new ConcurrentHashMap<>();
        private final Set<String> missing = ConcurrentHashMap.newKeySet();

        @Override
        protected String resolveCodeWithoutArguments(String code, Locale locale) {
            for (String basename : getBasenameSet()) {
                ResourceBundle own = ownFileWith(basename, code, locale);
                if (own != null) {
                    String result = getStringOrNull(own, code);
                    if (result != null) return result;
                }
            }
            return super.resolveCodeWithoutArguments(code, locale);
        }

        @Override
        protected MessageFormat resolveCode(String code, Locale locale) {
            for (String basename : getBasenameSet()) {
                ResourceBundle own = ownFileWith(basename, code, locale);
                if (own != null) {
                    MessageFormat result = getMessageFormat(own, code, locale);
                    if (result != null) return result;
                }
            }
            return super.resolveCode(code, locale);
        }

        /**
         * The bundle's file for the locale (_ko_KR) or its language (_ko)
         * when that file itself has the code; else null - also for a bundle
         * that is not a properties file, which the second pass still reads.
         */
        private ResourceBundle ownFileWith(String basename, String code, Locale locale) {
            if (locale == null || locale.getLanguage().isEmpty()) return null;
            Set<Locale> candidates = new LinkedHashSet<Locale>();
            candidates.add(locale);
            candidates.add(new Locale(locale.getLanguage(), locale.getCountry()));
            candidates.add(new Locale(locale.getLanguage()));
            for (Locale candidate : candidates) {
                ResourceBundle bundle = getResourceBundle(basename, candidate);
                if (bundle instanceof PropertyResourceBundle && candidate.equals(bundle.getLocale())
                        && ((PropertyResourceBundle) bundle).handleGetObject(code) != null) {
                    return bundle;
                }
            }
            return null;
        }

        @Override
        protected ResourceBundle getResourceBundle(String basename, Locale locale) {
            String key = basename + '\u0000' + locale;
            ResourceBundle bundle = found.get(key);
            if (bundle != null) return bundle;
            if (missing.contains(key)) return null;
            try {
                bundle = doGetBundle(basename, locale);
                found.put(key, bundle);
                return bundle;
            } catch (MissingResourceException ex) {
                missing.add(key);
                return null;
            }
        }
    }
}
