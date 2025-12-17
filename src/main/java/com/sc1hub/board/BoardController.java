package com.sc1hub.board;

import com.sc1hub.member.MemberDTO;
import com.sc1hub.common.util.IpService;
import com.sc1hub.common.dto.PageDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.nio.file.AccessDeniedException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@Slf4j
@RequestMapping("/boards")
public class BoardController {

    @Autowired
    BoardService boardService;

    @GetMapping(value = "/{boardTitle}")
    public String list(@PathVariable String boardTitle, PageDTO page, Model model, HttpSession session)
            throws Exception {
        String koreanTitle = boardService.getKoreanTitle(boardTitle);
        model.addAttribute("koreanTitle", koreanTitle);
        model.addAttribute("boardTitle", boardTitle);
        model.addAttribute("page", boardService.pageSetting(boardTitle, page));
        model.addAttribute("selfNoticeList", boardService.showSelfNoticeList(boardTitle));
        model.addAttribute("postList", boardService.showPostList(boardTitle, page));

        // 글쓰기 권한 설정
        boolean canWrite = false;
        MemberDTO member = (MemberDTO) session.getAttribute("member");
        if (member != null) {
            canWrite = true;
            if (!boardService.canWrite(boardTitle, member)) {
                canWrite = false;
            }
        }
        model.addAttribute("canWrite", canWrite);

        return "board/postList";
    }

    @GetMapping("/{boardTitle}/readPost")
    public String readPost(@PathVariable String boardTitle, Model model, HttpServletRequest request) throws Exception {
        int postNum = Integer.parseInt(request.getParameter("postNum"));
        String ip = IpService.getRemoteIP(request);
        boardService.increaseViewCount(boardTitle, postNum, ip);
        String koreanTitle = boardService.getKoreanTitle(boardTitle);
        model.addAttribute("koreanTitle", koreanTitle);
        model.addAttribute("boardTitle", boardTitle);
        model.addAttribute("post", boardService.readPost(boardTitle, postNum));
        return "board/readPost";
    }

    @RequestMapping("/{boardTitle}/writePost")
    public String writePost(@PathVariable String boardTitle, Model model, HttpServletRequest request) {
        log.debug("세션 만료 시간 : {}", request.getSession().getMaxInactiveInterval());
        String koreanTitle = boardService.getKoreanTitle(boardTitle);
        model.addAttribute("koreanTitle", koreanTitle);
        model.addAttribute("boardTitle", boardTitle);
        return "board/writePost";
    }

    @RequestMapping(value = "/{boardTitle}/submitPost", method = RequestMethod.POST)
    public String submitPost(@PathVariable String boardTitle, BoardDTO post, HttpServletRequest request, Model model)
            throws Exception {
        MemberDTO member = (MemberDTO) request.getSession().getAttribute("member");
        if (!post.getWriter().equals(member.getNickName()) && !member.getId().equals("admin")) {
            log.debug("글작성자와 로그인정보 확인 - 작성:{} / 회원:{}", post.getWriter(), member.getNickName());
            model.addAttribute("msg", "로그인 상태를 확인해주세요");
            model.addAttribute("url", "/");
            return "alert";
        }

        boardService.submitPost(boardTitle, post);
        return "redirect:/boards/" + boardTitle;
    }

    @RequestMapping(value = "/{boardTitle}/deletePost", method = RequestMethod.POST)
    public String deletePost(@PathVariable String boardTitle, BoardDTO post, HttpServletRequest request,
            RedirectAttributes redirectAttributes) throws Exception {
        MemberDTO member = (MemberDTO) request.getSession().getAttribute("member");
        if (member == null) {
            redirectAttributes.addFlashAttribute("msg", "로그인 정보가 없습니다.");
            return "redirect:/";
        }
        try {
            boardService.deletePost(boardTitle, post.getPostNum(), member);
            return "redirect:/boards/" + boardTitle;
        } catch (AccessDeniedException e) {
            log.warn("삭제 권한 오류 발생 - 유저 ID: {}", member.getId());
            redirectAttributes.addFlashAttribute("msg", "삭제 권한이 없습니다.");
            return "redirect:/";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("msg", e.getMessage());
            return "redirect:/";
        }
    }

    @RequestMapping(value = "/{boardTitle}/modifyPost")
    public String modifyPost(@PathVariable String boardTitle, Model model, int postNum) throws Exception {
        String koreanTitle = boardService.getKoreanTitle(boardTitle);
        model.addAttribute("koreanTitle", koreanTitle);
        model.addAttribute("boardTitle", boardTitle);
        model.addAttribute("post", boardService.readPost(boardTitle, postNum));
        return "board/modifyPost";
    }

