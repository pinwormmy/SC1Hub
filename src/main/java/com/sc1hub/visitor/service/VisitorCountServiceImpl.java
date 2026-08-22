package com.sc1hub.visitor.service;

import com.sc1hub.visitor.mapper.VisitorCountMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.regex.Pattern;

@Slf4j
@Service
public class VisitorCountServiceImpl implements VisitorCountService {
    private static final String VISITOR_COOKIE_NAME = "visitor";
    private static final String VISITOR_COOKIE_VALUE = "true";
    private static final String ROOT_PATH = "/";
    private static final ZoneId VISITOR_ZONE = ZoneId.of("Asia/Seoul");
    private static final Pattern BOT_USER_AGENT = Pattern.compile(
            "bot|crawler|spider|slurp|bingpreview|headless|facebookexternalhit|" +
                    "whatsapp|telegrambot|discordbot|curl|wget|python-requests|go-http-client|" +
                    "httpclient|java/|okhttp|scanner|scrapy|semrush|ahrefs|mj12bot|" +
                    "googleother|inspectiontool|lighthouse|pagespeed|zgrab|censys|masscan|nikto",
            Pattern.CASE_INSENSITIVE
    );

    private final VisitorCountMapper visitorCountMapper;
    private final Clock clock;
    private LocalDate lastIdentityCleanupDate;

    @Autowired
    public VisitorCountServiceImpl(VisitorCountMapper visitorCountMapper) {
        this(visitorCountMapper, Clock.system(VISITOR_ZONE));
    }

    VisitorCountServiceImpl(VisitorCountMapper visitorCountMapper, Clock clock) {
        this.visitorCountMapper = visitorCountMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void incrementVisitorCount() {
        LocalDate today = LocalDate.now(clock);
        visitorCountMapper.upsertDailyCount(today);
        visitorCountMapper.incrementTotalCount();
    }

    @Override
    public int getTotalCount() {
        return nullToZero(visitorCountMapper.getTotalCount());
    }

    @Override
    public int getTodayCount() {
        LocalDate today = LocalDate.now(clock);
        return nullToZero(visitorCountMapper.getTodayCount(today));
    }

    @Override
    @Transactional
    public void processVisitor(HttpServletRequest request, HttpServletResponse response) {
        if (hasVisitorCookie(request.getCookies()) || isBot(request.getHeader("User-Agent"))) {
            return;
        }

        LocalDate today = LocalDate.now(clock);
        String visitorHash = createVisitorHash(request, today);
        createVisitorCookie(response);

        if (visitorCountMapper.insertDailyVisitor(today, visitorHash) == 1) {
            incrementVisitorCount();
            cleanupOldIdentitiesOnce(today);
        }
    }

    private boolean hasVisitorCookie(Cookie[] cookies) {
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (VISITOR_COOKIE_NAME.equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }

    private void createVisitorCookie(HttpServletResponse response) {
        Cookie visitorCookie = new Cookie(VISITOR_COOKIE_NAME, VISITOR_COOKIE_VALUE);

        visitorCookie.setMaxAge(secondsUntilTomorrow());
        visitorCookie.setPath(ROOT_PATH);
        visitorCookie.setHttpOnly(true);
        response.addCookie(visitorCookie);
    }

    private boolean isBot(String userAgent) {
        return userAgent == null || userAgent.trim().isEmpty() || BOT_USER_AGENT.matcher(userAgent).find();
    }

    private String createVisitorHash(HttpServletRequest request, LocalDate date) {
        // Forwarded headers are normalized by the configured Tomcat RemoteIpValve.
        // Reading the raw X-Forwarded-For header here would let direct clients spoof identities.
        String clientAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        String source = date + "|" + normalize(clientAddress) + "|" + normalize(userAgent);

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hash.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return hash.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private synchronized void cleanupOldIdentitiesOnce(LocalDate today) {
        if (today.equals(lastIdentityCleanupDate)) {
            return;
        }
        visitorCountMapper.deleteDailyVisitorsBefore(today);
        lastIdentityCleanupDate = today;
    }

    private int secondsUntilTomorrow() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime midnight = LocalDateTime.of(LocalDate.now(clock).plusDays(1), LocalTime.MIDNIGHT);
        return (int) Duration.between(now, midnight).getSeconds();
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
