package com.sc1hub.strategytip.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.Date;

@Data
public class StrategyTipDTO {
    private int tipNum;
    private String category;
    private String categoryName;
    private String content;
    private String writer;
    private String memberId;
    @JsonIgnore
    private String guestPassword;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date regDate;
    private int recommendCount;
}
