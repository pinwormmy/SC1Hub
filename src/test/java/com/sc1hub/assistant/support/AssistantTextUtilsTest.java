package com.sc1hub.assistant.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantTextUtilsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void stripHtmlToText_handlesNullBlankTagsEntitiesAndWhitespace() {
        assertEquals("", AssistantTextUtils.stripHtmlToText(null));
        assertEquals("", AssistantTextUtils.stripHtmlToText("   \n\t  "));
        assertEquals("아비터 &amp; <리콜> '핵' \"팁\" 테스트",
                AssistantTextUtils.stripHtmlToText(
                        "<p>아비터&nbsp;&amp;amp;&nbsp;&lt;리콜&gt;&nbsp;&#39;핵&#39;&nbsp;&quot;팁&quot;<br/>테스트</p>"));
        assertEquals("테란 & 저그 < > \" ' 끝",
                AssistantTextUtils.stripHtmlToText(
                        "<div>테란 &amp; 저그 &lt; &gt; &quot; &#39;\n   끝</div>"));
    }

    @Test
    void looksLikeJsonArray_andParseTerms_handleJsonAndDelimitedFallbacks() {
        assertFalse(AssistantTextUtils.looksLikeJsonArray(null));
        assertFalse(AssistantTextUtils.looksLikeJsonArray("   "));
        assertFalse(AssistantTextUtils.looksLikeJsonArray("{\"a\":1}"));
        assertTrue(AssistantTextUtils.looksLikeJsonArray("  [\"질럿\", \"드라군\"]  "));

        assertEquals(Collections.emptyList(), AssistantTextUtils.parseTerms(null, objectMapper));
        assertEquals(Collections.emptyList(), AssistantTextUtils.parseTerms("  ", objectMapper));
        assertEquals(Arrays.asList("23넥", "더블 넥서스"),
                AssistantTextUtils.parseTerms(" [\"23넥\", \"더블 넥서스\"] ", objectMapper));
        assertEquals(Arrays.asList("[invalid", "json]"),
                AssistantTextUtils.parseTerms("[invalid, json]", objectMapper));
        assertEquals(Arrays.asList("23넥", "더블", "패스트 캐리어"),
                AssistantTextUtils.parseTerms("23넥, 더블\n패스트 캐리어\r\n", objectMapper));
        assertEquals(Arrays.asList("드라군", "사업"),
                AssistantTextUtils.parseTerms("[\"드라군\", \"사업\"]", objectMapper));
        assertEquals(Arrays.asList("드라군", "사업"),
                AssistantTextUtils.parseTerms("드라군, 사업", null));
    }

    @Test
    void stripCodeFences_removesMarkdownJsonFences() {
        assertEquals("", AssistantTextUtils.stripCodeFences(null));
        assertEquals("", AssistantTextUtils.stripCodeFences("   "));
        assertEquals("{\"answer\":\"ok\"}",
                AssistantTextUtils.stripCodeFences("```json\n{\"answer\":\"ok\"}\n```"));
        assertEquals("[1, 2, 3]",
                AssistantTextUtils.stripCodeFences("```JSON\n[1, 2, 3]\n```"));
    }

    @Test
    void extractFirstBalancedJson_handlesSurroundingProseNestedStructuresStringsAndEscapes() {
        String withProse = "설명입니다:\n```json\n{\"answer\":\"중괄호 { } \\\"이스케이프\\\" 테스트\",\"nested\":{\"arr\":[1,2]}}\n```\n감사합니다.";
        assertEquals("{\"answer\":\"중괄호 { } \\\"이스케이프\\\" 테스트\",\"nested\":{\"arr\":[1,2]}}",
                AssistantTextUtils.extractFirstJsonObject(withProse));

        String arrayProse = "추천 순서: [3, 1, 2] 입니다.";
        assertEquals("[3, 1, 2]", AssistantTextUtils.extractFirstJsonArray(arrayProse));

        assertEquals("", AssistantTextUtils.extractFirstJsonObject("{\"unclosed\": {\"a\":1}"));
        assertEquals("", AssistantTextUtils.extractFirstBalancedJson(null, '{', '}'));
    }
}
