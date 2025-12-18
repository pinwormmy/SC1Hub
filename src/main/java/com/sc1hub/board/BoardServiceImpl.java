package com.sc1hub.board;

import com.sc1hub.mapper.BoardMapper;
import com.sc1hub.member.MemberDTO;
import com.sc1hub.common.dto.PageDTO;
import com.sc1hub.common.util.PageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.AccessDeniedException;
import java.util.List;

@Slf4j
@Service
public class BoardServiceImpl implements BoardService {

    @Autowired
    BoardMapper boardMapper;

    @Override
    public List<BoardDTO> showPostList(String boardTitle, PageDTO page) throws Exception {
        // log.debug("showPostList 작동 테스트");
        boardTitle = boardTitle.toLowerCase();
        return boardMapper.showPostList(boardTitle, page);
    }

    @Override
    public void submitPost(String boardTitle, BoardDTO board) throws Exception {
        boardTitle = boardTitle.toLowerCase();
        boardMapper.submitPost(boardTitle, board);
    }

    @Override
    public BoardDTO readPost(String boardTitle, int postNum) throws Exception {
        boardTitle = boardTitle.toLowerCase();
        return boardMapper.readPost(boardTitle, postNum);
    }

    @Override
    public void submitModifyPost(String boardTitle, BoardDTO post) throws Exception {
        boardTitle = boardTitle.toLowerCase();
        boardMapper.submitModifyPost(boardTitle, post);
    }

    @Override
    public void deletePost(String boardTitle, int postNum, MemberDTO requestingMember) throws Exception {
        boardTitle = boardTitle.toLowerCase();
        BoardDTO postToDelete = boardMapper.readPost(boardTitle, postNum);
        if (postToDelete == null) {
            throw new IllegalArgumentException("존재하지 않는 게시글입니다.");
        }
        if (!postToDelete.getWriter().equals(requestingMember.getNickName())
                && !requestingMember.getId().equals("admin")) {
            throw new AccessDeniedException("삭제 권한이 없습니다.");
        }
        boardMapper.deletePost(boardTitle, postNum);
    }

    @Override
    public PageDTO pageSetting(String boardTitle, PageDTO page) throws Exception {
        boardTitle = boardTitle.toLowerCase();
        checkPageAndKeyword(page);
        return utilLoadingForPage(boardTitle, page);
    }

    @Override
    public void addComment(String boardTitle, CommentDTO comment) throws Exception {
        boardTitle = boardTitle.toLowerCase();
        boardMapper.addComment(boardTitle, comment);
    }

    @Override
    public List<CommentDTO> showCommentList(String boardTitle, PageDTO page) throws Exception {
        boardTitle = boardTitle.toLowerCase();
        return boardMapper.showCommentList(boardTitle, page);
    }

    @Override
    public void deleteComment(String boardTitle, int commentNum) throws Exception {
        boardTitle = boardTitle.toLowerCase();
        boardMapper.deleteComment(boardTitle, commentNum);
    }

    @Override
    public void updateCommentCount(String boardTitle, int postNum) throws Exception {
        boardTitle = boardTitle.toLowerCase();
        boardMapper.updateCommentCount(boardTitle, postNum);
    }

    @Override
    public void updateViews(String boardTitle, int postNum) throws Exception {
        boardTitle = boardTitle.toLowerCase();
        boardMapper.updateViews(boardTitle, postNum);
    }

    @Override
    public int checkViewUserIp(String boardTitle, int postNum, String ip) throws Exception {
        boardTitle = boardTitle.toLowerCase();
        return boardMapper.checkViewUserIp(boardTitle, postNum, ip);
    }

    @Override
    public void saveViewUserIp(String boardTitle, int postNum, String ip) throws Exception {
        boardTitle = boardTitle.toLowerCase();
        boardMapper.saveViewUserIp(boardTitle, postNum, ip);
    }

    @Override
    public List<BoardDTO> showSelfNoticeList(String boardTitle) throws Exception {
        boardTitle = boardTitle.toLowerCase();
        return boardMapper.showSelfNoticeList(boardTitle);
    }

    @Override
    public int countTotalPost(String boardTitle, PageDTO page) throws Exception {
        boardTitle = boardTitle.toLowerCase();
        return boardMapper.countTotalPost(boardTitle, page);
    }

    @Override
    public int countTotalComment(String boardTitle, PageDTO page) throws Exception {
        boardTitle = boardTitle.toLowerCase();
        return boardMapper.countTotalComment(boardTitle, page);
    }

    @Override
    public int checkRecommendation(String boardTitle, RecommendDTO recommendDTO) {
        boardTitle = boardTitle.toLowerCase();
        return boardMapper.checkRecommendation(boardTitle, recommendDTO);
    }

    @Override
    @Transactional
    public void insertRecommendation(String boardTitle, RecommendDTO recommendDTO) {
        boardTitle = boardTitle.toLowerCase();
        // 1. 사용자가 이미 해당 게시글을 추천했는지 확인
        int count = boardMapper.checkRecommendation(boardTitle, recommendDTO);
        if (count == 0) {
            // 2. 추천하지 않았다면, 추천 테이블에 데이터를 추가
            boardMapper.insertRecommendation(boardTitle, recommendDTO);
            // 3. 게시글의 총 추천 수를 갱신
            updateRecommendCount(boardTitle, recommendDTO.getPostNum());
        } else {
            throw new RuntimeException("이미 추천한 게시글입니다.");
        }
    }

