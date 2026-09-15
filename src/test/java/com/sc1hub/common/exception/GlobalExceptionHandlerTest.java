package com.sc1hub.common.exception;

import com.sc1hub.board.support.BoardTitleNormalizer;
import com.sc1hub.board.support.InvalidBoardException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import jakarta.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class GlobalExceptionHandlerTest {

    @Test
    void handleResourceNotFoundException_returnsReal404() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExtendedModelMap model = new ExtendedModelMap();

        String view = handler.handleResourceNotFoundException(
                new ResourceNotFoundException("존재하지 않는 게시글입니다."), model, response);

        assertEquals("alert", view);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
        assertEquals("noindex,nofollow,noarchive", model.get("robots"));
    }

    @Test
    void handleMethodNotSupported_returns405AndPreventsIndexing() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExtendedModelMap model = new ExtendedModelMap();

        String view = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("GET", java.util.List.of("POST")),
                model, response);

        assertEquals("alert", view);
        assertEquals(HttpServletResponse.SC_METHOD_NOT_ALLOWED, response.getStatus());
        assertEquals("POST", response.getHeader("Allow"));
        assertEquals("noindex,nofollow,noarchive", response.getHeader("X-Robots-Tag"));
        assertEquals("noindex,nofollow,noarchive", model.get("robots"));
    }

    @Test
    void methodMismatch_isHandledThroughDispatcherServlet() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PostOnlyController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/post-only"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(header().string("X-Robots-Tag", "noindex,nofollow,noarchive"))
                .andExpect(view().name("alert"));
    }

    @Test
    void missingParameter_returns400WithoutStackTrace() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExtendedModelMap model = new ExtendedModelMap();

        String view = handler.handleMissingParameter(
                new org.springframework.web.bind.MissingServletRequestParameterException("postNum", "int"),
                model, response);

        assertEquals("alert", view);
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
        assertEquals("noindex,nofollow,noarchive", response.getHeader("X-Robots-Tag"));
    }

    @Test
    void invalidBoard_returns404AndWarnInsteadOf400Error() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExtendedModelMap model = new ExtendedModelMap();

        String view = handler.handleInvalidBoardException(
                new InvalidBoardException("올바르지 않은 게시판 주소입니다."), model, response);

        assertEquals("alert", view);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
        assertEquals("noindex,nofollow,noarchive", response.getHeader("X-Robots-Tag"));
        assertEquals("올바르지 않은 게시판 주소입니다.", model.get("msg"));
    }

    @Test
    void invalidBoard_isRoutedToThe404HandlerNotTheIllegalArgument400Handler() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new BoardStubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/boards/supportboard"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Robots-Tag", "noindex,nofollow,noarchive"))
                .andExpect(view().name("alert"));

        mockMvc.perform(get("/boards/funboard"))
                .andExpect(status().isOk());
    }

    @RestController
    private static class BoardStubController {

        @GetMapping("/boards/{boardTitle}")
        String board(@PathVariable String boardTitle) {
            return BoardTitleNormalizer.requireValid(boardTitle);
        }
    }

    @RestController
    private static class PostOnlyController {

        @PostMapping("/post-only")
        String postOnly() {
            return "ok";
        }
    }
}
