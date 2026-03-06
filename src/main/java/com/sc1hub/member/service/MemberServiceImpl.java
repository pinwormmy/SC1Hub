package com.sc1hub.member.service;

import com.sc1hub.common.dto.PageDTO;
import com.sc1hub.common.util.PageUtils;
import com.sc1hub.member.dto.MemberDTO;
import com.sc1hub.member.dto.VisitorsDTO;
import com.sc1hub.member.mapper.MemberMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class MemberServiceImpl implements MemberService {

    private static final String DEFAULT_MEMBER_SEARCH_TYPE = "id";
    private static final int MEMBER_DISPLAY_POST_LIMIT = 10;
    private static final int DEFAULT_PAGESET_LIMIT = 10;
    private static final int TEMP_PASSWORD_LENGTH = 8;

    private final MemberMapper memberMapper;
    private final EmailService emailService;

    public MemberServiceImpl(MemberMapper memberMapper, EmailService emailService) {
        this.memberMapper = memberMapper;
        this.emailService = emailService;
    }

    @Override
    public String isUniqueId(String id) throws Exception {
        return memberMapper.isUniqueId(id);
    }

    @Override
    public String isUniqueNickName(String nickName) {
        return memberMapper.isUniqueNickName(nickName);
    }

    @Override
    public List<VisitorsDTO> getRecentVisitors() {
        return memberMapper.getRecentVisitors();
    }

    @Override
    public void submitSignUp(MemberDTO memberDTO) throws Exception {
        memberMapper.submitSignUp(memberDTO);
    }

    @Override
    public MemberDTO checkLoginData(MemberDTO memberDTO) throws Exception {
        return memberMapper.checkLoginData(memberDTO);
    }

    @Override
    public void submitModifyMyInfo(MemberDTO member) throws Exception {
        memberMapper.submitModifyMyInfo(member);
    }

    @Override
    public String isUniqueEmail(String email) {
        return memberMapper.isUniqueEmail(email);
    }

    @Override
    public PageDTO pageSetting(PageDTO page) {
        page = PageUtils.normalize(page, DEFAULT_MEMBER_SEARCH_TYPE);
        return PageUtils.calculate(page, getTotalMemberCount(page), MEMBER_DISPLAY_POST_LIMIT, DEFAULT_PAGESET_LIMIT);
    }

    @Override
    public MemberDTO getMemberInfo(String id) {
        return memberMapper.getMemberInfo(id);
    }

    @Override
    public List<MemberDTO> getMemberList(PageDTO page) {
        return memberMapper.getMemberList(page);
    }

    @Override
    public int getTotalMemberCount(PageDTO page) {
        return memberMapper.getTotalMemberCount(page);
    }

    @Override
    public void submitModifyMemberByAdmin(MemberDTO memberDTO) {
        memberMapper.submitModifyMemberByAdmin(memberDTO);
    }

    public boolean findCredentials(String email) {
        MemberDTO member = memberMapper.findByEmail(email);
        if (member == null) {
            return false;
        }

        // 임시 비밀번호 생성
        issueTemporaryPassword(member);

        // 이메일로 아이디 및 임시 비밀번호 보내기
        try {
            emailService.sendSimpleMessage(email);
        } catch (Exception e) {
            log.error("이메일 전송오류", e);
        }

        return true;
    }

    @Override
    public String findIdByNameAndEmail(String userName, String email) {
        try {
            return memberMapper.getIdByNameAndEmail(userName, email);
        } catch (Exception e) {
            log.error("이름과 이메일로 아이디를 찾는 중 오류가 발생했습니다.", e);
            return null;
        }
    }

    @Override
    public String findPassword(String userId, String email) {
        MemberDTO member = memberMapper.findByUserIdAndEmail(userId, email);
        if (member == null) {
            return "입력하신 ID와 이메일로 등록된 회원을 찾을 수 없습니다.";
        }

        // 임시 비밀번호 생성
        String tempPassword = issueTemporaryPassword(member);

        // 이메일로 임시 비밀번호 전송
        try {
            emailService.sendNewPasswordMessage(email, tempPassword);
            return "success";
        } catch (Exception e) {
            log.error("임시 비밀번호 이메일 전송 중 오류 발생", e);
            return "비밀번호 찾기 중 문제가 발생했습니다. 다시 시도해 주세요.";
        }
    }

    @Override
    public void deleteMember(String id) {
        memberMapper.deleteMember(id);
    }

    private String issueTemporaryPassword(MemberDTO member) {
        String tempPassword = UUID.randomUUID().toString().replace("-", "").substring(0, TEMP_PASSWORD_LENGTH);
        member.setPw(tempPassword);
        memberMapper.updatePassword(member);
        return tempPassword;
    }

}
