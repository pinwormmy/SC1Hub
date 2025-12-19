package com.sc1hub.assistant;

import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.assistant.dto.AssistantRagReindexResponseDTO;
import com.sc1hub.assistant.dto.AssistantRagUpdateResponseDTO;
import com.sc1hub.assistant.rag.AssistantRagIndexService;
import com.sc1hub.assistant.rag.AssistantRagSearchService;
import com.sc1hub.member.MemberDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/assistant/rag")
@Slf4j
public class AssistantRagController {

    private final AssistantRagIndexService ragIndexService;
    private final AssistantRagSearchService ragSearchService;
    private final AssistantProperties assistantProperties;

    public AssistantRagController(AssistantRagIndexService ragIndexService,
                                  AssistantRagSearchService ragSearchService,
                                  AssistantProperties assistantProperties) {
        this.ragIndexService = ragIndexService;
        this.ragSearchService = ragSearchService;
        this.assistantProperties = assistantProperties;
    }

    @GetMapping("/status")
    public AssistantRagSearchService.Status status() {
        return ragSearchService.getStatus();
    }

    @PostMapping("/reindex")
    public ResponseEntity<AssistantRagReindexResponseDTO> reindex(HttpSession session,
                                                                  @RequestParam(name = "async", defaultValue = "true") boolean async) {
        AssistantRagReindexResponseDTO response = new AssistantRagReindexResponseDTO();

        MemberDTO member = session == null ? null : (MemberDTO) session.getAttribute("member");
        if (isAdmin(member)) {
            if (async) {
                AssistantRagIndexService.ReindexJobStatus status = ragIndexService.requestReindex();
                response.setEnabled(status.isEnabled());
                response.setAccepted(status.isAccepted());
                response.setRunning(status.isRunning());
                response.setStartedAt(status.getStartedAt());
                response.setFinishedAt(status.getFinishedAt());
                response.setLastError(status.getLastError());

                AssistantRagIndexService.ReindexResult lastResult = status.getLastResult();
                if (lastResult != null) {
                    response.setIndexedPosts(lastResult.getIndexedPosts());
                    response.setIndexedChunks(lastResult.getIndexedChunks());
                    response.setDimension(lastResult.getDimension());
                    response.setIndexPath(lastResult.getIndexPath());
                }

                if (!status.isEnabled()) {
                    response.setError("RAG 기능이 비활성화되어 있습니다.");
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
                }
                if (!status.isAccepted() && status.isRunning()) {
                    response.setError("RAG 인덱스 생성이 이미 진행 중입니다.");
                }
                return ResponseEntity.status(status.isAccepted() ? HttpStatus.ACCEPTED : HttpStatus.OK).body(response);
            }

            try {
                AssistantRagIndexService.ReindexResult result = ragIndexService.reindex();
                response.setEnabled(result.isEnabled());
                response.setAccepted(true);
                response.setRunning(false);
                response.setIndexedPosts(result.getIndexedPosts());
                response.setIndexedChunks(result.getIndexedChunks());
                response.setDimension(result.getDimension());
                response.setIndexPath(result.getIndexPath());
                return ResponseEntity.ok(response);
            } catch (Exception e) {
                log.error("RAG 인덱스 생성 실패", e);
                response.setError("RAG 인덱스 생성 중 오류가 발생했습니다.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
        }

        response.setError("관리자만 인덱스를 생성할 수 있습니다.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @PostMapping("/update")
    public ResponseEntity<AssistantRagUpdateResponseDTO> update(HttpSession session) {
        AssistantRagUpdateResponseDTO response = new AssistantRagUpdateResponseDTO();

        MemberDTO member = session == null ? null : (MemberDTO) session.getAttribute("member");
        if (isAdmin(member)) {
            try {
                AssistantRagIndexService.UpdateResult result = ragIndexService.update();
                response.setEnabled(result.isEnabled());
                response.setReady(result.isReady());
                response.setUpdatedPosts(result.getUpdatedPosts());
                response.setUpdatedChunks(result.getUpdatedChunks());
                response.setDimension(result.getDimension());
                response.setIndexPath(result.getIndexPath());

                if (!result.isEnabled()) {
                    response.setError("RAG 기능이 비활성화되어 있습니다.");
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
                }
                if (!result.isReady()) {
                    response.setError("RAG 인덱스가 존재하지 않습니다. reindex를 먼저 실행해주세요.");
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
                }

                return ResponseEntity.ok(response);
            } catch (IllegalStateException e) {
                log.warn("RAG 인덱스 업데이트 실패 (설정/상태 문제)", e);
                response.setError(e.getMessage() != null ? e.getMessage() : "RAG 인덱스 업데이트에 실패했습니다.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            } catch (Exception e) {
                log.error("RAG 인덱스 업데이트 실패", e);
                response.setError("RAG 인덱스 업데이트 중 오류가 발생했습니다.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
        }

        response.setError("관리자만 인덱스를 업데이트할 수 있습니다.");
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
