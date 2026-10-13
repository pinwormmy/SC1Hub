package com.sc1hub.board.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.Date;

@Data
public class BoardDTO {
    private int postNum;
    private String title;
    private String content;
    private String writer;
    @JsonIgnore
    private String guestPassword;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date regDate;
    // 마지막 수정 시각. 수정한 적 없는 글은 null.
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date modDate;
    private int views;
    private int commentCount;
    private int notice;
    private int recommendCount;
    private String searchTerms;
}