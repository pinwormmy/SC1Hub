package com.sc1hub.seo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.board.dto.BoardDTO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;

import java.util.Date;

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
}
