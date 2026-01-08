package com.sc1hub.home.dto;

import com.sc1hub.board.dto.BoardDTO;
import lombok.Data;

import java.util.List;

@Data
public class HomePopularBoardDTO {
    private String boardTitle;
    private String koreanTitle;
    private List<BoardDTO> posts;
}
