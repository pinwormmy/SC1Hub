package com.sc1hub.board.controller;

import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.dto.BoardListDataDTO;
import com.sc1hub.board.dto.BoardListDTO;
import com.sc1hub.board.dto.CommentDTO;
import com.sc1hub.board.dto.LatestPostDTO;
import com.sc1hub.board.dto.RecommendDTO;
import com.sc1hub.board.service.BoardService;
import com.sc1hub.board.support.BoardTitleNormalizer;
import com.sc1hub.common.dto.PageDTO;
import com.sc1hub.common.exception.ResourceNotFoundException;
import com.sc1hub.common.security.AttackContentDetector;
import com.sc1hub.common.security.DuplicateContentGuard;
import com.sc1hub.common.security.OffenderTracker;
import com.sc1hub.common.util.IpService;
import com.sc1hub.member.dto.MemberDTO;
import com.sc1hub.member.service.MemberService;
import com.sc1hub.seo.SeoMetadataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.nio.file.AccessDeniedException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Controller
@Slf4j
@RequestMapping("/boards")
public class BoardController {

    private static final String ADMIN_ID = "admin";
    private static final String GUEST_WRITABLE_BOARD = "funboard";
    private static final String GUEST_POST_AUTH_SESSION_KEY = "authorizedGuestPostKeys";
    private static final String GUEST_NICKNAME_CONFLICT_MESSAGE = "기존 가입자 닉네임은 비회원이 사용할 수 없습니다.";
    private static final String INVALID_COMMENT_MESSAGE = "댓글 내용을 확인해주세요.";
    private static final String GUEST_COMMENT_CREDENTIALS_MESSAGE = "닉네임과 비밀번호를 확인해주세요.";
    private static final int COMMENT_CONTENT_MAX_LENGTH = 500;
    private static final int COMMENT_NICKNAME_MAX_LENGTH = 50;
    private static final int COMMENT_PASSWORD_MAX_LENGTH = 100;

    private static final String DUPLICATE_POST_MESSAGE = "같은 내용의 글을 짧은 시간에 반복 등록할 수 없습니다.";
    private static final String DUPLICATE_COMMENT_MESSAGE = "같은 내용의 댓글을 짧은 시간에 반복 등록할 수 없습니다.";
    private static final int DUPLICATE_POST_MIN_LENGTH = 30;
    private static final int DUPLICATE_COMMENT_MIN_LENGTH = 20;
    private static final long DUPLICATE_POST_WINDOW_MILLIS = 10 * 60 * 1000L;
    private static final long DUPLICATE_COMMENT_WINDOW_MILLIS = 5 * 60 * 1000L;
    private static final String ATTACK_CONTENT_MESSAGE = "허용되지 않는 내용(스크립트·주입 구문·과다 링크 등)이 포함되어 있어 등록할 수 없습니다.";
    private static final int POST_MAX_LINKS = 5;
    private static final int COMMENT_MAX_LINKS = 3;

    private final BoardService boardService;
    private final MemberService memberService;
    private final SeoMetadataService seoMetadataService;
    private final DuplicateContentGuard duplicateContentGuard;
    private final AttackContentDetector attackContentDetector;
    private final OffenderTracker offenderTracker;

    public BoardController(BoardService boardService, MemberService memberService,
                           SeoMetadataService seoMetadataService, DuplicateContentGuard duplicateContentGuard,
                           AttackContentDetector attackContentDetector, OffenderTracker offenderTracker) {
        this.boardService = boardService;
        this.memberService = memberService;
        this.seoMetadataService = seoMetadataService;
        this.duplicateContentGuard = duplicateContentGuard;
        this.attackContentDetector = attackContentDetector;
        this.offenderTracker = offenderTracker;
    }

    @GetMapping(value = "/{boardTitle}")
    public String list(@PathVariable String boardTitle, PageDTO page, Model model, HttpSession session,
                       HttpServletRequest request)
            throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        String koreanTitle = boardService.getKoreanTitle(boardTitle);
        requireBoard(koreanTitle);
        model.addAttribute("koreanTitle", koreanTitle);
        model.addAttribute("boardTitle", boardTitle);
        PageDTO resolvedPage = boardService.pageSetting(boardTitle, page);
        model.addAttribute("page", resolvedPage);
        model.addAttribute("selfNoticeList", boardService.showSelfNoticeList(boardTitle));
        model.addAttribute("postList", boardService.showPostList(boardTitle, page));

