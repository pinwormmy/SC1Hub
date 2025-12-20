package com.sc1hub.board.controller;

import com.sc1hub.board.dto.LatestPostDTO;
import com.sc1hub.board.service.BoardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api")
@Slf4j
public class LatestPostController {

    private final BoardService boardService;

    public LatestPostController(BoardService boardService) {
        this.boardService = boardService;
    }

    @GetMapping("/latest-posts")
    public List<LatestPostDTO> latestPosts() {
        try {
            List<LatestPostDTO> latestPosts = boardService.showLatestPosts();
            return latestPosts == null ? Collections.emptyList() : latestPosts;
        } catch (Exception e) {
            log.error("최신글 조회 중 오류 발생", e);
            return Collections.emptyList();
        }
    }
}
