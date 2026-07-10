package com.sc1hub.strategytip.controller;

import com.sc1hub.common.dto.PageDTO;
import com.sc1hub.common.exception.ResourceNotFoundException;
import com.sc1hub.member.dto.MemberDTO;
import com.sc1hub.seo.SeoMetadataService;
import com.sc1hub.strategytip.dto.StrategyTipCategoryDTO;
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
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/strategy-tips")
public class StrategyTipController {

    private final StrategyTipService strategyTipService;
    private final SeoMetadataService seoMetadataService;

    public StrategyTipController(StrategyTipService strategyTipService, SeoMetadataService seoMetadataService) {
        this.strategyTipService = strategyTipService;
        this.seoMetadataService = seoMetadataService;
    }

    @GetMapping
    public String list(PageDTO page, @RequestParam(required = false) String category,
                       Model model, HttpServletRequest request) {
        String normalizedCategory = trimToNull(category);
        List<StrategyTipCategoryDTO> categories = strategyTipService.getCategories();
        String categoryName = findCategoryName(categories, normalizedCategory);
        if (normalizedCategory != null && categoryName == null) {
            throw new ResourceNotFoundException("존재하지 않는 한줄 공략 분류입니다.");
        }
        model.addAttribute("koreanTitle", "한줄 공략");
        model.addAttribute("category", normalizedCategory);
        model.addAttribute("categories", categories);
        PageDTO resolvedPage = strategyTipService.pageSetting(page, normalizedCategory);
        model.addAttribute("page", resolvedPage);
        model.addAttribute("tips", strategyTipService.getTips(page, normalizedCategory));
        seoMetadataService.applyStrategyTips(model, request, categoryName, resolvedPage);
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

    private String findCategoryName(List<StrategyTipCategoryDTO> categories, String category) {
        if (category == null || categories == null) {
            return null;
        }
        for (StrategyTipCategoryDTO candidate : categories) {
            if (candidate != null && category.equals(candidate.getCode())) {
                return candidate.getName();
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }
}
