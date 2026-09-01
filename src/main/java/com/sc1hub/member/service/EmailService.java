package com.sc1hub.member.service;

public interface EmailService {
    void sendNewPasswordMessage(String email, String tempPassword) throws Exception;
}
