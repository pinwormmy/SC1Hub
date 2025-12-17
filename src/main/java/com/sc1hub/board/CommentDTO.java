package com.sc1hub.board;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sc1hub.member.MemberDTO;
import lombok.Data;

import java.util.Date;

@Data
public class CommentDTO {
    private int postNum;
    private int commentNum;
    private String id;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+9")
    private Date regDate;
    private String content;
    private String nickname; // 비로그인 작성자 닉네임
    private String password; // 비로그인 작성자 비밀번호

    private MemberDTO memberDTO;
}
