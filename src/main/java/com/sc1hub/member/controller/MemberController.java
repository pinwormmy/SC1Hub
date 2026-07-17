package com.sc1hub.member.controller;

import com.sc1hub.common.dto.PageDTO;
import com.sc1hub.member.dto.EmailDTO;
import com.sc1hub.member.dto.MemberDTO;
import com.sc1hub.member.dto.VerificationResponseDTO;
import com.sc1hub.member.service.EmailService;
import com.sc1hub.member.service.MemberService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

@Controller
@Slf4j
public class MemberController {
    private static final int LOGIN_SESSION_TIMEOUT_SECONDS = 30 * 60;

    private final MemberService memberService;
    private final EmailService emailService;

    public MemberController(MemberService memberService, EmailService emailService) {
        this.memberService = memberService;
        this.emailService = emailService;
    }

    @GetMapping("/login")
    public String login(HttpServletRequest request) {
        request.getSession().setAttribute("pageBeforeLogin", resolveSafeReturnPath(request.getHeader("Referer")));
        return "login";
    }

    @GetMapping("/signUp")
    public String signUp() {
        return "signUp";
    }

    @GetMapping("/signAgreement")
    public String signAgreement() {
        return "signAgreement";
    }

    @GetMapping("/isUniqueId")
    @ResponseBody
    public ResponseEntity<String> isUniqueId(@RequestParam(required = false) String id) throws Exception {
        if (!StringUtils.hasText(id)) {
            return ResponseEntity.badRequest().body("");
        }
        log.debug("(중복확인용)ID 입력 확인: {}", id);
        return ResponseEntity.ok(memberService.isUniqueId(id));
    }

    @PostMapping("/submitSignUp")
    public String submitSignUp(MemberDTO memberDTO, HttpSession httpSession) throws Exception {
        memberService.submitSignUp(memberDTO);
        httpSession.setAttribute("member", memberService.checkLoginData(memberDTO)); // 로그인도 해줌
        log.debug("회원가입 확인: {}", memberDTO);
        return "redirect:/";
    }

    @PostMapping("/submitLogin")
    public String submitLogin(HttpSession session, MemberDTO memberDTO, Model model) throws Exception {
        MemberDTO loginData = memberService.checkLoginData(memberDTO);
        if (loginData == null) {
            model.addAttribute("message", "아이디 또는 비밀번호가 일치하지 않습니다.");
            return "login";
        }
        session.setAttribute("member", loginData);
        log.debug("로그인 확인: {}", memberDTO);
        Object returnPath = session.getAttribute("pageBeforeLogin");
        String target = returnPath instanceof String ? (String) returnPath : "/";
        return "redirect:" + target;
    }

    @GetMapping(value = "/logout")
    public String logout(HttpSession httpSession) {
        httpSession.invalidate();
        return "redirect:/";
    }

    @GetMapping(value = "/myPage")
    public String myPage() {
        return "myPage";
    }

    @GetMapping(value = "/modifyMyInfo")
    public String modifyMyInfo() {
        return "modifyMyInfo";
    }

    @PostMapping("/submitModifyMyInfo")
    public String submitModifyMyInfo(MemberDTO member, HttpSession session) throws Exception {
        MemberDTO authenticatedMember = (MemberDTO) session.getAttribute("member");
        if (authenticatedMember == null) {
            return "redirect:/login";
        }
        member.setId(authenticatedMember.getId());
        memberService.submitModifyMyInfo(member);
        session.setAttribute("member", memberService.checkLoginData(member)); // 재로그인해서 회원정보갱신
        return "myPage";
    }

    @GetMapping(value = "/modifyMember")
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
    public ResponseEntity<String> checkUniqueId(@RequestParam(required = false) String id) throws Exception {
        if (!StringUtils.hasText(id)) {
            return ResponseEntity.badRequest().body("");
        }
        log.info("아이디 중복 확인 컨트롤러 작동");
        return ResponseEntity.ok(memberService.isUniqueId(id));
    }

    @GetMapping("/checkUniqueEmail")
    @ResponseBody
    public ResponseEntity<String> checkUniqueEmail(@RequestParam(required = false) String email) {
        if (!StringUtils.hasText(email)) {
            return ResponseEntity.badRequest().body("");
        }
        log.info("이멜 중복 확인 컨트롤러 작동");
        return ResponseEntity.ok(memberService.isUniqueEmail(email));
    }

    @GetMapping("/checkUniqueNickName")
    @ResponseBody
    public ResponseEntity<String> checkUniqueNickName(@RequestParam(required = false) String nickName) {
        if (!StringUtils.hasText(nickName)) {
            return ResponseEntity.badRequest().body("");
        }
        log.info("별명 중복 확인 컨트롤러 작동");
        return ResponseEntity.ok(memberService.isUniqueNickName(nickName));
    }

    @GetMapping(value = "/adminPage")
    public String adminPage(Model model, PageDTO page) throws Exception {
        log.info("관리자 모드");
        page = memberService.pageSetting(page);
        PageDTO totalCountPage = new PageDTO();
        totalCountPage.setKeyword("");
        int totalMemberCount = memberService.getTotalMemberCount(totalCountPage);
        model.addAttribute("totalMemberCount", totalMemberCount);
        model.addAttribute("pageInfo", page);
        model.addAttribute("memberList", memberService.getMemberList(page));
        model.addAttribute("recentVisitors", memberService.getRecentVisitors());
        return "adminPage";
    }

    @GetMapping(value = "/modifyMemberByAdmin")
    public String modifyMemberByAdmin(Model model, String id) {
        log.info("관리자의 회원수정 페이지");
        model.addAttribute("member", memberService.getMemberInfo(id));
        return "modifyMemberByAdmin";
    }

    @PostMapping("/submitModifyMemberByAdmin")
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
    public ResponseEntity<Void> extendLogin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        MemberDTO member = session == null ? null : (MemberDTO) session.getAttribute("member");
        if (member == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        session.setMaxInactiveInterval(LOGIN_SESSION_TIMEOUT_SECONDS);
        log.debug("로그인 시간을 연장합니다. memberId={}", member.getId());
        return ResponseEntity.noContent().build();
    }

    private String resolveSafeReturnPath(String referer) {
        if (referer == null || referer.trim().isEmpty()) {
            return "/";
        }
        try {
            URI uri = new URI(referer.trim());
            if (uri.getHost() != null
                    && !"sc1hub.com".equalsIgnoreCase(uri.getHost())
                    && !"www.sc1hub.com".equalsIgnoreCase(uri.getHost())) {
                return "/";
            }
            String path = uri.getRawPath();
            if (path == null || !path.startsWith("/") || path.startsWith("//")) {
                return "/";
            }
            return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
        } catch (URISyntaxException e) {
            return "/";
        }
    }

}
