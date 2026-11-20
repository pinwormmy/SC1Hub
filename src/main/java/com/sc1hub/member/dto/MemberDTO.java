package com.sc1hub.member.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.Date;

@Data
public class MemberDTO {
    private String id;
    @JsonIgnore
    private String pw;
    private String nickName;
    private String realName;
    private String email;
    private String phone;
    private int grade;
    private Date regDate;
}
