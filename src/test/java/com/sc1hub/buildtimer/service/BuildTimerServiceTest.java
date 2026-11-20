package com.sc1hub.buildtimer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.buildtimer.dto.BuildTimerBuildDTO;
import com.sc1hub.buildtimer.dto.BuildTimerCheckpointDTO;
import com.sc1hub.buildtimer.dto.BuildTimerStepDTO;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildTimerServiceTest {

    private static final Set<String> MATCHUPS = Set.of("테저전", "테프전", "테테전", "저테전", "저프전", "저저전", "프테전", "프저전", "프프전");

    private final BuildTimerService service = new BuildTimerService(new ObjectMapper());

    @Test
    void catalog_loadsCompleteBuildsWithUniqueIds() {
        List<BuildTimerBuildDTO> builds = service.getBuilds();
        assertFalse(builds.isEmpty());

        Set<String> ids = new HashSet<>();
        for (BuildTimerBuildDTO build : builds) {
            assertTrue(ids.add(build.getId()), "중복 id: " + build.getId());
            assertTrue(Set.of("terran", "zerg", "protoss").contains(build.getRace()), build.getId());
            assertTrue(MATCHUPS.contains(build.getMatchup()), build.getId());
            assertNotBlank(build.getName(), build.getId());
            assertNotBlank(build.getSummary(), build.getId());
            assertNotBlank(build.getSourceTitle(), build.getId());
            assertNotNull(build.getSteps(), build.getId());
            assertTrue(build.getSteps().size() >= 2, build.getId());
            assertNotNull(build.getCheckpoints(), build.getId());
            for (BuildTimerStepDTO step : build.getSteps()) {
                assertNotBlank(step.getAction(), build.getId());
            }
        }
    }

    @Test
    void catalog_citesSc1HubPostsAsSources() {
        for (BuildTimerBuildDTO build : service.getBuilds()) {
            assertTrue(build.getSourceUrl().matches("/boards/[a-z]+/readPost\\?postNum=\\d+"),
                    build.getId() + " -> " + build.getSourceUrl());
        }
    }

    @Test
    void checkpoints_areOrderedAndCarryTheSourceTimeLabel() {
        for (BuildTimerBuildDTO build : service.getBuilds()) {
            int previous = -1;
            for (BuildTimerCheckpointDTO checkpoint : build.getCheckpoints()) {
                assertTrue(checkpoint.getAt() > previous, build.getId() + " 체크포인트 순서");
                previous = checkpoint.getAt();
                assertNotBlank(checkpoint.getTime(), build.getId());
                assertNotBlank(checkpoint.getText(), build.getId());
            }
        }
    }

    @Test
    void buildsJson_cannotCloseTheEmbeddingScriptTag() throws Exception {
        String json = service.getBuildsJson();
        assertFalse(json.contains("<"));
        assertFalse(json.contains(">"));
        assertFalse(json.contains("&"));

        BuildTimerBuildDTO[] parsed = new ObjectMapper().readValue(json, BuildTimerBuildDTO[].class);
        assertEquals(service.getBuilds().size(), parsed.length);
        assertEquals(service.getBuilds().get(0).getSteps(), List.of(parsed[0].getSteps().toArray()));
    }

    private static void assertNotBlank(String value, String context) {
        assertTrue(value != null && !value.isBlank(), context);
    }
}
