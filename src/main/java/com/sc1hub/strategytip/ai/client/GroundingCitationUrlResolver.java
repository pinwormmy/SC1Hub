package com.sc1hub.strategytip.ai.client;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolves Gemini grounding redirects without following a request to the cited site.
 */
@Component
public class GroundingCitationUrlResolver {

    private static final int MAX_REDIRECT_HOPS = 5;
    private static final int MAX_DISPLAY_TITLE_CHARS = 180;
    private static final String VERTEX_REDIRECT_HOST = "vertexaisearch.cloud.google.com";
    private static final Pattern SC1HUB_IN_TITLE = Pattern.compile(
            "(?i)(^|[^a-z0-9.-])(?:[a-z0-9-]+\\.)*sc1hub\\.com\\.?($|[^a-z0-9.-])");

    private final RedirectFetcher redirectFetcher;

    public GroundingCitationUrlResolver() {
        this(new JdkRedirectFetcher());
    }

    GroundingCitationUrlResolver(RedirectFetcher redirectFetcher) {
        this.redirectFetcher = redirectFetcher;
    }

    ResolvedDestination resolveDestination(String rawUrl) {
        URI initial = requireSafeHttpsUri(rawUrl);
        if (VERTEX_REDIRECT_HOST.equals(normalizedHost(initial))
                && !isTrustedGoogleRedirect(initial)) {
            throw unsafeCitation("Google grounding citation used an untrusted redirect path.");
        }
        if (isGoogleRedirectOrSearchPage(initial) && !isTrustedGoogleRedirect(initial)) {
            throw unsafeCitation("Gemini cited a Google redirect or search page instead of a source.");
        }
        if (!isTrustedGoogleRedirect(initial)) {
            return new ResolvedDestination(toDisplayUrl(initial), normalizedHost(initial));
        }

        URI current = initial;
        Set<String> visited = new LinkedHashSet<>();
        for (int hop = 0; hop < MAX_REDIRECT_HOPS; hop++) {
            String visitKey = toDisplayUrl(current);
            if (!visited.add(visitKey)) {
                throw unsafeCitation("Google grounding redirect loop was detected.");
            }
            if (!isTrustedGoogleRedirect(current)) {
                throw unsafeCitation("Google grounding redirect left the trusted endpoint unexpectedly.");
            }

            RedirectResponse response;
            try {
                response = redirectFetcher.fetch(current);
            } catch (IOException e) {
                throw new GeminiStrategyTipException(
                        "Google grounding citation redirect could not be resolved.", e);
            }
            if (response == null || !isRedirectStatus(response.statusCode)
                    || !StringUtils.hasText(response.location)) {
                throw unsafeCitation("Google grounding citation redirect was missing or invalid.");
            }

            URI next = resolveLocation(current, response.location);
            next = requireSafeHttpsUri(next.toASCIIString());
            if (isTrustedGoogleRedirect(next)) {
                current = next;
                continue;
            }
            if (isGoogleRedirectOrSearchPage(next)) {
                throw unsafeCitation("Google grounding citation did not resolve to a source page.");
            }

            // The first non-Google destination is the cited page. It is validated but never fetched.
            return new ResolvedDestination(toDisplayUrl(next), normalizedHost(next));
        }
        throw unsafeCitation("Google grounding citation exceeded the redirect limit.");
    }

    String safeDisplayTitle(ResolvedDestination destination, String rawTitle) {
        String title = normalizeDisplayTitle(rawTitle);
        if (StringUtils.hasText(title) && SC1HUB_IN_TITLE.matcher(title).find()) {
            throw unsafeCitation("Gemini cited SC1Hub as external evidence.");
        }
        if (StringUtils.hasText(title) && titleLooksUnsafe(title)) {
            throw unsafeCitation("Gemini returned an unsafe external citation title.");
        }
        return StringUtils.hasText(title) ? title : destination.host;
    }

    private URI resolveLocation(URI current, String location) {
        try {
            return current.resolve(new URI(location.trim()));
        } catch (IllegalArgumentException | URISyntaxException e) {
            throw new GeminiStrategyTipException(
                    "Google grounding citation redirect URL was invalid.", e);
        }
    }

