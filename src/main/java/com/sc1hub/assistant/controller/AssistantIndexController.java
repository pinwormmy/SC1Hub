package com.sc1hub.assistant.controller;

import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.assistant.dto.AssistantIndexReindexResponseDTO;
import com.sc1hub.assistant.dto.AssistantIndexStatusResponseDTO;
import com.sc1hub.assistant.dto.AssistantIndexUpdateResponseDTO;
import com.sc1hub.assistant.dto.AssistantRagReindexResponseDTO;
import com.sc1hub.assistant.dto.AssistantRagUpdateResponseDTO;
import com.sc1hub.assistant.dto.AssistantSearchTermsReindexResponseDTO;
import com.sc1hub.assistant.rag.AssistantRagIndexService;
import com.sc1hub.assistant.rag.AssistantRagSearchService;
import com.sc1hub.assistant.search.AssistantSearchTermsIndexService;
import com.sc1hub.member.dto.MemberDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/assistant/index")
@Slf4j
public class AssistantIndexController {

    private final AssistantRagIndexService ragIndexService;
    private final AssistantRagSearchService ragSearchService;
    private final AssistantSearchTermsIndexService searchTermsIndexService;
    private final AssistantProperties assistantProperties;

    public AssistantIndexController(AssistantRagIndexService ragIndexService,
                                    AssistantRagSearchService ragSearchService,
                                    AssistantSearchTermsIndexService searchTermsIndexService,
                                    AssistantProperties assistantProperties) {
        this.ragIndexService = ragIndexService;
        this.ragSearchService = ragSearchService;
        this.searchTermsIndexService = searchTermsIndexService;
        this.assistantProperties = assistantProperties;
    }

