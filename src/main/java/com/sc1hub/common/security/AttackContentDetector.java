package com.sc1hub.common.security;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 사용자가 제출한 텍스트에서 공격 의도를 가진 페이로드를 찾는다. 저장 전 정화(sanitizer)와 별개로,
 * "시도 자체"를 신호로 삼아 자동 제재를 걸기 위한 것이다.
 *
 * <ul>
 *   <li><b>HIGH</b>: 스크립트 태그·이벤트 핸들러·javascript: URI·외부 iframe/object 등 HTML 주입, SQL 주입,
 *       템플릿/JNDI 주입, 경로 조작, 명령 실행 구문. 정상 이용자가 우연히 쓸 일이 없는 형태만 고른다.</li>
 *   <li><b>MEDIUM</b>: 링크 다량 삽입, 관리자 사칭 + 외부 링크 + 인증 유도 문구, AI 에게 관리자·DB 자격증명을
 *       요구하는 질문. 거부하되 즉시 차단하지 않고 가중 스트라이크로 누적한다.</li>
 * </ul>
 * 정규식은 되돌림(backtracking)이 폭발하지 않는 형태로만 쓰고, 검사 길이도 제한한다.
 */
@Component
public class AttackContentDetector {

    public enum Severity { NONE, MEDIUM, HIGH }

    /** 판정 결과. {@code rule} 은 사람이 읽을 수 있는 규칙 이름이다(페이로드 원문은 담지 않는다). */
    public record Verdict(Severity severity, String rule) {
        public static final Verdict NONE = new Verdict(Severity.NONE, null);

        public boolean isAttack() {
            return severity != Severity.NONE;
        }

        public boolean isHigh() {
            return severity == Severity.HIGH;
        }
    }

    static final int MAX_SCAN_LENGTH = 20_000;
    private static final int FLAGS = Pattern.CASE_INSENSITIVE;

    private static final List<Rule> HIGH_RULES = List.of(
            new Rule("jndi-injection", Pattern.compile("\\$\\{\\s*jndi\\s*:", FLAGS)),
            new Rule("script-tag", Pattern.compile("<\\s*/?\\s*script\\b", FLAGS)),
            new Rule("event-handler", Pattern.compile(
                    "\\bon(?:error|load|unload|click|dblclick|mouse\\w{2,8}|focus\\w{0,3}|blur|key\\w{2,5}|submit|input|change|toggle|animation\\w{0,9}|pointer\\w{2,6}|wheel|scroll|begin|end|abort|resize)\\s*=",
                    FLAGS)),
            new Rule("js-uri", Pattern.compile("\\b(?:javascript|vbscript)\\s*:", FLAGS)),
            new Rule("html-injection", Pattern.compile("<\\s*(?:svg|object|embed|meta|base|form|link|style|math|xml)\\b[^>]{0,300}=", FLAGS)),
            new Rule("foreign-iframe", Pattern.compile(
                    "<\\s*iframe\\b(?![^>]{0,300}\\bsrc\\s*=\\s*[\"']?https?://(?:www\\.)?(?:youtube(?:-nocookie)?\\.com|youtu\\.be)/)",
                    FLAGS)),
            new Rule("html-data-uri", Pattern.compile("data\\s*:\\s*text/html|\\bsrcdoc\\s*=", FLAGS)),
            new Rule("sql-injection", Pattern.compile(
                    "\\bunion\\b\\s+(?:all\\s+)?select\\b|\\b(?:or|and)\\b\\s+['\"]?\\d+['\"]?\\s*=\\s*['\"]?\\d+"
                            + "|\\bsleep\\s*\\(\\s*\\d+\\s*\\)|\\bbenchmark\\s*\\(|\\binformation_schema\\b|\\bxp_cmdshell\\b"
                            + "|\\bload_file\\s*\\(|\\binto\\s+(?:out|dump)file\\b|;\\s*(?:drop|truncate|alter)\\s+table\\b",
                    FLAGS)),
            new Rule("template-injection", Pattern.compile("\\$\\{[^}]{0,200}\\}|<%[^>]{0,200}%>|#\\{[^}]{0,200}\\}", FLAGS)),
            new Rule("path-traversal", Pattern.compile("(?:\\.\\./){2,}|(?:\\.\\.\\\\){2,}|/etc/(?:passwd|shadow)\\b|\\bWEB-INF/", FLAGS)),
            new Rule("command-injection", Pattern.compile(
                    "\\b(?:wget|curl)\\s+https?://\\S+\\s*\\|\\s*(?:ba)?sh\\b|/bin/(?:ba)?sh\\b|\\brm\\s+-rf\\s+/|\\bnc\\s+-e\\b|\\bchmod\\s+\\+x\\b",
                    FLAGS)));

