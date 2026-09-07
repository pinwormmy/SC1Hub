package com.sc1hub.board.service;

import com.sc1hub.assistant.config.AssistantBotProperties;
import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.dto.BoardListDTO;
import com.sc1hub.board.dto.CommentDTO;
import com.sc1hub.board.mapper.BoardMapper;
import com.sc1hub.board.support.BoardTitleNormalizer;
import com.sc1hub.board.support.GuestPasswordHasher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 비회원 글(funboard.guest_password)·댓글(*_comment.password)의 평문 비밀번호 행을 서버 안에서 BCrypt 로 승격한다.
 *
 * <p>평문 행은 저장값이 곧 이용자가 입력해야 하는 비밀번호이므로 그 값을 해시해 제자리에 저장하면 검증
 * ({@link GuestPasswordHasher#matches})은 그대로 통과한다. 봇 페르소나가 발행한 행은 예전에 하나의 공유
 * 비밀번호로 만들어졌으므로(값이 공개 저장소 이력에 남은 적이 있다) 그 값 대신 아무도 모르는 무작위 값으로
 * 다시 잠근다 — 봇 글은 관리자만 관리한다.
 *
 * <p>안전장치: BCrypt 형식 행은 건드리지 않는다, 갱신은 읽었을 때와 값이 같을 때만 적용한다(낙관적 UPDATE),
 * 어떤 예외도 기동을 막지 않는다. 기동 시 데몬 스레드에서 한 번 실행되며(표 하나당 최대 {@value #MAX_ROWS_PER_TABLE_PER_RUN}행),
 * 남은 행은 다음 기동이나 관리자 API({@code POST /api/admin/security/guest-passwords/migrate})에서 이어서 처리한다.
 */
@Component
@Slf4j
public class GuestPasswordMigration {

    static final int MAX_ROWS_PER_TABLE_PER_RUN = 1000;
    private static final String GUEST_POST_BOARD = "funboard";

    private final BoardMapper boardMapper;
    private final GuestPasswordHasher hasher;
    private final Set<String> botPersonaNames;
    private final boolean enabledOnStartup;
    private volatile Summary lastRun;

    public GuestPasswordMigration(BoardMapper boardMapper, GuestPasswordHasher hasher,
                                  AssistantBotProperties botProperties,
                                  @Value("${sc1hub.security.guest-password-migration-enabled:true}")
                                  boolean enabledOnStartup) {
        this.boardMapper = boardMapper;
        this.hasher = hasher;
        this.botPersonaNames = collectPersonaNames(botProperties);
        this.enabledOnStartup = enabledOnStartup;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrateOnStartup() {
        if (!enabledOnStartup) {
            log.info("비회원 비밀번호 일괄 승격은 설정으로 비활성화되어 있습니다.");
            return;
        }
        // BCrypt 는 행마다 수십 ms 가 들므로 기동을 막지 않도록 백그라운드에서 돈다.
        Thread worker = new Thread(this::run, "guest-password-migration");
        worker.setDaemon(true);
        worker.start();
    }

    /** 승격을 실행하고 요약을 돌려준다. 비밀번호 값은 어떤 형태로도 로그·요약에 남기지 않는다. */
    public synchronized Summary run() {
        Counters counters = new Counters();
        String error = null;
        try {
            List<BoardListDTO> boards = boardMapper.getBoardList();
            for (BoardListDTO board : boards == null ? Collections.<BoardListDTO>emptyList() : boards) {
                String boardTitle = BoardTitleNormalizer.requireValid(board.getBoardTitle());
                if (GUEST_POST_BOARD.equals(boardTitle)) {
                    migrateGuestPosts(boardTitle, counters);
                }
                migrateGuestComments(boardTitle, counters);
            }
        } catch (Exception e) {
            error = e.getClass().getSimpleName();
            log.error("비회원 비밀번호 일괄 승격을 수행하지 못했습니다.", e);
        }
        Summary summary = new Summary(Instant.now(), counters.candidates, counters.upgraded, counters.rekeyedBotRows,
                counters.skipped, counters.failed, counters.truncated, error);
        lastRun = summary;
        log.info("비회원 비밀번호 일괄 승격 결과: 대상 {}건, 승격 {}건(봇 행 재잠금 {}건), 건너뜀 {}건, 실패 {}건{}{}",
                counters.candidates, counters.upgraded, counters.rekeyedBotRows, counters.skipped, counters.failed,
                counters.truncated ? ", 남은 행 있음(다음 실행에서 계속)" : "",
                error == null ? "" : ", 오류 " + error);
        return summary;
    }

    public Summary getLastRun() {
        return lastRun;
    }

    private void migrateGuestPosts(String boardTitle, Counters counters) throws Exception {
        List<BoardDTO> rows = boardMapper.selectLegacyGuestPostPasswords(boardTitle, MAX_ROWS_PER_TABLE_PER_RUN);
        if (rows == null) {
            return;
        }
        if (rows.size() >= MAX_ROWS_PER_TABLE_PER_RUN) {
            counters.truncated = true;
        }
        for (BoardDTO row : rows) {
            counters.candidates++;
            String stored = row.getGuestPassword();
            if (!StringUtils.hasText(stored) || hasher.isHashed(stored)) {
                counters.skipped++;
                continue;
            }
            boolean botRow = isBotName(row.getWriter());
            try {
                String hash = hasher.hash(botRow ? GuestPasswordHasher.newRandomSecret() : stored);
                int updated = boardMapper.updateGuestPostPasswordIfUnchanged(boardTitle, row.getPostNum(), stored, hash);
                counters.record(updated, botRow);
            } catch (Exception e) {
                counters.failed++;
                log.error("비회원 글 비밀번호 승격 실패. board={}, postNum={}", boardTitle, row.getPostNum(), e);
            }
        }
    }

    private void migrateGuestComments(String boardTitle, Counters counters) throws Exception {
        List<CommentDTO> rows = boardMapper.selectLegacyCommentPasswords(boardTitle, MAX_ROWS_PER_TABLE_PER_RUN);
        if (rows == null) {
            return;
        }
        if (rows.size() >= MAX_ROWS_PER_TABLE_PER_RUN) {
            counters.truncated = true;
        }
        for (CommentDTO row : rows) {
            counters.candidates++;
            String stored = row.getPassword();
            if (!StringUtils.hasText(stored) || hasher.isHashed(stored)) {
                counters.skipped++;
                continue;
            }
            boolean botRow = !StringUtils.hasText(row.getId()) && isBotName(row.getNickname());
            try {
                String hash = hasher.hash(botRow ? GuestPasswordHasher.newRandomSecret() : stored);
                int updated = boardMapper.updateCommentPasswordIfUnchanged(boardTitle, row.getCommentNum(), stored, hash);
                counters.record(updated, botRow);
            } catch (Exception e) {
                counters.failed++;
                log.error("비회원 댓글 비밀번호 승격 실패. board={}, commentNum={}", boardTitle, row.getCommentNum(), e);
            }
        }
    }

    private boolean isBotName(String writer) {
        return StringUtils.hasText(writer) && botPersonaNames.contains(writer.trim());
    }

    private static Set<String> collectPersonaNames(AssistantBotProperties botProperties) {
        Set<String> names = new HashSet<>();
        if (botProperties == null) {
            return names;
        }
        if (StringUtils.hasText(botProperties.getPersonaName())) {
            names.add(botProperties.getPersonaName().trim());
        }
        if (botProperties.getPersonas() != null) {
            for (AssistantBotProperties.PersonaProperties persona : botProperties.getPersonas()) {
                if (persona != null && StringUtils.hasText(persona.getName())) {
                    names.add(persona.getName().trim());
                }
            }
        }
        return names;
    }

    private static final class Counters {
        int candidates;
        int upgraded;
        int rekeyedBotRows;
        int skipped;
        int failed;
        boolean truncated;

        void record(int updatedRows, boolean botRow) {
            if (updatedRows == 1) {
                upgraded++;
                if (botRow) {
                    rekeyedBotRows++;
                }
            } else {
                // 그 사이 값이 바뀐 행. 다음 실행에서 다시 판단한다.
                skipped++;
            }
        }
    }

    /** 실행 요약. 비밀번호 값은 어떤 형태로도 포함하지 않는다. */
    public record Summary(Instant ranAt, int candidates, int upgraded, int rekeyedBotRows, int skipped, int failed,
                          boolean truncated, String error) {
    }
}
