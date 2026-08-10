package com.sc1hub.strategytip.mapper;

import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.strategytip.dto.StrategyTipAiDailyRunDTO;
import com.sc1hub.strategytip.dto.StrategyTipAiDraftDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface StrategyTipAiMapper {

    int insertDailyRunIfAbsent(@Param("generationDate") LocalDate generationDate);

    int claimDailyRun(@Param("generationDate") LocalDate generationDate,
                      @Param("maxDailyApiCalls") int maxDailyApiCalls,
                      @Param("staleBefore") LocalDateTime staleBefore);

    int completeDailyRun(@Param("generationDate") LocalDate generationDate,
                         @Param("attemptNo") int attemptNo,
                         @Param("inputTokens") int inputTokens,
                         @Param("outputTokens") int outputTokens,
                         @Param("searchQueryCount") int searchQueryCount);

    int failDailyRun(@Param("generationDate") LocalDate generationDate,
                     @Param("attemptNo") int attemptNo,
                     @Param("lastError") String lastError,
                     @Param("inputTokens") int inputTokens,
                     @Param("outputTokens") int outputTokens,
                     @Param("searchQueryCount") int searchQueryCount);

    StrategyTipAiDailyRunDTO selectDailyRun(@Param("generationDate") LocalDate generationDate);

    int countDraftsByGenerationDate(@Param("generationDate") LocalDate generationDate);

    int countPendingDrafts();

    List<Integer> selectUsedSlots(@Param("generationDate") LocalDate generationDate);

    List<String> selectUsedCategories(@Param("generationDate") LocalDate generationDate);

    void insertDraft(StrategyTipAiDraftDTO draft);

    List<StrategyTipAiDraftDTO> selectPendingDrafts();

    List<StrategyTipAiDraftDTO> selectRecentDrafts(@Param("limit") int limit);

    StrategyTipAiDraftDTO selectPendingDraft(@Param("draftId") long draftId);

    int claimDraftForApproval(@Param("draftId") long draftId,
                              @Param("reviewedBy") String reviewedBy);

    StrategyTipAiDraftDTO selectDraft(@Param("draftId") long draftId);

    int completeDraftApproval(@Param("draftId") long draftId,
                              @Param("category") String category,
                              @Param("content") String content,
                              @Param("publishedTipNum") int publishedTipNum);

    int rejectDraft(@Param("draftId") long draftId,
                    @Param("reviewedBy") String reviewedBy);

    List<String> selectRecentDraftAndPublishedContents(@Param("limit") int limit);

    List<String> selectRecentPublishedContents(@Param("limit") int limit);

    List<BoardDTO> selectSourcePosts(@Param("boardTitle") String boardTitle,
                                     @Param("limit") int limit);
}
