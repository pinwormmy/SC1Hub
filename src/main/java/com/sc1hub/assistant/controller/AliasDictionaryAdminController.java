package com.sc1hub.assistant.controller;

import com.sc1hub.assistant.dto.AliasDictionaryDTO;
import com.sc1hub.assistant.dto.AliasDictionaryFormDTO;
import com.sc1hub.assistant.service.AliasDictionaryAdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/adminPage/aliasDictionary")
@Slf4j
public class AliasDictionaryAdminController {

    private final AliasDictionaryAdminService aliasDictionaryAdminService;

    public AliasDictionaryAdminController(AliasDictionaryAdminService aliasDictionaryAdminService) {
        this.aliasDictionaryAdminService = aliasDictionaryAdminService;
    }

    @GetMapping
    public String aliasDictionaryPage(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) Long editId,
                                      Model model) {
        prepareModel(model, keyword, editId, null, null);
        return "adminAliasDictionary";
    }

    @PostMapping("/create")
    public String createAliasDictionary(AliasDictionaryFormDTO form,
                                        @RequestParam(required = false) String keyword,
                                        Model model,
                                        RedirectAttributes redirectAttributes) {
        String validationMessage = validateForm(form, false);
        if (validationMessage != null) {
            prepareModel(model, keyword, null, form, validationMessage);
            return "adminAliasDictionary";
        }

        try {
            aliasDictionaryAdminService.create(form);
            redirectAttributes.addFlashAttribute("message", "별칭이 등록되었습니다.");
            if (StringUtils.hasText(keyword)) {
                redirectAttributes.addAttribute("keyword", keyword);
            }
            return "redirect:/adminPage/aliasDictionary";
        } catch (DuplicateKeyException e) {
            log.warn("alias_dictionary alias 중복", e);
            prepareModel(model, keyword, null, form, "이미 등록된 alias 입니다.");
            return "adminAliasDictionary";
        } catch (Exception e) {
            log.error("alias_dictionary 등록 실패", e);
            prepareModel(model, keyword, null, form, "등록 중 오류가 발생했습니다.");
            return "adminAliasDictionary";
        }
    }

    @PostMapping("/update")
    public String updateAliasDictionary(AliasDictionaryFormDTO form,
                                        @RequestParam(required = false) String keyword,
                                        Model model,
                                        RedirectAttributes redirectAttributes) {
        String validationMessage = validateForm(form, true);
        if (validationMessage != null) {
            prepareModel(model, keyword, form.getId(), form, validationMessage);
            return "adminAliasDictionary";
        }

        try {
            boolean updated = aliasDictionaryAdminService.update(form);
            if (!updated) {
                prepareModel(model, keyword, form.getId(), form, "수정 대상이 존재하지 않습니다.");
                return "adminAliasDictionary";
            }
            redirectAttributes.addFlashAttribute("message", "별칭이 수정되었습니다.");
            if (StringUtils.hasText(keyword)) {
                redirectAttributes.addAttribute("keyword", keyword);
            }
            return "redirect:/adminPage/aliasDictionary";
        } catch (DuplicateKeyException e) {
            log.warn("alias_dictionary alias 중복", e);
            prepareModel(model, keyword, form.getId(), form, "이미 등록된 alias 입니다.");
            return "adminAliasDictionary";
        } catch (Exception e) {
            log.error("alias_dictionary 수정 실패", e);
            prepareModel(model, keyword, form.getId(), form, "수정 중 오류가 발생했습니다.");
            return "adminAliasDictionary";
        }
    }

    @PostMapping("/delete")
    public String deleteAliasDictionary(@RequestParam("id") Long id,
                                        @RequestParam(required = false) String keyword,
                                        RedirectAttributes redirectAttributes) {
        if (id == null) {
            redirectAttributes.addFlashAttribute("message", "삭제 대상이 없습니다.");
            return "redirect:/adminPage/aliasDictionary";
        }
        try {
            boolean deleted = aliasDictionaryAdminService.delete(id);
            if (!deleted) {
                redirectAttributes.addFlashAttribute("message", "삭제 대상이 존재하지 않습니다.");
            } else {
                redirectAttributes.addFlashAttribute("message", "별칭이 삭제되었습니다.");
            }
        } catch (Exception e) {
            log.error("alias_dictionary 삭제 실패", e);
            redirectAttributes.addFlashAttribute("message", "삭제 중 오류가 발생했습니다.");
        }
        if (StringUtils.hasText(keyword)) {
            redirectAttributes.addAttribute("keyword", keyword);
        }
        return "redirect:/adminPage/aliasDictionary";
    }

    private void prepareModel(Model model,
                              String keyword,
                              Long editId,
                              AliasDictionaryFormDTO form,
                              String message) {
        List<AliasDictionaryDTO> aliases = aliasDictionaryAdminService.list(keyword);
        Map<Long, String> canonicalDisplay = new HashMap<>();
        Map<Long, String> boardTargetDisplay = new HashMap<>();
        for (AliasDictionaryDTO alias : aliases) {
            canonicalDisplay.put(alias.getId(), aliasDictionaryAdminService.formatTermsForDisplay(alias.getCanonicalTerms()));
            boardTargetDisplay.put(alias.getId(), aliasDictionaryAdminService.formatBoardTargetsForDisplay(alias));
        }
        String createBoardTargetsText = "";
        String editBoardTargetsText = "";

        model.addAttribute("aliasList", aliases);
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("canonicalDisplay", canonicalDisplay);
        model.addAttribute("boardTargetDisplay", boardTargetDisplay);

        if (editId != null) {
            AliasDictionaryDTO editItem = aliasDictionaryAdminService.findById(editId);
            if (editItem != null) {
                AliasDictionaryFormDTO editForm = new AliasDictionaryFormDTO();
                editForm.setId(editItem.getId());
                editForm.setAlias(editItem.getAlias());
                editForm.setCanonicalTerms(aliasDictionaryAdminService.formatTermsForInput(editItem.getCanonicalTerms()));
                editForm.setBoardTargets(aliasDictionaryAdminService.resolveBoardTargetsForForm(editItem));
                model.addAttribute("editForm", editForm);
                editBoardTargetsText = toBoardTargetsMarker(editForm);
            }
            model.addAttribute("editId", editId);
        }

        if (form != null) {
            if (editId != null) {
                model.addAttribute("editForm", form);
                editBoardTargetsText = toBoardTargetsMarker(form);
            } else {
                model.addAttribute("createForm", form);
                createBoardTargetsText = toBoardTargetsMarker(form);
            }
        }
        model.addAttribute("createBoardTargetsText", createBoardTargetsText);
        model.addAttribute("editBoardTargetsText", editBoardTargetsText);

        if (message != null) {
            model.addAttribute("message", message);
        }
    }

    private String validateForm(AliasDictionaryFormDTO form, boolean requireId) {
        if (form == null) {
            return "입력값이 없습니다.";
        }
        if (requireId && form.getId() == null) {
            return "수정 대상이 없습니다.";
        }
        if (!StringUtils.hasText(form.getAlias())) {
            return "alias는 필수입니다.";
        }
        if (!StringUtils.hasText(form.getCanonicalTerms())) {
            return "canonical terms는 필수입니다.";
        }
        return null;
    }

    private static String toBoardTargetsMarker(AliasDictionaryFormDTO form) {
        if (form == null || form.getBoardTargets() == null || form.getBoardTargets().isEmpty()) {
            return "";
        }
        StringBuilder marker = new StringBuilder(",");
        for (String boardTarget : form.getBoardTargets()) {
            if (!StringUtils.hasText(boardTarget)) {
                continue;
            }
            marker.append(boardTarget.trim()).append(",");
        }
        return marker.toString();
    }
}
