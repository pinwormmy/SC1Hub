package com.sc1hub.board.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sc1hub.member.dto.MemberDTO;
import lombok.Data;

import java.util.Date;

@Data
public class CommentDTO {
    private int postNum;
    private int commentNum;
    @JsonIgnore
    private String id;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+9")
    private Date regDate;
    private String content;
    private String nickname;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private boolean deletable;
    private boolean guestComment;
    private boolean passwordRequired;

    @JsonIgnoreProperties({"id", "pw", "realName", "email", "phone", "grade", "regDate"})
    private MemberDTO memberDTO;
}
