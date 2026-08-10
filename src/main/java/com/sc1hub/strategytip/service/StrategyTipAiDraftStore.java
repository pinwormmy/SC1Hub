package com.sc1hub.strategytip.service;

import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.strategytip.ai.StrategyTipSourceCatalog;
import com.sc1hub.strategytip.dto.StrategyTipAiDailyRunDTO;
import com.sc1hub.strategytip.dto.StrategyTipAiDraftDTO;
import com.sc1hub.strategytip.dto.StrategyTipDTO;
import com.sc1hub.strategytip.mapper.StrategyTipAiMapper;
import com.sc1hub.strategytip.mapper.StrategyTipMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class StrategyTipAiDraftStore {

    private final StrategyTipAiMapper strategyTipAiMapper;
    private final StrategyTipMapper strategyTipMapper;

    public StrategyTipAiDraftStore(StrategyTipAiMapper strategyTipAiMapper,
                                   StrategyTipMapper strategyTipMapper) {
        this.strategyTipAiMapper = strategyTipAiMapper;
        this.strategyTipMapper = strategyTipMapper;
    }

    public int countGeneratedOn(LocalDate generationDate) {
        return strategyTipAiMapper.countDraftsByGenerationDate(generationDate);
    }

    public int countPending() {
        return strategyTipAiMapper.countPendingDrafts();
    }

    public List<Integer> getUsedSlots(LocalDate generationDate) {
        List<Integer> slots = strategyTipAiMapper.selectUsedSlots(generationDate);
        return slots == null ? Collections.emptyList() : slots;
    }

    public List<StrategyTipAiDraftDTO> getPendingDrafts() {
        List<StrategyTipAiDraftDTO> drafts = strategyTipAiMapper.selectPendingDrafts();
        return drafts == null ? Collections.emptyList() : drafts;
    }

    public List<StrategyTipAiDraftDTO> getRecentDrafts(int limit) {
        List<StrategyTipAiDraftDTO> drafts = strategyTipAiMapper.selectRecentDrafts(Math.max(1, Math.min(limit, 100)));
        return drafts == null ? Collections.emptyList() : drafts;
    }

    public StrategyTipAiDraftDTO getPendingDraft(long draftId) {
        return strategyTipAiMapper.selectPendingDraft(draftId);
    }

    public List<String> getRecentContents(int limit) {
        List<String> contents = strategyTipAiMapper.selectRecentDraftAndPublishedContents(
                Math.max(1, Math.min(limit, 200)));
        return contents == null ? Collections.emptyList() : contents;
    }

    public List<String> getRecentPublishedContents(int limit) {
        List<String> contents = strategyTipAiMapper.selectRecentPublishedContents(
                Math.max(1, Math.min(limit, 200)));
        return contents == null ? Collections.emptyList() : contents;
    }

    public List<BoardDTO> getSourcePosts(String boardTitle, int limit) {
        if (!isAllowedSourceBoard(boardTitle)) {
            throw new IllegalArgumentException("허용되지 않은 공략 출처입니다.");
        }
        List<BoardDTO> posts = strategyTipAiMapper.selectSourcePosts(boardTitle,
                Math.max(1, Math.min(limit, 10)));
        return posts == null ? Collections.emptyList() : posts;
    }

    public StrategyTipAiDailyRunDTO getDailyRun(LocalDate generationDate) {
        return strategyTipAiMapper.selectDailyRun(generationDate);
    }

    @Transactional
    public int claimDailyApiCall(LocalDate generationDate, int maxDailyApiCalls,
                                 LocalDateTime staleBefore) {
        strategyTipAiMapper.insertDailyRunIfAbsent(generationDate);
        if (strategyTipAiMapper.claimDailyRun(generationDate,
                Math.max(1, maxDailyApiCalls), staleBefore) != 1) {
            return 0;
        }
        StrategyTipAiDailyRunDTO run = strategyTipAiMapper.selectDailyRun(generationDate);
        if (run == null || !"RUNNING".equals(run.getLastStatus()) || run.getApiCallCount() < 1) {
            throw new IllegalStateException("AI 한줄 공략 실행 소유권을 확인하지 못했습니다.");
        }
        return run.getApiCallCount();
    }

    @Transactional
    public void saveGeneratedDrafts(LocalDate generationDate, int attemptNo,
                                    List<StrategyTipAiDraftDTO> drafts, int inputTokens,
                                    int outputTokens, int searchQueryCount) {
        if (drafts == null || drafts.isEmpty()) {
            throw new IllegalArgumentException("저장할 AI 한줄 공략 초안이 없습니다.");
        }
        for (StrategyTipAiDraftDTO draft : drafts) {
            strategyTipAiMapper.insertDraft(draft);
        }
        int updated = strategyTipAiMapper.completeDailyRun(generationDate, attemptNo,
                Math.max(0, inputTokens), Math.max(0, outputTokens),
                Math.max(0, searchQueryCount));
        if (updated != 1) {
            throw new IllegalStateException("AI 한줄 공략 생성 실행 상태를 완료하지 못했습니다.");
        }
    }

    public void failDailyRun(LocalDate generationDate, int attemptNo, String errorMessage,
                             int inputTokens, int outputTokens, int searchQueryCount) {
        int updated = strategyTipAiMapper.failDailyRun(
                generationDate, attemptNo, truncate(errorMessage, 500),
                Math.max(0, inputTokens), Math.max(0, outputTokens),
                Math.max(0, searchQueryCount));
        if (updated != 1) {
            throw new IllegalStateException("AI 한줄 공략 실패 상태를 기록하지 못했습니다.");
        }
    }

    @Transactional
    public int approve(long draftId, String category, String content,
                       String reviewedBy, String publicWriter) {
        if (strategyTipAiMapper.claimDraftForApproval(draftId, reviewedBy) != 1) {
            throw new IllegalArgumentException("이미 처리되었거나 존재하지 않는 초안입니다.");
        }

        StrategyTipAiDraftDTO draft = strategyTipAiMapper.selectDraft(draftId);
        if (draft == null || !"APPROVING".equals(draft.getStatus())) {
            throw new IllegalStateException("승인할 초안을 불러오지 못했습니다.");
        }
        if (!category.equals(draft.getCategory())) {
            throw new IllegalArgumentException("근거와 연결된 초안 분류는 변경할 수 없습니다.");
        }

        StrategyTipDTO published = new StrategyTipDTO();
        published.setCategory(category);
        published.setContent(content);
        published.setWriter(publicWriter);
        published.setMemberId(reviewedBy);
        published.setGuestPassword(null);
        strategyTipMapper.insertTip(published);
        if (published.getTipNum() < 1) {
            throw new IllegalStateException("승인된 한줄 공략의 번호를 확인하지 못했습니다.");
        }

        int completed = strategyTipAiMapper.completeDraftApproval(
                draftId, category, content, published.getTipNum());
        if (completed != 1) {
            throw new IllegalStateException("AI 한줄 공략 승인 상태를 완료하지 못했습니다.");
        }
        return published.getTipNum();
    }

    public void reject(long draftId, String reviewedBy) {
        if (strategyTipAiMapper.rejectDraft(draftId, reviewedBy) != 1) {
            throw new IllegalArgumentException("이미 처리되었거나 존재하지 않는 초안입니다.");
        }
    }

    private boolean isAllowedSourceBoard(String boardTitle) {
        if (boardTitle == null) {
            return false;
        }
        for (StrategyTipSourceCatalog.Entry entry : StrategyTipSourceCatalog.entries()) {
            if (boardTitle.equals(entry.getBoardTitle())) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