    private URI requireSafeHttpsUri(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            throw unsafeCitation("Gemini returned a blank external citation URL.");
        }

        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException e) {
            throw new GeminiStrategyTipException("Gemini returned an invalid external citation URL.", e);
        }

        String host = normalizedHost(uri);
        int port = uri.getPort();
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !StringUtils.hasText(host)
                || uri.getUserInfo() != null
                || (port != -1 && port != 443)
                || isUnsafeHost(host)) {
            throw unsafeCitation("Gemini returned an unsafe external citation URL.");
        }
        return uri;
    }

    private String normalizedHost(URI uri) {
        String host = uri == null ? null : uri.getHost();
        if (!StringUtils.hasText(host)) {
            return "";
        }
        host = host.trim();
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        try {
            return IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private boolean isUnsafeHost(String host) {
        if (!StringUtils.hasText(host)
                || host.indexOf(':') >= 0
                || host.matches("[0-9.]+")
                || host.matches("[0-9]+")
                || host.matches("(?i)0x[0-9a-f]+")) {
            return true;
        }
        if ("sc1hub.com".equals(host) || host.endsWith(".sc1hub.com")) {
            return true;
        }
        if ("localhost".equals(host) || host.endsWith(".localhost")
                || "localdomain".equals(host) || host.endsWith(".localdomain")
                || host.endsWith(".local") || host.endsWith(".internal")
                || host.endsWith(".lan") || host.endsWith(".home")) {
            return true;
        }
        if (host.indexOf('.') < 0) {
            return true;
        }
        String[] labels = host.split("\\.");
        for (String label : labels) {
            if (!StringUtils.hasText(label)
                    || label.startsWith("-") || label.endsWith("-")
                    || !label.matches("[a-z0-9-]+")) {
                return true;
            }
        }
        return false;
    }

    private boolean titleLooksUnsafe(String title) {
        String candidate = title.trim();
        if (candidate.regionMatches(true, 0, "https://", 0, "https://".length())) {
            try {
                return isUnsafeHost(normalizedHost(new URI(candidate)));
            } catch (URISyntaxException e) {
                return true;
            }
        }
        if (candidate.matches("(?i)(localhost|[^\\s/]+\\.local|[0-9.]+)")) {
            return true;
        }
        return false;
    }

    private boolean isTrustedGoogleRedirect(URI uri) {
        String host = normalizedHost(uri);
        String path = uri.getPath() == null ? "" : uri.getPath();
        return VERTEX_REDIRECT_HOST.equals(host)
                && path.startsWith("/grounding-api-redirect/");
    }

    private boolean isGoogleRedirectOrSearchPage(URI uri) {
        String host = normalizedHost(uri);
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (VERTEX_REDIRECT_HOST.equals(host)) {
            return true;
        }
        return ("google.com".equals(host) || "www.google.com".equals(host))
                && ("/url".equals(path) || "/search".equals(path) || "/imgres".equals(path));
    }

    private String toDisplayUrl(URI uri) {
        try {
            String host = normalizedHost(uri);
            StringBuilder value = new StringBuilder("https://").append(host);
            if (uri.getPort() != -1 && uri.getPort() != 443) {
                value.append(':').append(uri.getPort());
            }
            value.append(StringUtils.hasText(uri.getRawPath()) ? uri.getRawPath() : "/");
            if (StringUtils.hasText(uri.getRawQuery())) {
                value.append('?').append(uri.getRawQuery());
            }
            URI safe = new URI(value.toString()).normalize();
            return safe.toASCIIString();
        } catch (URISyntaxException e) {
            throw new GeminiStrategyTipException("Gemini returned an invalid external citation URL.", e);
        }
    }

    private String normalizeDisplayTitle(String rawTitle) {
        if (!StringUtils.hasText(rawTitle)) {
            return "";
        }
        String value = rawTitle.replaceAll("[\\p{Cntrl}]", " ")
                .replaceAll("\\s+", " ").trim();
        return value.length() <= MAX_DISPLAY_TITLE_CHARS
                ? value : value.substring(0, MAX_DISPLAY_TITLE_CHARS).trim();
    }

    private boolean isRedirectStatus(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303
                || statusCode == 307 || statusCode == 308;
    }

    private GeminiStrategyTipException unsafeCitation(String message) {
        return new GeminiStrategyTipException(message);
    }

    interface RedirectFetcher {
        RedirectResponse fetch(URI uri) throws IOException;
    }

    static final class RedirectResponse {
        private final int statusCode;
        private final String location;

        RedirectResponse(int statusCode, String location) {
            this.statusCode = statusCode;
            this.location = location;
        }
    }

    static final class ResolvedDestination {
        private final String url;
        private final String host;

        private ResolvedDestination(String url, String host) {
            this.url = url;
            this.host = host;
        }

        String getUrl() {
            return url;
        }
    }

    private static final class JdkRedirectFetcher implements RedirectFetcher {

        @Override
        public RedirectResponse fetch(URI uri) throws IOException {
            URLConnection rawConnection = uri.toURL().openConnection();
            if (!(rawConnection instanceof HttpsURLConnection)) {
                throw new IOException("Grounding redirect did not use HTTPS.");
            }
            HttpsURLConnection connection = (HttpsURLConnection) rawConnection;
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            connection.setRequestProperty("User-Agent", "SC1Hub-Citation-Resolver/1.0");
            try {
                return new RedirectResponse(
                        connection.getResponseCode(), connection.getHeaderField("Location"));
            } finally {
                connection.disconnect();
            }
        }
    }
}
