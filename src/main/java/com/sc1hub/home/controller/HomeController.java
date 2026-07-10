package com.sc1hub.home.controller;

import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.service.BoardService;
import com.sc1hub.home.dto.HomePopularBoardDTO;
import com.sc1hub.home.dto.HomePopularSectionDTO;
import com.sc1hub.seo.SeoMetadataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Controller
@Slf4j
public class HomeController {

    private static final int RECENT_POST_LIMIT = 3;

    private final BoardService boardService;
    private final SeoMetadataService seoMetadataService;

    public HomeController(BoardService boardService, SeoMetadataService seoMetadataService) {
        this.boardService = boardService;
        this.seoMetadataService = seoMetadataService;
    }

    @GetMapping("/")
    public String home(Model model, HttpServletRequest request) {
        model.addAttribute("popularSections", buildPopularSections());
        seoMetadataService.applyHome(model, request);

        return "index";
    }

    @GetMapping("/guidelines")
    public String showGuidelines(Model model, HttpServletRequest request) {
        seoMetadataService.applyContentPage(
                model,
                request,
                "SC1Hub 이용 안내 | 게시판 운영 원칙",
                "SC1Hub 게시판 유형, 글 작성 원칙과 서비스 이용에 필요한 안내를 확인하세요.");
        return "guidelines";
    }

    private List<HomePopularSectionDTO> buildPopularSections() {
        List<HomePopularSectionDTO> sections = new ArrayList<>();
        sections.add(buildSection(
                "테란 네트워크",
                "terran-field",
                Arrays.asList("tvszboard", "tvspboard", "tvstboard")
        ));
        sections.add(buildSection(
                "저그 네트워크",
                "zerg-field",
                Arrays.asList("zvstboard", "zvspboard", "zvszboard")
        ));
        sections.add(buildSection(
                "프로토스 네트워크",
                "protoss-field",
                Arrays.asList("pvstboard", "pvszboard", "pvspboard")
        ));
        return sections;
    }

    private HomePopularSectionDTO buildSection(String title, String cssClass,
                                               List<String> boardTitles) {
        HomePopularSectionDTO section = new HomePopularSectionDTO();
        section.setTitle(title);
        section.setCssClass(cssClass);
        section.setBoards(buildBoards(boardTitles));
        return section;
    }

    private List<HomePopularBoardDTO> buildBoards(List<String> boardTitles) {
        List<HomePopularBoardDTO> boards = new ArrayList<>();
        for (String boardTitle : boardTitles) {
            HomePopularBoardDTO board = new HomePopularBoardDTO();
            board.setBoardTitle(boardTitle);
            String koreanTitle = boardService.getKoreanTitle(boardTitle);
            board.setKoreanTitle(koreanTitle != null ? koreanTitle : boardTitle);
            board.setPosts(fetchRecentPosts(boardTitle));
            boards.add(board);
        }
        return boards;
    }

    private List<BoardDTO> fetchRecentPosts(String boardTitle) {
        try {
            List<BoardDTO> posts = boardService.getRecentPosts(boardTitle, RECENT_POST_LIMIT);
            return posts == null ? Collections.emptyList() : posts;
        } catch (Exception e) {
            log.error("메인 최신글 조회 중 오류 발생. boardTitle={}", boardTitle, e);
            return Collections.emptyList();
        }
    }
}
