package com.sc1hub.strategytip.controller;

import com.sc1hub.common.dto.PageDTO;
import com.sc1hub.member.dto.MemberDTO;
import com.sc1hub.strategytip.dto.StrategyTipDTO;
import com.sc1hub.strategytip.service.StrategyTipService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/strategy-tips")
public class StrategyTipController {

    private final StrategyTipService strategyTipService;

    public StrategyTipController(StrategyTipService strategyTipService) {
        this.strategyTipService = strategyTipService;
    }

    @GetMapping
    public String list(PageDTO page, @RequestParam(required = false) String category, Model model) {
        model.addAttribute("koreanTitle", "한줄 공략");
        model.addAttribute("metaDescription", "스타크래프트 종족전, 팀플, 꿀팁을 짧게 공유하는 한줄 공략");
        model.addAttribute("category", category);
        model.addAttribute("categories", strategyTipService.getCategories());
        model.addAttribute("page", strategyTipService.pageSetting(page, category));
        model.addAttribute("tips", strategyTipService.getTips(page, category));
        return "strategyTip/list";
    }

    @PostMapping
    public String add(StrategyTipDTO tip, HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            strategyTipService.addTip(tip, getMember(session));
            redirectAttributes.addFlashAttribute("msg", "한줄 공략을 등록했습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("msg", e.getMessage());
        }
        return "redirect:/strategy-tips";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam int tipNum, @RequestParam(required = false) String guestPassword,
            HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            strategyTipService.deleteTip(tipNum, guestPassword, getMember(session));
            redirectAttributes.addFlashAttribute("msg", "한줄 공략을 삭제했습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("msg", e.getMessage());
        }
        return "redirect:/strategy-tips";
    }

    @PostMapping("/recommend")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> recommend(@RequestParam int tipNum) {
        try {
            int recommendCount = strategyTipService.recommend(tipNum);
            Map<String, Object> response = new HashMap<>();
            response.put("recommendCount", recommendCount);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(Collections.singletonMap("message", e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    private MemberDTO getMember(HttpSession session) {
        return session == null ? null : (MemberDTO) session.getAttribute("member");
    }
}
