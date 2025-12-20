package com.sc1hub.board.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class LatestPostDTO {
    int postNum;
    String title;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date regDate;
    String boardTitle;
    String koreaBoardTitle;
}
