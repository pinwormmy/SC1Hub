package com.sc1hub.member.controller;

import com.sc1hub.common.dto.PageDTO;
import com.sc1hub.member.dto.EmailDTO;
import com.sc1hub.member.dto.MemberDTO;
import com.sc1hub.member.dto.VerificationResponseDTO;
import com.sc1hub.member.service.EmailService;
import com.sc1hub.member.service.MemberService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@Controller
@Slf4j
public class MemberController {
    @Autowired
    MemberService memberService;
    @Autowired
    EmailService emailService;

    @RequestMapping("/login")
    public String login(HttpServletRequest request) {
        // 로그인 이전 페이지 정보 세션에 저장
        request.getSession().setAttribute("pageBeforeLogin", request.getHeader("Referer"));
        return "login";
    }

    @RequestMapping("/signUp")
    public String signUp() {
        return "signUp";
    }

    @RequestMapping("/signAgreement")
    public String signAgreement() {
        return "signAgreement";
    }

    @RequestMapping("/isUniqueId")
    @ResponseBody
    public String isUniqueId(String id) throws Exception {
        log.debug("(중복확인용)ID 입력 확인: {}", id);
        return memberService.isUniqueId(id);
    }

    @RequestMapping(value = "/submitSignUp", method = RequestMethod.POST)
    public String submitSignUp(MemberDTO memberDTO, HttpSession httpSession) throws Exception {
        memberService.submitSignUp(memberDTO);
        httpSession.setAttribute("member", memberService.checkLoginData(memberDTO)); // 로그인도 해줌
        log.debug("회원가입 확인: {}", memberDTO);
        return "redirect:/";
    }

    @RequestMapping(value = "/submitLogin", method = RequestMethod.POST)
    public String submitLogin(HttpSession session, MemberDTO memberDTO, Model model) throws Exception {
        MemberDTO loginData = memberService.checkLoginData(memberDTO);
        if (loginData == null) {
            model.addAttribute("message", "아이디 또는 비밀번호가 일치하지 않습니다.");
            return "login";
        }
        session.setAttribute("member", loginData);
        log.debug("로그인 확인: {}", memberDTO);
        return "redirect:" + session.getAttribute("pageBeforeLogin");
    }

    @RequestMapping(value = "/logout")
    public String logout(HttpSession httpSession) {
        httpSession.invalidate();
        return "redirect:/";
    }

    @RequestMapping(value = "/myPage")
    public String myPage() {
        return "myPage";
    }

    @RequestMapping(value = "/modifyMyInfo")
    public String modifyMyInfo() {
        return "modifyMyInfo";
    }

    @RequestMapping(value = "/submitModifyMyInfo", method = RequestMethod.POST)
    public String submitModifyMyInfo(MemberDTO member, HttpSession session) throws Exception {
        memberService.submitModifyMyInfo(member);
        session.setAttribute("member", memberService.checkLoginData(member)); // 재로그인해서 회원정보갱신
        return "myPage";
    }

    @RequestMapping(value = "/modifyMember")
    public String modifyMember() {
        return "modifyMember";
    }