    @GetMapping("/status")
    public ResponseEntity<AssistantIndexStatusResponseDTO> status() {
        AssistantIndexStatusResponseDTO response = new AssistantIndexStatusResponseDTO();
        response.setSuccess(true);
        response.setRag(ragSearchService.getStatus());
        response.setSearchTerms(searchTermsIndexService.getStatus());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reindex")
    public ResponseEntity<AssistantIndexReindexResponseDTO> reindex(HttpSession session) {
        AssistantIndexReindexResponseDTO response = new AssistantIndexReindexResponseDTO();

        MemberDTO member = session == null ? null : (MemberDTO) session.getAttribute("member");
        if (isAdmin(member)) {
            AssistantRagReindexResponseDTO ragResponse = new AssistantRagReindexResponseDTO();
            boolean ragSuccess = true;
            try {
                AssistantRagIndexService.ReindexJobStatus status = ragIndexService.requestReindex();
                ragResponse.setEnabled(status.isEnabled());
                ragResponse.setAccepted(status.isAccepted());
                ragResponse.setRunning(status.isRunning());
                ragResponse.setStartedAt(status.getStartedAt());
                ragResponse.setFinishedAt(status.getFinishedAt());
                ragResponse.setLastError(status.getLastError());

                AssistantRagIndexService.ReindexResult lastResult = status.getLastResult();
                if (lastResult != null) {
                    ragResponse.setIndexedPosts(lastResult.getIndexedPosts());
                    ragResponse.setIndexedChunks(lastResult.getIndexedChunks());
                    ragResponse.setDimension(lastResult.getDimension());
                    ragResponse.setIndexPath(lastResult.getIndexPath());
                }

                if (!status.isEnabled()) {
                    ragResponse.setError("RAG 기능이 비활성화되어 있습니다.");
                } else if (!status.isAccepted() && status.isRunning()) {
                    ragResponse.setError("RAG 인덱스 생성이 이미 진행 중입니다.");
                }
            } catch (Exception e) {
                ragSuccess = false;
                log.error("RAG 인덱싱 실패", e);
                ragResponse.setError("RAG 인덱싱 중 오류가 발생했습니다.");
            }

            AssistantSearchTermsReindexResponseDTO searchTermsResponse = new AssistantSearchTermsReindexResponseDTO();
            boolean searchTermsSuccess = true;
            try {
                AssistantSearchTermsIndexService.ReindexResult result = searchTermsIndexService.reindexAllDefault();
                searchTermsResponse.setSuccess(true);
                searchTermsResponse.setBoardCount(result.getBoardCount());
                searchTermsResponse.setScannedPosts(result.getScannedPosts());
                searchTermsResponse.setUpdatedPosts(result.getUpdatedPosts());
                searchTermsResponse.setBatchSize(result.getBatchSize());
                searchTermsResponse.setFailedBoards(result.getFailedBoards());
            } catch (Exception e) {
                searchTermsSuccess = false;
                log.error("search_terms 재인덱싱 실패", e);
                searchTermsResponse.setSuccess(false);
                searchTermsResponse.setBatchSize(200);
                searchTermsResponse.setError("search_terms 재인덱싱 중 오류가 발생했습니다.");
            }

            response.setRag(ragResponse);
            response.setSearchTerms(searchTermsResponse);
            response.setSuccess(ragSuccess && searchTermsSuccess);

            if (!response.isSuccess()) {
                StringBuilder summary = new StringBuilder();
                if (!ragSuccess) {
                    summary.append("RAG 인덱싱 실패");
                }
                if (!searchTermsSuccess) {
                    if (summary.length() > 0) {
                        summary.append(" / ");
                    }
                    summary.append("search_terms 재인덱싱 실패");
                }
                if (summary.length() > 0) {
                    response.setError(summary.toString());
                }
            } else if (StringUtils.hasText(ragResponse.getError()) && !ragResponse.isEnabled()) {
                response.setError("RAG 기능이 비활성화되어 search_terms만 재인덱싱했습니다.");
            }

            return ResponseEntity.ok(response);
        }

        response.setSuccess(false);
        response.setError("관리자만 재인덱싱을 실행할 수 있습니다.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @PostMapping("/update")
    public ResponseEntity<AssistantIndexUpdateResponseDTO> update(HttpSession session) {
        AssistantIndexUpdateResponseDTO response = new AssistantIndexUpdateResponseDTO();

        MemberDTO member = session == null ? null : (MemberDTO) session.getAttribute("member");
        if (isAdmin(member)) {
            AssistantRagUpdateResponseDTO ragResponse = new AssistantRagUpdateResponseDTO();
            boolean ragSuccess = true;
            try {
                AssistantRagIndexService.UpdateResult result = ragIndexService.update();
                ragResponse.setEnabled(result.isEnabled());
                ragResponse.setReady(result.isReady());
                ragResponse.setUpdatedPosts(result.getUpdatedPosts());
                ragResponse.setUpdatedChunks(result.getUpdatedChunks());
                ragResponse.setDimension(result.getDimension());
                ragResponse.setIndexPath(result.getIndexPath());

                if (!result.isEnabled()) {
                    ragResponse.setError("RAG 기능이 비활성화되어 있습니다.");
                } else if (!result.isReady()) {
                    ragResponse.setError("RAG 인덱스가 존재하지 않습니다. reindex를 먼저 실행해주세요.");
                }
            } catch (Exception e) {
                ragSuccess = false;
                log.error("RAG update 실패", e);
                ragResponse.setError("RAG 업데이트 중 오류가 발생했습니다.");
            }

            AssistantSearchTermsReindexResponseDTO searchTermsResponse = new AssistantSearchTermsReindexResponseDTO();
            boolean searchTermsSuccess = true;
            try {
                AssistantSearchTermsIndexService.ReindexResult result = searchTermsIndexService.reindexAllDefault();
                searchTermsResponse.setSuccess(true);
                searchTermsResponse.setBoardCount(result.getBoardCount());
                searchTermsResponse.setScannedPosts(result.getScannedPosts());
                searchTermsResponse.setUpdatedPosts(result.getUpdatedPosts());
                searchTermsResponse.setBatchSize(result.getBatchSize());
                searchTermsResponse.setFailedBoards(result.getFailedBoards());
            } catch (Exception e) {
                searchTermsSuccess = false;
                log.error("search_terms 업데이트 실패", e);
                searchTermsResponse.setSuccess(false);
                searchTermsResponse.setBatchSize(200);
                searchTermsResponse.setError("search_terms 업데이트 중 오류가 발생했습니다.");
            }

            response.setRag(ragResponse);
            response.setSearchTerms(searchTermsResponse);
            response.setSuccess(ragSuccess && searchTermsSuccess);

            if (!response.isSuccess()) {
                StringBuilder summary = new StringBuilder();
                if (!ragSuccess) {
                    summary.append("RAG 업데이트 실패");
                }
                if (!searchTermsSuccess) {
                    if (summary.length() > 0) {
                        summary.append(" / ");
                    }
                    summary.append("search_terms 업데이트 실패");
                }
                if (summary.length() > 0) {
                    response.setError(summary.toString());
                }
            } else if (StringUtils.hasText(ragResponse.getError()) && !ragResponse.isEnabled()) {
                response.setError("RAG 기능이 비활성화되어 search_terms만 업데이트했습니다.");
            }

            return ResponseEntity.ok(response);
        }

        response.setSuccess(false);
        response.setError("관리자만 업데이트를 실행할 수 있습니다.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
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
