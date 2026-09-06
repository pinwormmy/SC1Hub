package com.sc1hub.member.service;

import com.sc1hub.board.dto.BoardListDTO;
import com.sc1hub.board.mapper.BoardMapper;
import com.sc1hub.board.support.BoardTitleNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 게시글 관리 권한이 작성자 닉네임 문자열로 판정되는 동안, 과거에 회원 게시글 작성에 쓰인 닉네임을
 * 다른 계정이 새로 취득하지 못하게 막는다(닉네임 변경·탈퇴로 해제된 닉네임을 통한 과거 글 관리 권한
 * 취득 차단). 게시판 수와 글 수가 작아 게시판별 COUNT 로 충분하다.
 */
@Component
@Slf4j
public class WriterNicknameGuard {

    private final BoardMapper boardMapper;

    public WriterNicknameGuard(BoardMapper boardMapper) {
        this.boardMapper = boardMapper;
    }

    /** 해당 닉네임으로 작성된 회원 게시글(비회원 글 제외)이 하나라도 있으면 true. */
    public boolean hasAuthoredPosts(String nickname) {
        if (!StringUtils.hasText(nickname)) {
            return false;
        }
        String writer = nickname.trim();
        List<BoardListDTO> boards = boardMapper.getBoardList();
        if (boards == null) {
            return false;
        }
        for (BoardListDTO board : boards) {
            String boardTitle = BoardTitleNormalizer.normalizeNullable(board.getBoardTitle());
            if (boardTitle == null) {
                continue;
            }
            try {
                if (boardMapper.countMemberPostsByWriter(boardTitle, writer) > 0) {
                    return true;
                }
            } catch (Exception e) {
                // 개별 게시판 조회 실패가 가입·수정 전체를 막지 않도록 해당 게시판만 건너뛰고 기록한다.
                log.error("작성자 닉네임 확인 실패. boardTitle={}", boardTitle, e);
            }
        }
        return false;
    }
}