    @PostMapping("/sendVerificationMail")
    @ResponseBody
    public ResponseEntity<VerificationResponseDTO> sendVerificationMail(@RequestBody EmailDTO emailDTO) {
        try {
            String email = emailDTO.getEmail();
            String verificationNumber = emailService.sendSimpleMessage(email);

            VerificationResponseDTO response = new VerificationResponseDTO(true, verificationNumber);

            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            log.error("인증 메일 발송 중 오류가 발생했습니다: ", e);
            VerificationResponseDTO response = new VerificationResponseDTO(false, null);
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/checkUniqueId")
    @ResponseBody
    public String checkUniqueId(String id) throws Exception {
        log.info("아이디 중복 확인 컨트롤러 작동");
        return memberService.isUniqueId(id);
    }

    @GetMapping("/checkUniqueEmail")
    @ResponseBody
    public String checkUniqueEmail(String email) {
        log.info("이멜 중복 확인 컨트롤러 작동");
        return memberService.isUniqueEmail(email);
    }

    @GetMapping("/checkUniqueNickName")
    @ResponseBody
    public String checkUniqueNickName(String nickName) {
        log.info("별명 중복 확인 컨트롤러 작동");
        return memberService.isUniqueNickName(nickName);
    }

    @GetMapping(value = "/adminPage")
    public String adminPage(Model model, PageDTO page) throws Exception {
        log.info("관리자 모드");
        page = memberService.pageSetting(page);
        model.addAttribute("pageInfo", page);
        model.addAttribute("memberList", memberService.getMemberList(page));
        model.addAttribute("recentVisitors", memberService.getRecentVisitors());
        return "adminPage";
    }

    @RequestMapping(value = "/modifyMemberByAdmin")
    public String modifyMemberByAdmin(Model model, String id) {
        log.info("관리자의 회원수정 페이지");
        model.addAttribute("member", memberService.getMemberInfo(id));
        return "modifyMemberByAdmin";
    }

    @RequestMapping(value = "/submitModifyMemberByAdmin", method = RequestMethod.POST)
    public String submitModifyMemberByAdmin(MemberDTO memberDTO) {
        log.info("관리자의 회원수정 제출");
        memberService.submitModifyMemberByAdmin(memberDTO);
        return "redirect:/adminPage";
    }

    @GetMapping(value = "/findId")
    public String findId() {
        log.info("아이디 찾기 페이지");
        return "findId";
    }

    @PostMapping("/findId")
    public String findIdByNameAndEmail(String userName, String email, Model model) {
        String userId = memberService.findIdByNameAndEmail(userName, email);
        if (userId != null && !userId.isEmpty()) {
            model.addAttribute("message", "당신의 아이디는 " + userId + "입니다.");
        } else {
            model.addAttribute("message", "입력하신 이름과 이메일로 등록된 아이디를 찾을 수 없습니다.");
        }
        return "findId";
    }

    @GetMapping(value = "/findPassword")
    public String findPassword() {
        log.info("패스워드 찾기 페이지");
        return "findPassword";
    }

    @PostMapping("/findPassword")
    public String findPassword(String userId, String email, Model model) {
        try {
            String result = memberService.findPassword(userId, email);

            if (result.equals("success")) {
                model.addAttribute("message", "임시 패스워드가 이메일로 전송되었습니다.");
            } else {
                model.addAttribute("message", result);
            }
        } catch (Exception e) {
            log.error("비밀번호 찾기 중 에러 발생", e);
            model.addAttribute("message", "비밀번호 찾기 중 문제가 발생했습니다. 다시 시도해 주세요.");
        }
        return "findPassword";
    }

    @PostMapping("/deleteMember")
    @ResponseBody
    public ResponseEntity<Map<String, Boolean>> deleteMember(@RequestParam("id") String id) {
        Map<String, Boolean> response = new HashMap<>();
        try {
            memberService.deleteMember(id);
            response.put("success", true);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (DataAccessException dae) {
            // DataAccessException 처리
            log.error("회원 삭제 중 DataAccessException 발생, 회원 ID: {}", id, dae);
            response.put("success", false);
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            // 기타 예외 처리
            log.error("회원 삭제 중 예외 발생, 회원 ID: {}", id, e);
            response.put("success", false);
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/deleteMyAccount")
    @ResponseBody
    public String deleteMyAccount(HttpServletRequest request) {
        log.info("계정 탈퇴 후 로그아웃 처리하기....");
        String userId = null; // userId 변수를 try 블록 바깥에 선언합니다.
        try {
            MemberDTO member = (MemberDTO) request.getSession().getAttribute("member");
            userId = member.getId(); // userId 변수를 초기화합니다.

            memberService.deleteMember(userId);

            request.getSession().invalidate();
            return "{\"success\": true}";
        } catch (DataAccessException dae) {
            // 데이터베이스 관련 예외 처리
            log.error("회원 삭제 중 DataAccessException 발생, 회원 ID: {}", userId, dae);
            return "{\"success\": false, \"message\": \"데이터베이스 오류가 발생했습니다.\"}";
        } catch (Exception e) {
            // 기타 예외 처리
            log.error("회원 삭제 중 예외 발생, 회원 ID: {}", userId, e);
            return "{\"success\": false, \"message\": \"알 수 없는 오류가 발생했습니다.\"}";
        }
    }

    @PutMapping("/extendLogin")
    @ResponseBody
    public void extendLogin(HttpServletRequest request) {
        log.info("로그인 시간을 연장합니다.");
        request.getSession().setMaxInactiveInterval(1800);
    }

}
