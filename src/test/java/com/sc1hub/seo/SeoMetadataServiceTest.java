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
    void editedPostsReportWhenTheyWereModified() throws Exception {
        BoardDTO post = new BoardDTO();
        post.setTitle("973 빌드");
        post.setRegDate(new Date(0));
        post.setModDate(new Date(86_400_000L));
        ExtendedModelMap model = new ExtendedModelMap();

        service.applyPost(model, new MockHttpServletRequest(), "저프전", post);

        var json = new ObjectMapper().readTree((String) model.get("structuredDataJson"));
        assertEquals("1970-01-02T09:00:00+09:00", json.get("dateModified").asText());
    }

    @Test
    void postAndBoardPagesCarryABreadcrumbTrail() throws Exception {
        BoardDTO post = new BoardDTO();
        post.setTitle("973 빌드");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("canonical", "https://sc1hub.com/boards/zvspboard/readPost?postNum=2");
        ExtendedModelMap model = new ExtendedModelMap();

        service.applyPost(model, request, "저프전 게시판", post);

        var items = new ObjectMapper().readTree((String) model.get("breadcrumbDataJson")).get("itemListElement");
        assertEquals(3, items.size());
        assertEquals("https://sc1hub.com/", items.get(0).get("item").asText());
        assertEquals("저프전 게시판", items.get(1).get("name").asText());
        assertEquals("https://sc1hub.com/boards/zvspboard", items.get(1).get("item").asText());
        assertEquals("973 빌드", items.get(2).get("name").asText());
        assertEquals("https://sc1hub.com/boards/zvspboard/readPost?postNum=2", items.get(2).get("item").asText());

        MockHttpServletRequest listRequest = new MockHttpServletRequest();
        listRequest.setAttribute("canonical", "https://sc1hub.com/boards/zvspboard?recentPage=2");
        ExtendedModelMap listModel = new ExtendedModelMap();
        service.applyBoardList(listModel, listRequest, "저프전 게시판", null);
        var listItems = new ObjectMapper().readTree((String) listModel.get("breadcrumbDataJson")).get("itemListElement");
        assertEquals(2, listItems.size());
        assertEquals("https://sc1hub.com/boards/zvspboard", listItems.get(1).get("item").asText());

        ExtendedModelMap homeModel = new ExtendedModelMap();
        service.applyHome(homeModel, new MockHttpServletRequest());
        assertFalse(homeModel.containsAttribute("breadcrumbDataJson"));
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
