package com.sc1hub.seo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.board.dto.BoardDTO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeoMetadataServiceTest {

    private final SeoMetadataService service = new SeoMetadataService(new ObjectMapper());

    @Test
    void applyPost_buildsEscapedStructuredDataAndPlainDescription() {
        BoardDTO post = new BoardDTO();
        post.setPostNum(2);
        post.setTitle("973 빌드 </script>");
        post.setContent("<h2>빌드 오더</h2><p>9오버 &amp; 9풀</p>");
        post.setWriter("admin");
        post.setRegDate(new Date(0));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("canonical", "https://sc1hub.com/boards/zvspboard/readPost?postNum=2");
        ExtendedModelMap model = new ExtendedModelMap();

        service.applyPost(model, request, "저프전 게시판", post);

        String description = (String) model.get("metaDescription");
        String structuredData = (String) model.get("structuredDataJson");
        assertTrue(description.contains("빌드 오더 9오버 & 9풀"));
        assertFalse(structuredData.contains("</script>"));
        assertTrue(structuredData.contains("\\u003c/script\\u003e"));
    }

    @Test
    void postPreviewUsesCrawlableImageAndUnmodifiedHeadline() throws Exception {
        BoardDTO post = new BoardDTO();
        post.setTitle("실전 빌드");
        post.setContent("<script>hidden script</script><p>&#xBE4C;드 &copy;</p>"
                + "<img src='data:image/png;base64,abc'><img src='/uploads/hero.webp'>");
        post.setRegDate(new Date(0));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("canonical", "https://sc1hub.com/boards/zvspboard/readPost?postNum=2");
        ExtendedModelMap model = new ExtendedModelMap();
        service.applyPost(model, request, "저프전", post);
        var json = new ObjectMapper().readTree((String) model.get("structuredDataJson"));
        assertEquals("https://sc1hub.com/uploads/hero.webp", model.get("socialImage"));
        assertEquals(model.get("socialImage"), json.get("image").asText());
        assertEquals("실전 빌드", json.get("headline").asText());
        assertFalse(json.has("dateModified"));
        assertEquals("저프전 - 빌드 ©", model.get("metaDescription"));
    }

    @Test
    void unusableImagesFallBackToDefaultPreview() {
        BoardDTO post = new BoardDTO();
        post.setContent("<img src='javascript:alert(1)'><img src='https://user:pass@example.com/a.jpg'>");
        ExtendedModelMap model = new ExtendedModelMap();
        service.applyPost(model, new MockHttpServletRequest(), "저프전", post);
        assertEquals(SeoMetadataService.DEFAULT_SOCIAL_IMAGE, model.get("socialImage"));
    }
}
