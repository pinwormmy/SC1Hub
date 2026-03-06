package com.sc1hub.board.service;

import com.sc1hub.assistant.search.AssistantSearchTermsService;
import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.dto.BoardListDTO;
import com.sc1hub.board.dto.CommentDTO;
import com.sc1hub.board.dto.LatestPostDTO;
import com.sc1hub.board.dto.RecommendDTO;
import com.sc1hub.board.mapper.BoardMapper;
import com.sc1hub.common.dto.PageDTO;
import com.sc1hub.common.util.PageUtils;
import com.sc1hub.member.dto.MemberDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.AccessDeniedException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
public class BoardServiceImpl implements BoardService {

    private static final String DEFAULT_BOARD_SEARCH_TYPE = "title";
    private static final int BOARD_DISPLAY_POST_LIMIT = 15;
    private static final int DEFAULT_PAGESET_LIMIT = 10;
    private static final Set<String> ADMIN_ONLY_BOARDS = new HashSet<>(Arrays.asList(
            "tvstboard", "tvszboard", "tvspboard",
            "zvstboard", "zvszboard", "zvspboard",
            "pvstboard", "pvszboard", "pvspboard",
            "teamplayguideboard", "noticeboard", "tipboard"
    ));

    private final BoardMapper boardMapper;
    private final AssistantSearchTermsService searchTermsService;
    private final UploadedImageDimensionInjector uploadedImageDimensionInjector;

    public BoardServiceImpl(
            BoardMapper boardMapper,
            AssistantSearchTermsService searchTermsService,
            UploadedImageDimensionInjector uploadedImageDimensionInjector) {
        this.boardMapper = boardMapper;
        this.searchTermsService = searchTermsService;
        this.uploadedImageDimensionInjector = uploadedImageDimensionInjector;
    }

    @Override
    public List<BoardDTO> showPostList(String boardTitle, PageDTO page) throws Exception {
        // log.debug("showPostList 작동 테스트");
        boardTitle = normalizeBoardTitle(boardTitle);
        return boardMapper.showPostList(boardTitle, page);
    }

