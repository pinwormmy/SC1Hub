package com.sc1hub.assistant.controller;

import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.assistant.dto.AssistantSearchTermsReindexResponseDTO;
import com.sc1hub.assistant.search.AssistantSearchTermsIndexService;
import com.sc1hub.member.dto.MemberDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/assistant/search-terms")
@Slf4j
public class AssistantSearchTermsController {

    private final AssistantSearchTermsIndexService indexService;
    private final AssistantProperties assistantProperties;

    public AssistantSearchTermsController(AssistantSearchTermsIndexService indexService,
                                          AssistantProperties assistantProperties) {
        this.indexService = indexService;
        this.assistantProperties = assistantProperties;
    }

    @PostMapping("/reindex")
    public ResponseEntity<AssistantSearchTermsReindexResponseDTO> reindex(HttpSession session,
                                                                          @RequestParam(name = "batchSize", defaultValue = "200") int batchSize) {
        AssistantSearchTermsReindexResponseDTO response = new AssistantSearchTermsReindexResponseDTO();
        MemberDTO member = session == null ? null : (MemberDTO) session.getAttribute("member");
        if (!isAdmin(member)) {
            response.setError("관리자만 재인덱싱을 실행할 수 있습니다.");
            response.setSuccess(false);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        try {
            AssistantSearchTermsIndexService.ReindexResult result = indexService.reindexAll(batchSize);
            response.setSuccess(true);
            response.setBoardCount(result.getBoardCount());
            response.setScannedPosts(result.getScannedPosts());
            response.setUpdatedPosts(result.getUpdatedPosts());
            response.setBatchSize(result.getBatchSize());
            response.setFailedBoards(result.getFailedBoards());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("search_terms 재인덱싱 실패", e);
            response.setSuccess(false);
            response.setError("search_terms 재인덱싱 중 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private boolean isAdmin(MemberDTO member) {
        if (member == null) {
            return false;
        }
        if (member.getGrade() == assistantProperties.getAdminGrade()) {
            return true;
        }
        String adminId = assistantProperties.getAdminId();
        return adminId != null && adminId.equals(member.getId());
    }
}
