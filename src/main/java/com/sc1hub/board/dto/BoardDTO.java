package com.sc1hub.board.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class BoardDTO {
    private int postNum;
    private String title;
    private String content;
    private String writer;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date regDate;
    private int views;
    private int commentCount;
    private int notice;
    private int recommendCount;
}