    @Override
    public void submitPost(String boardTitle, BoardDTO board) throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        preparePostForPersistence(board);
        boardMapper.submitPost(boardTitle, board);
    }

    @Override
    public BoardDTO readPost(String boardTitle, int postNum) throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        BoardDTO post = boardMapper.readPost(boardTitle, postNum);
        enrichPostContent(post);
        return post;
    }

    @Override
    public void submitModifyPost(String boardTitle, BoardDTO post) throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        preparePostForPersistence(post);
        boardMapper.submitModifyPost(boardTitle, post);
    }

    @Override
    public void deletePost(String boardTitle, int postNum, MemberDTO requestingMember) throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
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
        boardTitle = normalizeBoardTitle(boardTitle);
        page = PageUtils.normalize(page, DEFAULT_BOARD_SEARCH_TYPE);
        return PageUtils.calculate(page, countTotalPost(boardTitle, page), BOARD_DISPLAY_POST_LIMIT, DEFAULT_PAGESET_LIMIT);
    }

    @Override
    public void addComment(String boardTitle, CommentDTO comment) throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        boardMapper.addComment(boardTitle, comment);
    }

    @Override
    public List<CommentDTO> showCommentList(String boardTitle, PageDTO page) throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        return boardMapper.showCommentList(boardTitle, page);
    }

    @Override
    public void deleteComment(String boardTitle, int commentNum) throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        boardMapper.deleteComment(boardTitle, commentNum);
    }

    @Override
    public void updateCommentCount(String boardTitle, int postNum) throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        boardMapper.updateCommentCount(boardTitle, postNum);
    }

    @Override
    public void updateViews(String boardTitle, int postNum) throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        boardMapper.updateViews(boardTitle, postNum);
    }

    @Override
    public int checkViewUserIp(String boardTitle, int postNum, String ip) throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        return boardMapper.checkViewUserIp(boardTitle, postNum, ip);
    }

    @Override
    public void saveViewUserIp(String boardTitle, int postNum, String ip) throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        boardMapper.saveViewUserIp(boardTitle, postNum, ip);
    }

    @Override
    public List<BoardDTO> showSelfNoticeList(String boardTitle) throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        return boardMapper.showSelfNoticeList(boardTitle);
    }

    @Override
    public int countTotalPost(String boardTitle, PageDTO page) throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        return boardMapper.countTotalPost(boardTitle, page);
    }

    @Override
    public int countTotalComment(String boardTitle, PageDTO page) throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        return boardMapper.countTotalComment(boardTitle, page);
    }

    @Override
    public int checkRecommendation(String boardTitle, RecommendDTO recommendDTO) {
        boardTitle = normalizeBoardTitle(boardTitle);
        return boardMapper.checkRecommendation(boardTitle, recommendDTO);
    }

    @Override
    @Transactional
    public void insertRecommendation(String boardTitle, RecommendDTO recommendDTO) {
        boardTitle = normalizeBoardTitle(boardTitle);
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
        boardTitle = normalizeBoardTitle(boardTitle);
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
        boardTitle = normalizeBoardTitle(boardTitle);
        int actualRecommendCount = getActualRecommendCount(boardTitle, postNum);
        int currentRecommendCount = getRecommendCount(boardTitle, postNum);

        if (actualRecommendCount != currentRecommendCount) {
            updateTotalRecommendCount(boardTitle, postNum);
        }
    }

    public void updateTotalRecommendCount(String boardTitle, int postNum) {
        boardTitle = normalizeBoardTitle(boardTitle);
        boardMapper.updateTotalRecommendCount(boardTitle, postNum);
    }

    @Override
    public int getRecommendCount(String boardTitle, int postNum) {
        boardTitle = normalizeBoardTitle(boardTitle);
        return boardMapper.getRecommendCount(boardTitle, postNum);
    }

    @Override
    public int getActualRecommendCount(String boardTitle, int postNum) {
        boardTitle = normalizeBoardTitle(boardTitle);
        return boardMapper.getActualRecommendCount(boardTitle, postNum);
    }

    @Override
    public PageDTO commentPageSetting(String boardTitle, PageDTO page) throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        page = PageUtils.normalize(page, DEFAULT_BOARD_SEARCH_TYPE);
        return PageUtils.calculate(page, countTotalComment(boardTitle, page), BOARD_DISPLAY_POST_LIMIT, DEFAULT_PAGESET_LIMIT);
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
        return boardMapper.getKoreanTitle(normalizeBoardTitle(boardTitle));
    }

    @Override
    public List<LatestPostDTO> showLatestPosts() {
        return boardMapper.showLatestPosts();
    }

    @Override
    public List<BoardDTO> getPopularPosts(String boardTitle, int limit) throws Exception {
        if (limit < 1) {
            return Collections.emptyList();
        }
        if (boardTitle == null) {
            return Collections.emptyList();
        }
        boardTitle = normalizeBoardTitle(boardTitle);
        return boardMapper.selectPopularPosts(boardTitle, limit);
    }

    // 나머지 유틸리티 메서드들
    private void preparePostForPersistence(BoardDTO post) {
        if (post == null) {
            return;
        }

        String content = uploadedImageDimensionInjector.injectMissingDimensions(post.getContent());
        post.setContent(content);
        post.setSearchTerms(searchTermsService.buildSearchTerms(post.getTitle(), content));
    }

    private void enrichPostContent(BoardDTO post) {
        if (post == null) {
            return;
        }

        post.setContent(uploadedImageDimensionInjector.injectMissingDimensions(post.getContent()));
    }

    @Override
    public boolean canWrite(String boardTitle, MemberDTO member) {
        if (member == null) {
            return false;
        }

        String normalizedBoardTitle = normalizeBoardTitle(boardTitle);
        if (normalizedBoardTitle == null) {
            return false;
        }

        return !ADMIN_ONLY_BOARDS.contains(normalizedBoardTitle) || member.getGrade() == 3;
    }

    @Override
    @Transactional
    public void movePost(String boardTitle, int postNum, String targetBoardTitle) throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
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
        boardTitle = normalizeBoardTitle(boardTitle);
        if (boardMapper.checkViewUserIp(boardTitle, postNum, ip) == 0) {
            boardMapper.saveViewUserIp(boardTitle, postNum, ip);
            boardMapper.updateViews(boardTitle, postNum);
        }
    }

    private String normalizeBoardTitle(String boardTitle) {
        if (boardTitle == null) {
            return null;
        }
        return boardTitle.trim().toLowerCase(Locale.ROOT);
    }
}