    @RequestMapping(value = "/{boardTitle}/submitModifyPost", method = RequestMethod.POST)
    public String submitModifyPost(@PathVariable String boardTitle, BoardDTO post, HttpServletRequest request,
            Model model) throws Exception {
        MemberDTO member = (MemberDTO) request.getSession().getAttribute("member");
        if (!post.getWriter().equals(member.getNickName()) && !member.getId().equals("admin")) {
            log.debug("글작성자와 로그인정보 확인 - 작성:{} / 회원:{}", post.getWriter(), member.getNickName());
            model.addAttribute("msg", "로그인 정보를 확인해주세요");
            model.addAttribute("url", "/");
            return "alert";
        }
        boardService.submitModifyPost(boardTitle, post);
        return "redirect:/boards/" + boardTitle + "/readPost?postNum=" + post.getPostNum();
    }

    @RequestMapping(value = "/{boardTitle}/addComment", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<Map<String, String>> addComment(@PathVariable String boardTitle,
            @RequestBody CommentDTO comment) throws Exception {
        log.info("댓글 인수 확인(댓글내용) : {}", comment.getContent());
        boardService.addComment(boardTitle, comment);
        Map<String, String> response = new HashMap<>();
        response.put("message", "댓글이 성공적으로 추가되었습니다.");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @RequestMapping(value = "/{boardTitle}/commentPageSetting")
    @ResponseBody
    public PageDTO commentPageSetting(@PathVariable String boardTitle, @RequestBody PageDTO page) throws Exception {
        return boardService.commentPageSetting(boardTitle, page);
    }

    @RequestMapping("/{boardTitle}/showCommentList")
    @ResponseBody
    public List<CommentDTO> showCommentList(@PathVariable String boardTitle, @RequestBody PageDTO page)
            throws Exception {
        return boardService.showCommentList(boardTitle, page);
    }

    @RequestMapping(value = "/{boardTitle}/deleteComment", method = RequestMethod.POST)
    @ResponseBody
    public void deleteComment(@PathVariable String boardTitle, int commentNum) throws Exception {
        boardService.deleteComment(boardTitle, commentNum);
    }

    @RequestMapping(value = "/{boardTitle}/updateCommentCount")
    @ResponseBody
    public void updateCommentCount(@PathVariable String boardTitle, int postNum) throws Exception {
        boardService.updateCommentCount(boardTitle, postNum);
    }

    @RequestMapping(value = "/{boardTitle}/addRecommendation", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<RecommendDTO> addRecommendation(@PathVariable String boardTitle, HttpSession session,
            @RequestBody RecommendDTO recommendDTO) {
        try {
            MemberDTO member = (MemberDTO) session.getAttribute("member");
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

    @RequestMapping(value = "/{boardTitle}/cancelRecommendation", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<RecommendDTO> cancelRecommendation(@PathVariable String boardTitle, HttpSession session,
            @RequestBody RecommendDTO recommendDTO) {
        try {
            MemberDTO member = (MemberDTO) session.getAttribute("member");
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

    @RequestMapping(value = "/{boardTitle}/checkRecommendation", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<RecommendDTO> checkRecommendation(@PathVariable String boardTitle, RecommendDTO recommendDTO,
            HttpSession session) {
        try {
            MemberDTO member = (MemberDTO) session.getAttribute("member");
            if (member == null) {
                return new ResponseEntity<>(recommendDTO, HttpStatus.UNAUTHORIZED);
            }
            recommendDTO.setUserId(member.getId());
            int count = boardService.checkRecommendation(boardTitle, recommendDTO);
            boolean isRecommended = (count > 0);
            log.debug("추천 확인 컨트롤러 작동여부 : " + isRecommended);
            recommendDTO.setCheckRecommend(isRecommended);
            return new ResponseEntity<>(recommendDTO, HttpStatus.OK);
        } catch (Exception e) {
            log.error("추천 확인 중 오류 발생", e);
            return new ResponseEntity<>(recommendDTO, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @RequestMapping(value = "/{boardTitle}/getRecommendCount", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<Integer> getRecommendCount(@PathVariable String boardTitle,
            @RequestParam("postNum") int postNum) {
        try {
            log.info("getRecommendCount 요청 받음. postNum: " + postNum);
            int recommendCount = boardService.getRecommendCount(boardTitle, postNum);
            log.info("게시글 번호 " + postNum + "의 추천 수: " + recommendCount);
            return new ResponseEntity<>(recommendCount, HttpStatus.OK);
        } catch (Exception e) {
            log.error("추천 수 조회 중 오류 발생", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @RequestMapping("/{boardTitle}/movePost")
    public String movePost(@PathVariable String boardTitle, @RequestBody Map<String, Object> payload) throws Exception {
        int postNum = (int) payload.get("postNum");
        String targetBoardTitle = (String) payload.get("moveToBoard");
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
        return boardService.showLatestPosts();
    }
}
