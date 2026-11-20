package com.sc1hub.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiExceptionHandlerTest {

    @Test
    void restControllerErrorsReturnJsonInsteadOfHtmlAlertView() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SampleApiController())
                .setControllerAdvice(new ApiExceptionHandler(), new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/sample/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("요청 형식이 올바르지 않습니다."));

        mockMvc.perform(get("/api/sample/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("대상 없음"));
    }

    @RestController
    private static class SampleApiController {
        @GetMapping("/api/sample/missing")
        String missing() {
            throw new ResourceNotFoundException("대상 없음");
        }

        @GetMapping("/api/sample/{id}")
        String byId(@PathVariable int id) {
            return String.valueOf(id);
        }
    }
}
