package com.sc1hub.member.controller;

import com.sc1hub.common.dto.PageDTO;
import com.sc1hub.common.security.AttackContentDetector;
import com.sc1hub.common.security.OffenderTracker;
import com.sc1hub.member.dto.MemberDTO;
import com.sc1hub.common.util.IpService;
import com.sc1hub.member.service.LoginAttemptGuard;
import com.sc1hub.member.service.MemberService;
import com.sc1hub.member.service.MemberSessionRegistry;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

@Controller
@Slf4j
public class MemberController {
    private static final int LOGIN_SESSION_TIMEOUT_SECONDS = 30 * 60;

    private static final String LOGIN_THROTTLED_MESSAGE = "로그인 시도가 너무 많습니다. 5분 후 다시 시도해 주세요.";
    // 가입 폼의 숨김 필드. 사람은 채우지 않고 자동화 도구만 채우므로 값이 있으면 가입을 거부한다.
    static final String SIGNUP_HONEYPOT_FIELD = "homepage";

    private final MemberService memberService;
    private final LoginAttemptGuard loginAttemptGuard;
    private final MemberSessionRegistry sessionRegistry;
    private final OffenderTracker offenderTracker;
    private final AttackContentDetector attackContentDetector;

    public MemberController(MemberService memberService, LoginAttemptGuard loginAttemptGuard,
                            MemberSessionRegistry sessionRegistry, OffenderTracker offenderTracker,
                            AttackContentDetector attackContentDetector) {
        this.memberService = memberService;
        this.loginAttemptGuard = loginAttemptGuard;
        this.sessionRegistry = sessionRegistry;
        this.offenderTracker = offenderTracker;
        this.attackContentDetector = attackContentDetector;
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
    public String submitSignUp(HttpServletRequest request, MemberDTO memberDTO, HttpSession httpSession,
                               Model model) throws Exception {
        if (StringUtils.hasText(request.getParameter(SIGNUP_HONEYPOT_FIELD))) {
            offenderTracker.strike(IpService.getRemoteIP(request), IpService.hasForwardedClient(request),
                    null, null, "가입 봇 필드 입력");
            model.addAttribute("msg", "가입 정보를 확인해주세요.");
            model.addAttribute("url", "/signUp");
            return "alert";
        }
        AttackContentDetector.Verdict verdict = memberDTO == null ? AttackContentDetector.Verdict.NONE
                : attackContentDetector.inspect(0, memberDTO.getId(), memberDTO.getNickName(),
                        memberDTO.getRealName(), memberDTO.getEmail());
        if (verdict.isAttack()) {
            String ip = IpService.getRemoteIP(request);
            offenderTracker.attackDetected(ip, IpService.hasForwardedClient(request), null, null, verdict);
            log.warn("공격 패턴 가입 시도 거부 - rule={}, severity={}, ip={}", verdict.rule(), verdict.severity(), ip);
            model.addAttribute("msg", "가입 정보를 확인해주세요.");
            model.addAttribute("url", "/signUp");
            return "alert";
        }
        memberService.submitSignUp(memberDTO);
        MemberDTO loginData = memberService.checkLoginData(memberDTO); // 로그인도 해줌
        rotateSession(request); // 세션 고정 공격 방지: 인증 후 세션 ID 교체
        httpSession.setAttribute("member", loginData);
        registerSession(loginData, httpSession);
        log.debug("회원가입 확인: {}", memberDTO.getId());
        return "redirect:/";
    }

    @PostMapping("/submitLogin")
    public String submitLogin(HttpServletRequest request, HttpSession session, MemberDTO memberDTO,
                              Model model) throws Exception {
        String ip = IpService.getRemoteIP(request);
        boolean ipTrusted = IpService.hasForwardedClient(request);
        if (ipTrusted && loginAttemptGuard.isIpBlocked(ip)) {
            log.warn("로그인 시도 IP 잠금 상태의 접근입니다. memberId={}, ip={}", memberDTO.getId(), ip);
            model.addAttribute("message", LOGIN_THROTTLED_MESSAGE);
            return "login";
        }
        if (loginAttemptGuard.isBlocked(memberDTO.getId())) {
            log.warn("로그인 시도 잠금 상태의 접근입니다. memberId={}, ip={}", memberDTO.getId(), ip);
            model.addAttribute("message", LOGIN_THROTTLED_MESSAGE);
            return "login";
        }
        MemberDTO loginData = memberService.checkLoginData(memberDTO);
        if (loginData == null) {
            loginAttemptGuard.recordFailure(memberDTO.getId());
            if (ipTrusted) {
                loginAttemptGuard.recordIpFailure(ip);
            }
            offenderTracker.strike(ip, ipTrusted, null, null, "로그인 실패");
            model.addAttribute("message", "아이디 또는 비밀번호가 일치하지 않습니다.");
            return "login";
        }
        loginAttemptGuard.reset(memberDTO.getId());
        Object returnPath = session.getAttribute("pageBeforeLogin");
        rotateSession(request); // 세션 고정 공격 방지: 인증 후 세션 ID 교체
        session.setAttribute("member", loginData);
        registerSession(loginData, session);
        log.debug("로그인 확인: {}", memberDTO.getId());
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
        MemberDTO refreshed = memberService.checkLoginData(member); // 재로그인해서 회원정보갱신
        session.setAttribute("member", refreshed);
        registerSession(refreshed, session);
        // 비밀번호가 바뀌었을 수 있으므로 다른 기기의 기존 세션은 회수한다.
        sessionRegistry.invalidateMemberExcept(authenticatedMember.getId(), session);
        return "myPage";
    }

    @GetMapping(value = "/modifyMember")
    public String modifyMember() {
        return "modifyMember";
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
        // 등급 변경 등으로 세션 정보가 낡았으므로 대상 회원의 기존 세션을 회수한다.
        sessionRegistry.invalidateMember(memberDTO.getId());
        return "redirect:/adminPage";
    }

    @GetMapping(value = "/findId")
    public String findId() {
        log.info("아이디 찾기 페이지");
        return "findId";
    }

    @GetMapping(value = "/findPassword")
    public String findPassword() {
        log.info("패스워드 찾기 페이지");
        return "findPassword";
    }

    @PostMapping("/deleteMember")
    @ResponseBody
    public ResponseEntity<Map<String, Boolean>> deleteMember(@RequestParam("id") String id) {
        Map<String, Boolean> response = new HashMap<>();
        try {
            memberService.deleteMember(id);
            sessionRegistry.invalidateMember(id); // 탈퇴 처리된 회원의 기존 세션을 즉시 종료한다.
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
            // 현재 세션과 다른 기기의 세션을 모두 종료한다.
            sessionRegistry.invalidateMember(userId);
            HttpSession current = request.getSession(false);
            if (current != null) {
                try {
                    current.invalidate();
                } catch (IllegalStateException alreadyInvalidated) {
                    // 레지스트리가 이미 무효화한 세션
                }
            }
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

    /** 인증 성공 시 세션 ID를 교체해 세션 고정(fixation) 공격을 무력화한다. */
    private void rotateSession(HttpServletRequest request) {
        if (request == null) {
            return;
        }
        if (request.getSession(false) == null) {
            request.getSession(true);
        }
        try {
            request.changeSessionId();
        } catch (IllegalStateException noSession) {
            // 세션이 없으면 교체할 것도 없다.
        }
    }

    private void registerSession(MemberDTO member, HttpSession session) {
        if (member != null && session != null) {
            sessionRegistry.register(member.getId(), session);
        }
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