        model.addAttribute("canWrite", canWrite(boardTitle, session));
        seoMetadataService.applyBoardList(model, request, koreanTitle, resolvedPage);

        return "board/postList";
    }

    @GetMapping("/{boardTitle}/listData")
    @ResponseBody
    public BoardListDataDTO listData(@PathVariable String boardTitle, PageDTO page, HttpSession session)
            throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        BoardListDataDTO response = new BoardListDataDTO();
        response.setBoardTitle(boardTitle);
        String koreanTitle = boardService.getKoreanTitle(boardTitle);
        requireBoard(koreanTitle);
        response.setKoreanTitle(koreanTitle);
        response.setPage(boardService.pageSetting(boardTitle, page));
        response.setSelfNoticeList(boardService.showSelfNoticeList(boardTitle));
        response.setPostList(boardService.showPostList(boardTitle, page));

        response.setCanWrite(canWrite(boardTitle, session));

        return response;
    }

    @GetMapping("/{boardTitle}/readPost")
    public String readPost(@PathVariable String boardTitle, @RequestParam int postNum,
                           Model model, HttpServletRequest request) throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        String koreanTitle = boardService.getKoreanTitle(boardTitle);
        requireBoard(koreanTitle);
        BoardDTO post = boardService.readPost(boardTitle, postNum);
        if (post == null) {
            throw new ResourceNotFoundException("존재하지 않는 게시글입니다.");
        }
        String ip = IpService.getRemoteIP(request);
        boardService.increaseViewCount(boardTitle, postNum, ip);
        model.addAttribute("koreanTitle", koreanTitle);
        model.addAttribute("boardTitle", boardTitle);
        model.addAttribute("guestWritable", isGuestWritableBoard(boardTitle));
        model.addAttribute("post", post);
        seoMetadataService.applyPost(model, request, koreanTitle, post);
        return "board/readPost";
    }

    @GetMapping("/{boardTitle}/postData")
    @ResponseBody
    public BoardDTO postData(@PathVariable String boardTitle, @RequestParam int postNum, HttpServletRequest request)
            throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        String ip = IpService.getRemoteIP(request);
        boardService.increaseViewCount(boardTitle, postNum, ip);
        return boardService.readPost(boardTitle, postNum);
    }

    @GetMapping("/{boardTitle}/writePost")
    public String writePost(@PathVariable String boardTitle, Model model, HttpServletRequest request) {
        boardTitle = normalizeBoardTitle(boardTitle);
        log.debug("세션 만료 시간 : {}", request.getSession().getMaxInactiveInterval());
        String koreanTitle = boardService.getKoreanTitle(boardTitle);
        model.addAttribute("koreanTitle", koreanTitle);
        model.addAttribute("boardTitle", boardTitle);
        model.addAttribute("guestWritable", isGuestWritableBoard(boardTitle));
        return "board/writePost";
    }

    @PostMapping("/{boardTitle}/submitPost")
    public String submitPost(@PathVariable String boardTitle, BoardDTO post, HttpServletRequest request, Model model)
            throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        MemberDTO member = getMember(request.getSession());
        if (!preparePostForSubmission(boardTitle, post, member)) {
            model.addAttribute("msg", buildSubmitDeniedMessage(boardTitle));
            model.addAttribute("url", buildSubmitDeniedUrl(boardTitle));
            return "alert";
        }
        if (member == null && isRegisteredMemberNickname(post.getWriter())) {
            model.addAttribute("msg", GUEST_NICKNAME_CONFLICT_MESSAGE);
            model.addAttribute("url", buildSubmitDeniedUrl(boardTitle));
            return "alert";
        }
        // 공격 의도(스크립트·주입 구문·링크 도배)가 확인되면 거부하고 자동 제재를 건다(관리자 제외).
        if (!isExemptFromContentSanctions(member) && rejectsAttack(request, member,
                attackContentDetector.inspect(POST_MAX_LINKS, post.getTitle(), post.getContent(), post.getWriter()))) {
            model.addAttribute("msg", ATTACK_CONTENT_MESSAGE);
            model.addAttribute("url", buildSubmitDeniedUrl(boardTitle));
            return "alert";
        }
        // 도배 방지: 누가 올리든 같은 내용의 글은 짧은 시간에 한 번만 받는다(관리자 제외).
        if (!isAdmin(member) && duplicateContentGuard.isDuplicate("post", null,
                post.getTitle() + "\n" + post.getContent(), DUPLICATE_POST_MIN_LENGTH, DUPLICATE_POST_WINDOW_MILLIS)) {
            model.addAttribute("msg", DUPLICATE_POST_MESSAGE);
            model.addAttribute("url", buildSubmitDeniedUrl(boardTitle));
            return "alert";
        }

        if (!isAdmin(member)) {
            post.setNotice(0);
        }

        boardService.submitPost(boardTitle, post);
        return "redirect:/boards/" + boardTitle;
    }

    @PostMapping("/{boardTitle}/deletePost")
    public String deletePost(@PathVariable String boardTitle, BoardDTO post, HttpServletRequest request,
            RedirectAttributes redirectAttributes) throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        HttpSession session = request.getSession();
        MemberDTO member = getMember(session);
        BoardDTO existingPost = boardService.readPost(boardTitle, post.getPostNum());
        if (existingPost == null) {
            redirectAttributes.addFlashAttribute("msg", "존재하지 않는 게시글입니다.");
            return "redirect:/boards/" + boardTitle;
        }

        try {
            if (!canDeletePost(boardTitle, existingPost, member, post.getGuestPassword())) {
                redirectAttributes.addFlashAttribute("msg", buildDeleteDeniedMessage(existingPost));
                return "redirect:/boards/" + boardTitle + "/readPost?postNum=" + existingPost.getPostNum();
            }

            if (isGuestPost(existingPost) && !isAdmin(member)) {
                boardService.deletePost(boardTitle, existingPost.getPostNum());
            } else {
                boardService.deletePost(boardTitle, existingPost.getPostNum(), member);
            }
            clearGuestPostAuthorization(session, boardTitle, existingPost.getPostNum());
            return "redirect:/boards/" + boardTitle;
        } catch (AccessDeniedException e) {
            log.warn("삭제 권한 오류 발생 - 유저 ID: {}", member == null ? null : member.getId());
            redirectAttributes.addFlashAttribute("msg", "삭제 권한이 없습니다.");
            return "redirect:/";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("msg", e.getMessage());
            return "redirect:/";
        }
    }

    @PostMapping("/{boardTitle}/verifyGuestPostPassword")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verifyGuestPostPassword(@PathVariable String boardTitle,
            @RequestParam int postNum, @RequestParam String guestPassword, HttpSession session) throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        Map<String, Object> response = new HashMap<>();
        BoardDTO post = boardService.readPost(boardTitle, postNum);
        if (!isGuestWritableBoard(boardTitle) || !isGuestPost(post)) {
            response.put("valid", false);
            response.put("message", "존재하지 않는 게시글입니다.");
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }

        boolean valid = guestPasswordMatches(post, guestPassword);
        response.put("valid", valid);
        if (valid) {
            // 성공한 검증만 서버 세션에 수정 권한을 부여한다. 이후 수정 화면은 URL 비밀번호가 아니라
            // 이 세션 권한으로만 접근하므로 GET 으로 비밀번호를 시험하는 경로가 사라진다.
            authorizeGuestPost(session, boardTitle, postNum);
        } else {
            response.put("message", "비밀번호가 일치하지 않습니다.");
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping(value = "/{boardTitle}/modifyPost")
    public String modifyPost(@PathVariable String boardTitle, Model model, int postNum,
            HttpSession session) throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        BoardDTO post = boardService.readPost(boardTitle, postNum);
        if (post == null) {
            model.addAttribute("msg", "존재하지 않는 게시글입니다.");
            model.addAttribute("url", "/boards/" + boardTitle);
            return "alert";
        }

        MemberDTO member = getMember(session);
        if (hasManagePermission(boardTitle, post, member, session)) {
            String koreanTitle = boardService.getKoreanTitle(boardTitle);
            model.addAttribute("koreanTitle", koreanTitle);
            model.addAttribute("boardTitle", boardTitle);
            model.addAttribute("post", post);
            return "board/modifyPost";
        }

        model.addAttribute("msg", buildManageDeniedMessage(post));
        model.addAttribute("url", "/boards/" + boardTitle + "/readPost?postNum=" + postNum);
        return "alert";
    }

    @PostMapping("/{boardTitle}/submitModifyPost")
    public String submitModifyPost(@PathVariable String boardTitle, BoardDTO post, HttpServletRequest request,
            Model model) throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        HttpSession session = request.getSession();
        MemberDTO member = getMember(session);
        BoardDTO existingPost = boardService.readPost(boardTitle, post.getPostNum());
        if (existingPost == null) {
            model.addAttribute("msg", "존재하지 않는 게시글입니다.");
            model.addAttribute("url", "/boards/" + boardTitle);
            return "alert";
        }

        if (!hasManagePermission(boardTitle, existingPost, member, session)) {
            log.debug("게시글 수정 권한 확인 실패 - 게시글:{} / 회원:{}", post.getPostNum(), member == null ? null : member.getNickName());
            model.addAttribute("msg", buildManageDeniedMessage(existingPost));
            model.addAttribute("url", "/boards/" + boardTitle + "/readPost?postNum=" + post.getPostNum());
            return "alert";
        }

        if (!isExemptFromContentSanctions(member) && rejectsAttack(request, member,
                attackContentDetector.inspect(POST_MAX_LINKS, post.getTitle(), post.getContent()))) {
            model.addAttribute("msg", ATTACK_CONTENT_MESSAGE);
            model.addAttribute("url", "/boards/" + boardTitle + "/readPost?postNum=" + post.getPostNum());
            return "alert";
        }

        post.setWriter(existingPost.getWriter());
        post.setGuestPassword(existingPost.getGuestPassword());
        if (!isAdmin(member)) {
            post.setNotice(existingPost.getNotice());
        }

        boardService.submitModifyPost(boardTitle, post);
        clearGuestPostAuthorization(session, boardTitle, post.getPostNum());
        return "redirect:/boards/" + boardTitle + "/readPost?postNum=" + post.getPostNum();
    }

    @PostMapping("/{boardTitle}/addComment")
    @ResponseBody
    public ResponseEntity<Map<String, String>> addComment(@PathVariable String boardTitle,
            @RequestBody CommentDTO comment,
            @SessionAttribute(name = "member", required = false) MemberDTO member,
            HttpServletRequest request) throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        if (comment == null || comment.getPostNum() <= 0 || trimToNull(comment.getContent()) == null
                || exceedsCodePointLimit(comment.getContent(), COMMENT_CONTENT_MAX_LENGTH)) {
            return commentResponse(HttpStatus.BAD_REQUEST, INVALID_COMMENT_MESSAGE);
        }

        if (member != null) {
            comment.setId(member.getId());
            comment.setNickname(null);
            comment.setPassword(null);
        } else {
            comment.setId(null);
            comment.setNickname(trimToNull(comment.getNickname()));
            comment.setPassword(trimToNull(comment.getPassword()));
            if (comment.getNickname() == null || comment.getPassword() == null) {
                return commentResponse(HttpStatus.BAD_REQUEST, GUEST_COMMENT_CREDENTIALS_MESSAGE);
            }
            if (exceedsCodePointLimit(comment.getNickname(), COMMENT_NICKNAME_MAX_LENGTH)
                    || exceedsCodePointLimit(comment.getPassword(), COMMENT_PASSWORD_MAX_LENGTH)) {
                return commentResponse(HttpStatus.BAD_REQUEST, GUEST_COMMENT_CREDENTIALS_MESSAGE);
            }
            if (isRegisteredMemberNickname(comment.getNickname())) {
                return commentResponse(HttpStatus.BAD_REQUEST, GUEST_NICKNAME_CONFLICT_MESSAGE);
            }
        }
        if (!isExemptFromContentSanctions(member) && rejectsAttack(request, member,
                attackContentDetector.inspect(COMMENT_MAX_LINKS, comment.getContent(), comment.getNickname()))) {
            return commentResponse(HttpStatus.BAD_REQUEST, ATTACK_CONTENT_MESSAGE);
        }
        if (!isCommentAdmin(member) && duplicateContentGuard.isDuplicate("comment", null, comment.getContent(),
                DUPLICATE_COMMENT_MIN_LENGTH, DUPLICATE_COMMENT_WINDOW_MILLIS)) {
            return commentResponse(HttpStatus.TOO_MANY_REQUESTS, DUPLICATE_COMMENT_MESSAGE);
        }
        boardService.addComment(boardTitle, comment);
        return commentResponse(HttpStatus.OK, "댓글이 성공적으로 추가되었습니다.");
    }

    @PostMapping(value = "/{boardTitle}/commentPageSetting")
    @ResponseBody
    public PageDTO commentPageSetting(@PathVariable String boardTitle, @RequestBody PageDTO page) throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        return boardService.commentPageSetting(boardTitle, page);
    }

    @PostMapping("/{boardTitle}/showCommentList")
    @ResponseBody
    public List<CommentDTO> showCommentList(@PathVariable String boardTitle, @RequestBody PageDTO page,
            @SessionAttribute(name = "member", required = false) MemberDTO member)
            throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        List<CommentDTO> comments = boardService.showCommentList(boardTitle, page);
        applyCommentPermissions(comments, member);
        return comments;
    }

    @PostMapping("/{boardTitle}/deleteComment")
    @ResponseBody
    public ResponseEntity<Map<String, String>> deleteComment(@PathVariable String boardTitle,
            @RequestParam int commentNum,
            @RequestParam(required = false) String password,
            @SessionAttribute(name = "member", required = false) MemberDTO member) throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        try {
            boardService.deleteComment(boardTitle, commentNum, member, password);
            return commentResponse(HttpStatus.OK, "댓글이 삭제되었습니다.");
        } catch (AccessDeniedException e) {
            return commentResponse(HttpStatus.FORBIDDEN, "댓글 삭제 권한이 없습니다.");
        } catch (IllegalArgumentException e) {
            return commentResponse(HttpStatus.NOT_FOUND, "존재하지 않는 댓글입니다.");
        }
    }

    @PutMapping(value = "/{boardTitle}/updateCommentCount")
    @ResponseBody
    public void updateCommentCount(@PathVariable String boardTitle, int postNum) throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        boardService.updateCommentCount(boardTitle, postNum);
    }

    @PostMapping("/{boardTitle}/addRecommendation")
    @ResponseBody
    public ResponseEntity<RecommendDTO> addRecommendation(@PathVariable String boardTitle, HttpSession session,
            @RequestBody RecommendDTO recommendDTO) {
        try {
            boardTitle = normalizeBoardTitle(boardTitle);
            MemberDTO member = getMember(session);
            if (member == null || recommendDTO.getPostNum() == 0) {
                return new ResponseEntity<>(recommendDTO, HttpStatus.BAD_REQUEST);
            }
            log.debug("추천 시 데이터 확인 - 회원: {}, 게시글 번호: {}", member, recommendDTO.getPostNum());
            recommendDTO.setUserId(member.getId());
            boardService.insertRecommendation(boardTitle, recommendDTO);
            return new ResponseEntity<>(recommendDTO, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            log.error("추천 중 잘못된 인자가 전달되었습니다.", e);
            return new ResponseEntity<>(recommendDTO, HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("추천 중 오류 발생", e);
            return new ResponseEntity<>(recommendDTO, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/{boardTitle}/cancelRecommendation")
    @ResponseBody
    public ResponseEntity<RecommendDTO> cancelRecommendation(@PathVariable String boardTitle, HttpSession session,
            @RequestBody RecommendDTO recommendDTO) {
        try {
            boardTitle = normalizeBoardTitle(boardTitle);
            MemberDTO member = getMember(session);
            if (member == null || recommendDTO.getPostNum() == 0) {
                return new ResponseEntity<>(recommendDTO, HttpStatus.BAD_REQUEST);
            }
            log.debug("추천 취소 시 데이터 확인 - 회원: {}, 게시글 번호: {}", member, recommendDTO.getPostNum());
            recommendDTO.setUserId(member.getId());
            boardService.deleteRecommendation(boardTitle, recommendDTO);
            return new ResponseEntity<>(recommendDTO, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            log.error("추천 취소 중 잘못된 인자가 전달되었습니다.", e);
            return new ResponseEntity<>(recommendDTO, HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("추천 취소 중 오류 발생", e);
            return new ResponseEntity<>(recommendDTO, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/{boardTitle}/checkRecommendation")
    @ResponseBody
    public ResponseEntity<RecommendDTO> checkRecommendation(@PathVariable String boardTitle, RecommendDTO recommendDTO,
            HttpSession session) {
        try {
            boardTitle = normalizeBoardTitle(boardTitle);
            MemberDTO member = getMember(session);
            if (member == null) {
                return new ResponseEntity<>(recommendDTO, HttpStatus.UNAUTHORIZED);
            }
            recommendDTO.setUserId(member.getId());
            int count = boardService.checkRecommendation(boardTitle, recommendDTO);
            boolean isRecommended = (count > 0);
            log.debug("추천 확인 컨트롤러 작동여부 : {}", isRecommended);
            recommendDTO.setCheckRecommend(isRecommended);
            return new ResponseEntity<>(recommendDTO, HttpStatus.OK);
        } catch (Exception e) {
            log.error("추천 확인 중 오류 발생", e);
            return new ResponseEntity<>(recommendDTO, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/{boardTitle}/getRecommendCount")
    @ResponseBody
    public ResponseEntity<Integer> getRecommendCount(@PathVariable String boardTitle,
            @RequestParam("postNum") int postNum) {
        try {
            boardTitle = normalizeBoardTitle(boardTitle);
            log.info("getRecommendCount 요청 받음. postNum: {}", postNum);
            int recommendCount = boardService.getRecommendCount(boardTitle, postNum);
            log.info("게시글 번호 {}의 추천 수: {}", postNum, recommendCount);
            return new ResponseEntity<>(recommendCount, HttpStatus.OK);
        } catch (Exception e) {
            log.error("추천 수 조회 중 오류 발생", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/{boardTitle}/movePost")
    public String movePost(@PathVariable String boardTitle, @RequestBody Map<String, Object> payload) throws Exception {
        boardTitle = normalizeBoardTitle(boardTitle);
        int postNum = (int) payload.get("postNum");
        String targetBoardTitle = normalizeBoardTitle((String) payload.get("moveToBoard"));
        log.debug("게시글 이동 기능 {} {}", postNum, targetBoardTitle);

        boardService.movePost(boardTitle, postNum, targetBoardTitle);

        return "redirect:/boards/" + boardTitle;
    }

    @GetMapping("/boardList")
    @ResponseBody
    public List<BoardListDTO> getBoardList() {
        return boardService.getBoardList();
    }

    @GetMapping("/showLatestPosts")
    @ResponseBody
    public List<LatestPostDTO> showLatestPosts() {
        try {
            List<LatestPostDTO> latestPosts = boardService.showLatestPosts();
            return latestPosts == null ? Collections.emptyList() : latestPosts;
        } catch (Exception e) {
            log.error("최신글 조회 중 오류 발생", e);
            return Collections.emptyList();
        }
    }

    private boolean canWrite(String boardTitle, HttpSession session) {
        if (isGuestWritableBoard(boardTitle)) {
            return true;
        }
        MemberDTO member = getMember(session);
        return member != null && boardService.canWrite(boardTitle, member);
    }

    private MemberDTO getMember(HttpSession session) {
        return session == null ? null : (MemberDTO) session.getAttribute("member");
    }

    private boolean preparePostForSubmission(String boardTitle, BoardDTO post, MemberDTO member) {
        if (post == null) {
            return false;
        }
        if (member != null) {
            if (!boardService.canWrite(boardTitle, member)) {
                return false;
            }
            post.setWriter(member.getNickName());
            post.setGuestPassword(null);
            return true;
        }
        if (!isGuestWritableBoard(boardTitle)) {
            return false;
        }

        post.setWriter(trimToNull(post.getWriter()));
        post.setGuestPassword(trimToNull(post.getGuestPassword()));
        return post.getWriter() != null && post.getGuestPassword() != null;
    }

    private boolean hasManagePermission(String boardTitle, BoardDTO post, MemberDTO member, HttpSession session) {
        if (post == null) {
            return false;
        }
        if (isAdmin(member)) {
            return true;
        }
        if (isGuestPost(post)) {
            return isGuestWritableBoard(boardTitle) && isGuestPostAuthorized(session, boardTitle, post.getPostNum());
        }
        return isPostOwner(post, member);
    }

    private boolean canDeletePost(String boardTitle, BoardDTO post, MemberDTO member, String guestPassword) {
        if (post == null) {
            return false;
        }
        if (isAdmin(member)) {
            return true;
        }
        if (isGuestPost(post)) {
            return isGuestWritableBoard(boardTitle) && guestPasswordMatches(post, guestPassword);
        }
        return isPostOwner(post, member);
    }

    /**
     * 작성자 별명이 같아도 글보다 늦게 가입한 계정은 작성자로 보지 않는다. 변경·탈퇴로 해제된 별명을
     * 새 계정이 취득해 과거 글의 관리 권한을 얻는 경로를 막는다(가입일이 없는 옛 계정은 기존대로 허용).
     */
    private boolean isPostOwner(BoardDTO post, MemberDTO member) {
        if (member == null || !Objects.equals(post.getWriter(), member.getNickName())) {
            return false;
        }
        Date memberSince = member.getRegDate();
        Date postDate = post.getRegDate();
        return memberSince == null || postDate == null || !memberSince.after(postDate);
    }

    private boolean guestPasswordMatches(BoardDTO post, String guestPassword) {
        return Objects.equals(post.getGuestPassword(), trimToNull(guestPassword));
    }

    private boolean isGuestPost(BoardDTO post) {
        return post != null && trimToNull(post.getGuestPassword()) != null;
    }

    private String buildSubmitDeniedMessage(String boardTitle) {
        if (isGuestWritableBoard(boardTitle)) {
            return "이름과 비밀번호를 확인해주세요";
        }
        return "로그인 상태를 확인해주세요";
    }

    private String buildSubmitDeniedUrl(String boardTitle) {
        if (isGuestWritableBoard(boardTitle)) {
            return "/boards/" + boardTitle + "/writePost";
        }
        return "/";
    }

    private String buildManageDeniedMessage(BoardDTO post) {
        if (isGuestPost(post)) {
            return "비밀번호를 확인해주세요";
        }
        return "로그인 정보를 확인해주세요";
    }

    private String buildDeleteDeniedMessage(BoardDTO post) {
        if (isGuestPost(post)) {
            return "비밀번호가 일치하지 않습니다.";
        }
        return "삭제 권한이 없습니다.";
    }

    @SuppressWarnings("unchecked")
    private Set<String> getAuthorizedGuestPostKeys(HttpSession session) {
        Object value = session.getAttribute(GUEST_POST_AUTH_SESSION_KEY);
        if (value instanceof Set) {
            return (Set<String>) value;
        }
        Set<String> keys = new HashSet<>();
        session.setAttribute(GUEST_POST_AUTH_SESSION_KEY, keys);
        return keys;
    }

    private void authorizeGuestPost(HttpSession session, String boardTitle, int postNum) {
        if (session == null) {
            return;
        }
        getAuthorizedGuestPostKeys(session).add(buildGuestPostAuthKey(boardTitle, postNum));
    }

    private boolean isGuestPostAuthorized(HttpSession session, String boardTitle, int postNum) {
        if (session == null) {
            return false;
        }
        Object value = session.getAttribute(GUEST_POST_AUTH_SESSION_KEY);
        if (!(value instanceof Set)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Set<String> keys = (Set<String>) value;
        return keys.contains(buildGuestPostAuthKey(boardTitle, postNum));
    }

    private void clearGuestPostAuthorization(HttpSession session, String boardTitle, int postNum) {
        if (session == null) {
            return;
        }
        Object value = session.getAttribute(GUEST_POST_AUTH_SESSION_KEY);
        if (!(value instanceof Set)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Set<String> keys = (Set<String>) value;
        keys.remove(buildGuestPostAuthKey(boardTitle, postNum));
    }

    private String buildGuestPostAuthKey(String boardTitle, int postNum) {
        return normalizeBoardTitle(boardTitle) + ":" + postNum;
    }

    private boolean isGuestWritableBoard(String boardTitle) {
        return GUEST_WRITABLE_BOARD.equals(normalizeBoardTitle(boardTitle));
    }

    private boolean isAdmin(MemberDTO member) {
        return member != null && ADMIN_ID.equals(member.getId());
    }

    private boolean isCommentAdmin(MemberDTO member) {
        return member != null && (member.getGrade() == 3 || ADMIN_ID.equals(member.getId()));
    }

    /** 등급 3 관리자·운영 계정은 내용 기반 자동 제재 대상에서 제외한다(정화기는 그대로 적용된다). */
    private boolean isExemptFromContentSanctions(MemberDTO member) {
        return isCommentAdmin(member);
    }

    /**
     * 공격 패턴이 확인되면 {@link OffenderTracker}에 넘겨 즉시 제재(HIGH) 또는 가중 스트라이크(MEDIUM)를 걸고
     * true 를 돌려준다. 호출자가 거부 응답을 만든다.
     */
    private boolean rejectsAttack(HttpServletRequest request, MemberDTO member, AttackContentDetector.Verdict verdict) {
        if (verdict == null || !verdict.isAttack()) {
            return false;
        }
        String ip = IpService.getRemoteIP(request);
        offenderTracker.attackDetected(ip, IpService.hasForwardedClient(request),
                member == null ? null : member.getId(), member == null ? null : member.getNickName(), verdict);
        log.warn("공격 패턴 게시 시도 거부 - rule={}, severity={}, member={}, ip={}", verdict.rule(), verdict.severity(),
                member == null ? null : member.getId(), ip);
        return true;
    }

    private void applyCommentPermissions(List<CommentDTO> comments, MemberDTO member) {
        if (comments == null) {
            return;
        }
        for (CommentDTO comment : comments) {
            if (comment == null) {
                continue;
            }
            boolean guestComment = trimToNull(comment.getId()) == null;
            boolean guestHasPassword = guestComment && trimToNull(comment.getPassword()) != null;
            boolean memberOwnsComment = member != null && Objects.equals(comment.getId(), member.getId());
            boolean commentAdmin = isCommentAdmin(member);
            comment.setGuestComment(guestComment);
            comment.setDeletable(commentAdmin || memberOwnsComment || guestHasPassword);
            comment.setPasswordRequired(guestHasPassword && !commentAdmin);
        }
    }

    private ResponseEntity<Map<String, String>> commentResponse(HttpStatus status, String message) {
        Map<String, String> response = new HashMap<>();
        response.put("message", message);
        return new ResponseEntity<>(response, status);
    }

    private boolean exceedsCodePointLimit(String value, int maxLength) {
        return value != null && value.codePointCount(0, value.length()) > maxLength;
    }

    private String normalizeBoardTitle(String boardTitle) {
        return BoardTitleNormalizer.normalizeNullable(boardTitle);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isRegisteredMemberNickname(String nickname) {
        String trimmedNickname = trimToNull(nickname);
        if (trimmedNickname == null) {
            return false;
        }
        String duplicateCount = trimToNull(memberService.isUniqueNickName(trimmedNickname));
        return duplicateCount != null && !"0".equals(duplicateCount);
    }

    private void requireBoard(String koreanTitle) {
        if (koreanTitle == null || koreanTitle.trim().isEmpty()) {
            throw new ResourceNotFoundException("존재하지 않는 게시판입니다.");
        }
    }
}
