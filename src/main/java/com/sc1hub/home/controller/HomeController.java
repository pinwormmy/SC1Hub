package com.sc1hub.home.controller;

import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.service.BoardService;
import com.sc1hub.visitor.service.VisitorCountService;
import com.sc1hub.home.dto.HomePopularBoardDTO;
import com.sc1hub.home.dto.HomePopularSectionDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Controller
@Slf4j
public class HomeController {

    private static final int POPULAR_POST_LIMIT = 5;
    private static final String POPULAR_SECTION_LEGEND_COLOR = "#75f94c";

    private final VisitorCountService visitorCountService;
    private final BoardService boardService;

    public HomeController(VisitorCountService visitorCountService, BoardService boardService) {
        this.visitorCountService = visitorCountService;
        this.boardService = boardService;
    }

    @GetMapping("/")
    public String home(Model model, HttpServletRequest request, HttpServletResponse response) {
        visitorCountService.processVisitor(request, response);

        model.addAttribute("todayCount", visitorCountService.getTodayCount());
        model.addAttribute("totalCount", visitorCountService.getTotalCount());
        model.addAttribute("popularSections", buildPopularSections());

        return "index";
    }

    @GetMapping("/guidelines")
    public String showGuidelines() {
        return "guidelines";
    }

    private List<HomePopularSectionDTO> buildPopularSections() {
        List<HomePopularSectionDTO> sections = new ArrayList<>();
        sections.add(buildSection(
                "테란 네트워크",
                "terran-field",
                Arrays.asList("tVsZBoard", "tVsPBoard", "tVsTBoard")
        ));
        sections.add(buildSection(
                "저그 네트워크",
                "zerg-field",
                Arrays.asList("zVsTBoard", "zVsPBoard", "zVsZBoard")
        ));
        sections.add(buildSection(
                "프로토스 네트워크",
                "protoss-field",
                Arrays.asList("pVsTBoard", "pVsZBoard", "pVsPBoard")
        ));
        return sections;
    }

    private HomePopularSectionDTO buildSection(String title, String cssClass,
                                               List<String> boardTitles) {
        HomePopularSectionDTO section = new HomePopularSectionDTO();
        section.setTitle(title);
        section.setCssClass(cssClass);
        section.setLegendColor(POPULAR_SECTION_LEGEND_COLOR);
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
            board.setPosts(fetchPopularPosts(boardTitle));
            boards.add(board);
        }
        return boards;
    }

    private List<BoardDTO> fetchPopularPosts(String boardTitle) {
        try {
            List<BoardDTO> posts = boardService.getPopularPosts(boardTitle, POPULAR_POST_LIMIT);
            return posts == null ? Collections.emptyList() : posts;
        } catch (Exception e) {
            log.error("메인 인기글 조회 중 오류 발생. boardTitle={}", boardTitle, e);
            return Collections.emptyList();
        }
    }
}
