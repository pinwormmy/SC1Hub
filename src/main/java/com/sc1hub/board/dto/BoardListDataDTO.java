package com.sc1hub.board.dto;

import com.sc1hub.common.dto.PageDTO;
import lombok.Data;

import java.util.List;

@Data
public class BoardListDataDTO {
    private String boardTitle;
    private String koreanTitle;
    private PageDTO page;
    private List<BoardDTO> selfNoticeList;
    private List<BoardDTO> postList;
    private boolean canWrite;
}
