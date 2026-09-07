package com.sc1hub.member.service;

import com.sc1hub.board.dto.BoardListDTO;
import com.sc1hub.board.mapper.BoardMapper;
import com.sc1hub.board.support.BoardTitleNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 회원 삭제 전에 그 회원이 남긴 게시글 추천 기록을 모든 게시판에서 지우고 추천 수를 다시 맞춘다.
 * {@code *_recommend.user_id} 가 {@code member.id} 를 외래키로 참조하면서 삭제 연쇄가 없어, 추천을 한 번이라도
 * 한 회원은 탈퇴·관리자 삭제가 DB 오류로 실패하던 문제를 푼다.
 */
@Component
@Slf4j
public class MemberRecommendationCleanup {

    private final BoardMapper boardMapper;

    public MemberRecommendationCleanup(BoardMapper boardMapper) {
        this.boardMapper = boardMapper;
    }

    /** 지운 추천 기록 수를 돌려준다. */
    public int removeRecommendationsOf(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            return 0;
        }
        List<BoardListDTO> boards = boardMapper.getBoardList();
        if (boards == null) {
            return 0;
        }
        int removed = 0;
        for (BoardListDTO board : boards) {
            String boardTitle = BoardTitleNormalizer.normalizeNullable(board.getBoardTitle());
            if (boardTitle == null) {
                continue;
            }
            List<Integer> postNums = boardMapper.selectRecommendedPostNumsByUser(boardTitle, memberId);
            if (postNums == null || postNums.isEmpty()) {
                continue;
            }
            removed += boardMapper.deleteRecommendationsByUser(boardTitle, memberId);
            for (Integer postNum : postNums) {
                if (postNum != null) {
                    boardMapper.updateTotalRecommendCount(boardTitle, postNum);
                }
            }
        }
        if (removed > 0) {
            log.info("회원 삭제 전 추천 기록 {}건을 정리했습니다. memberId={}", removed, memberId);
        }
        return removed;
    }
}
