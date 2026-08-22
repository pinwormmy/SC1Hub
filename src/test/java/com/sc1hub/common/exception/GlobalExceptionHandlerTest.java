package com.sc1hub.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;

import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
