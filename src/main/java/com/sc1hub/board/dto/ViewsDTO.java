package com.sc1hub.board.dto;

import lombok.Data;

import java.util.Date;

@Data
public class ViewsDTO {
    private int postNum;
    private String ip;
    private Date regDate;
}