    private static final Pattern LINK = Pattern.compile("https?://", FLAGS);
    private static final Pattern EXTERNAL_LINK = Pattern.compile("https?://(?!(?:www\\.)?sc1hub\\.com(?:[/:?#]|$))[^\\s<>\"']+", FLAGS);
    private static final Pattern STAFF_WORD = Pattern.compile("관리자|운영자|\\badmin(?:istrator)?\\b|\\bstaff\\b", FLAGS);
    private static final Pattern CREDENTIAL_ACTION = Pattern.compile("비밀번호|패스워드|password|계정\\s*확인|인증|로그인|본인\\s*확인|클릭|접속", FLAGS);
    private static final Pattern CREDENTIAL_TARGET = Pattern.compile(
            "관리자|\\badmin\\b|\\bdb\\b|데이터베이스|database|datasource|application\\.properties|application-online|환경\\s*변수|\\benv\\b|keychain|키체인",
            FLAGS);
    private static final Pattern CREDENTIAL_SECRET = Pattern.compile(
            "비밀번호|패스워드|password|passwd|credential|자격\\s*증명|토큰|token|api\\s*key|secret|시크릿|접속\\s*정보|계정\\s*정보",
            FLAGS);

    /** 게시글·댓글·채팅 등 일반 텍스트 검사. {@code maxLinks} 를 넘는 링크 수는 MEDIUM 으로 본다(0이면 검사 안 함). */
    public Verdict inspect(int maxLinks, String... texts) {
        Verdict worst = Verdict.NONE;
        for (String raw : texts) {
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            String text = raw.length() > MAX_SCAN_LENGTH ? raw.substring(0, MAX_SCAN_LENGTH) : raw;
            for (Rule rule : HIGH_RULES) {
                if (rule.pattern.matcher(text).find()) {
                    return new Verdict(Severity.HIGH, rule.name);
                }
            }
            if (worst.severity() == Severity.NONE) {
                if (maxLinks > 0 && countMatches(LINK, text) > maxLinks) {
                    worst = new Verdict(Severity.MEDIUM, "link-flood");
                } else if (looksLikeStaffPhishing(text)) {
                    worst = new Verdict(Severity.MEDIUM, "staff-impersonation-link");
                }
            }
        }
        return worst;
    }

    /** AI 질문 검사: 일반 규칙에 더해 관리자·DB 자격증명을 캐내려는 질문을 MEDIUM 으로 본다. */
    public Verdict inspectAssistantQuestion(String question) {
        Verdict general = inspect(3, question);
        if (general.isAttack()) {
            return general;
        }
        if (question != null && CREDENTIAL_TARGET.matcher(question).find()
                && CREDENTIAL_SECRET.matcher(question).find()) {
            return new Verdict(Severity.MEDIUM, "credential-harvest");
        }
        return Verdict.NONE;
    }

    private static boolean looksLikeStaffPhishing(String text) {
        return STAFF_WORD.matcher(text).find()
                && EXTERNAL_LINK.matcher(text).find()
                && CREDENTIAL_ACTION.matcher(text).find();
    }

    private static int countMatches(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
            if (count > 50) {
                break;
            }
        }
        return count;
    }

    /** 규칙 이름 목록(문서·테스트용). */
    static List<String> highRuleNames() {
        List<String> names = new ArrayList<>();
        for (Rule rule : HIGH_RULES) {
            names.add(rule.name.toLowerCase(Locale.ROOT));
        }
        return names;
    }

    private record Rule(String name, Pattern pattern) {
    }
}