    @Override
    @Transactional
    public void deleteRecommendation(String boardTitle, RecommendDTO recommendDTO) {
        boardTitle = boardTitle.toLowerCase();
        // 1. 사용자가 이미 추천을 했는지 확인
        int recommendCount = boardMapper.checkRecommendation(boardTitle, recommendDTO);
        if (recommendCount == 0) {
            throw new RuntimeException("해당 게시글에 대한 추천이 없습니다.");
        }
        // 2. 추천을 했다면, 해당 추천을 데이터베이스에서 삭제
        boardMapper.deleteRecommendation(boardTitle, recommendDTO);
        // 3. 게시글의 총 추천 수를 갱신
        updateRecommendCount(boardTitle, recommendDTO.getPostNum());
    }

    private void updateRecommendCount(String boardTitle, int postNum) {
        boardTitle = boardTitle.toLowerCase();
        int actualRecommendCount = getActualRecommendCount(boardTitle, postNum);
        int currentRecommendCount = getRecommendCount(boardTitle, postNum);

        if (actualRecommendCount != currentRecommendCount) {
            updateTotalRecommendCount(boardTitle, postNum);
        }
    }

    public void updateTotalRecommendCount(String boardTitle, int postNum) {
        boardTitle = boardTitle.toLowerCase();
        boardMapper.updateTotalRecommendCount(boardTitle, postNum);
    }

    @Override
    public int getRecommendCount(String boardTitle, int postNum) {
        boardTitle = boardTitle.toLowerCase();
        return boardMapper.getRecommendCount(boardTitle, postNum);
    }

    @Override
    public int getActualRecommendCount(String boardTitle, int postNum) {
        boardTitle = boardTitle.toLowerCase();
        return boardMapper.getActualRecommendCount(boardTitle, postNum);
    }

    @Override
    public PageDTO commentPageSetting(String boardTitle, PageDTO page) throws Exception {
        boardTitle = boardTitle.toLowerCase();
        checkPageAndKeyword(page);
        return utilLoadingForCommentPage(boardTitle, page);
    }

    @Override
    public List<BoardListDTO> getBoardList() {
        return boardMapper.getBoardList();
    }

    @Override
    public String getKoreanTitle(String boardTitle) {
        if (boardTitle == null) {
            return null;
        }
        return boardMapper.getKoreanTitle(boardTitle.toLowerCase());
    }

    @Override
    public List<LatestPostDTO> showLatestPosts() {
        return boardMapper.showLatestPosts();
    }

    // 나머지 유틸리티 메서드들
    protected void checkPageAndKeyword(PageDTO page) {
        if (page.getRecentPage() < 1) {
            page.setRecentPage(1);
        }
        if (page.getSearchType() == null) {
            page.setSearchType("title");
        }
        if (page.getKeyword() == null) {
            page.setKeyword("");
        }
    }

    protected PageDTO utilLoadingForPage(String boardTitle, PageDTO page) throws Exception {
        boardTitle = boardTitle.toLowerCase();
        page.setTotalPostCount(countTotalPost(boardTitle, page));
        return initPageUtil().calculatePage(page);
    }

    protected PageDTO utilLoadingForCommentPage(String boardTitle, PageDTO page) throws Exception {
        boardTitle = boardTitle.toLowerCase();
        page.setTotalPostCount(countTotalComment(boardTitle, page));
        return initPageUtil().calculatePage(page);
    }

    protected PageService initPageUtil() {
        PageService util = new PageService();
        util.setDISPLAY_POST_LIMIT(15); // 한 페이지 내 게시물 갯수 변경
        util.setPAGESET_LIMIT(10);
        return util;
    }

    private static final String[] ADMIN_ONLY_BOARDS = {
            "tVsTBoard", "tVsZBoard", "tVsPBoard",
            "zVsTBoard", "zVsZBoard", "zVsPBoard",
            "pVsTBoard", "pVsZBoard", "pVsPBoard",
            "teamPlayGuideBoard", "noticeBoard", "tipBoard"
    };

    @Override
    public boolean canWrite(String boardTitle, MemberDTO member) {
        if (member == null)
            return false;

        for (String adminBoard : ADMIN_ONLY_BOARDS) {
            if (boardTitle.equalsIgnoreCase(adminBoard)) {
                return member.getGrade() == 3;
            }
        }
        return true;
    }

    @Override
    @Transactional
    public void movePost(String boardTitle, int postNum, String targetBoardTitle) throws Exception {
        boardTitle = boardTitle.toLowerCase();
        BoardDTO originalPost = readPost(boardTitle, postNum);
        String originalContent = originalPost.getContent();

        // 1. 원본 게시글의 내용을 수정합니다.
        String newContent = "이 게시글은 " + getKoreanTitle(targetBoardTitle) + "으로 이동되었습니다.";
        originalPost.setContent(newContent);
        submitModifyPost(boardTitle, originalPost);

        // 2. 새 게시판(targetBoardTitle)으로 게시글을 복사합니다.
        BoardDTO newPost = new BoardDTO();
        newPost.setTitle(originalPost.getTitle());
        newPost.setContent(originalContent); // 원본 내용을 그대로 사용
        newPost.setWriter(originalPost.getWriter());
        newPost.setRegDate(originalPost.getRegDate());
        newPost.setViews(originalPost.getViews());
        newPost.setCommentCount(originalPost.getCommentCount());
        newPost.setNotice(originalPost.getNotice());

        submitPost(targetBoardTitle, newPost);
    }

    @Override
    public void increaseViewCount(String boardTitle, int postNum, String ip) throws Exception {
        boardTitle = boardTitle.toLowerCase();
        if (checkViewUserIp(boardTitle, postNum, ip) == 0) {
            saveViewUserIp(boardTitle, postNum, ip);
            updateViews(boardTitle, postNum);
        }
    }
}
