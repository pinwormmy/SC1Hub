package com.sc1hub.board.support;

/**
 * 존재하지 않는 게시판 주소로 들어온 요청.
 * <p>
 * {@link IllegalArgumentException} 의 하위 타입이라 기존 catch 절과 컨트롤러별 핸들러는 그대로
 * 동작하고, 전역 핸들러만 이 타입을 따로 받아 404 + WARN 으로 처리한다. 크롤러가 삭제된
 * 게시판(supportboard 등)을 계속 두드리는 일상적 요청이라 ERROR 레벨로 남길 일이 아니다.
 */
public class InvalidBoardException extends IllegalArgumentException {

    public InvalidBoardException(String message) {
        super(message);
    }
}
