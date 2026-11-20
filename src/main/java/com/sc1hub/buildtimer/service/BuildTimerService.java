package com.sc1hub.buildtimer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.board.support.BoardTitleNormalizer;
import com.sc1hub.buildtimer.dto.BuildTimerBuildDTO;
import com.sc1hub.buildtimer.dto.BuildTimerCheckpointDTO;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 빌드오더 연습 타이머에 싣는 빌드 목록. 빌드 순서와 체크포인트는 SC1Hub 공략 글에서 옮긴 값만 쓰고,
 * 원글에 없는 시간은 만들어 넣지 않는다. 데이터는 buildtimer/builds.json 하나로 관리한다.
 */
@Service
public class BuildTimerService {

    private static final String CATALOG_PATH = "buildtimer/builds.json";
    private static final Set<String> VALID_RACES = Set.of("terran", "zerg", "protoss");

    private final List<BuildTimerBuildDTO> builds;
    private final String buildsJson;
    private final Map<String, List<BuildTimerBuildDTO>> buildsBySourceUrl;

    public BuildTimerService(ObjectMapper objectMapper) {
        List<BuildTimerBuildDTO> loaded = loadBuilds(objectMapper);
        validateBuilds(loaded);
        this.builds = List.copyOf(loaded);
        this.buildsJson = toScriptSafeJson(objectMapper, builds);
        this.buildsBySourceUrl = indexBySourceUrl(this.builds);
    }

    public List<BuildTimerBuildDTO> getBuilds() {
        return builds;
    }

    /** &lt;script type="application/json"&gt; 안에 그대로 넣어도 태그가 닫히지 않는 JSON. */
    public String getBuildsJson() {
        return buildsJson;
    }

    public List<BuildTimerBuildDTO> findByPost(String boardTitle, int postNum) {
        if (boardTitle == null || boardTitle.isBlank() || postNum <= 0) {
            return List.of();
        }
        String normalizedBoard = BoardTitleNormalizer.normalizeNullable(boardTitle);
        String key = normalizeSourceUrl("/boards/" + normalizedBoard + "/readPost?postNum=" + postNum);
        return buildsBySourceUrl.getOrDefault(key, List.of());
    }

    private static Map<String, List<BuildTimerBuildDTO>> indexBySourceUrl(List<BuildTimerBuildDTO> builds) {
        Map<String, List<BuildTimerBuildDTO>> grouped = new HashMap<>();
        for (BuildTimerBuildDTO build : builds) {
            String key = normalizeSourceUrl(build.getSourceUrl());
            if (!key.isEmpty()) {
                grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(build);
            }
        }
        Map<String, List<BuildTimerBuildDTO>> immutable = new HashMap<>(grouped.size());
        for (Map.Entry<String, List<BuildTimerBuildDTO>> entry : grouped.entrySet()) {
            immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(immutable);
    }

    private static String normalizeSourceUrl(String sourceUrl) {
        return sourceUrl == null ? "" : sourceUrl.trim().toLowerCase(Locale.ROOT);
    }

    private static List<BuildTimerBuildDTO> loadBuilds(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource(CATALOG_PATH).getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<List<BuildTimerBuildDTO>>() { });
        } catch (IOException e) {
            throw new UncheckedIOException("빌드 타이머 데이터를 읽지 못했습니다: " + CATALOG_PATH, e);
        }
    }

    private static void validateBuilds(List<BuildTimerBuildDTO> builds) {
        if (builds == null || builds.isEmpty()) {
            throw new IllegalStateException("빌드 타이머 카탈로그가 비어 있습니다: " + CATALOG_PATH);
        }
        Set<String> seenIds = new HashSet<>();
        for (BuildTimerBuildDTO build : builds) {
            if (build == null || build.getId() == null || build.getId().isBlank()) {
                throw new IllegalStateException("빌드 ID가 비어 있습니다.");
            }
            if (!seenIds.add(build.getId())) {
                throw new IllegalStateException("중복된 빌드 ID입니다: " + build.getId());
            }
            if (build.getRace() == null || !VALID_RACES.contains(build.getRace())) {
                throw new IllegalStateException("올바르지 않은 종족 값입니다: " + build.getId());
            }
            if (build.getSteps() == null || build.getSteps().isEmpty()) {
                throw new IllegalStateException("빌드 단계가 비어 있습니다: " + build.getId());
            }
            int previousAt = -1;
            for (BuildTimerCheckpointDTO checkpoint : build.getCheckpoints()) {
                if (checkpoint.getAt() <= previousAt) {
                    throw new IllegalStateException("체크포인트 시각이 오름차순이 아닙니다: " + build.getId());
                }
                previousAt = checkpoint.getAt();
            }
        }
    }

    private static String toScriptSafeJson(ObjectMapper objectMapper, List<BuildTimerBuildDTO> builds) {
        try {
            return objectMapper.writeValueAsString(builds)
                    .replace("<", "\\u003c")
                    .replace(">", "\\u003e")
                    .replace("&", "\\u0026");
        } catch (IOException e) {
            throw new UncheckedIOException("빌드 타이머 데이터를 JSON으로 바꾸지 못했습니다.", e);
        }
    }
}
