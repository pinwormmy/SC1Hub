package com.sc1hub.common.util;

import com.sc1hub.common.dto.PageDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PageUtilsTest {

    @Test
    void normalize_setsDefaultValues() {
        PageDTO page = new PageDTO();
        page.setRecentPage(0);
        page.setSearchType("   ");
        page.setKeyword(null);

        PageDTO result = PageUtils.normalize(page, "title");

        assertSame(page, result);
        assertEquals(1, result.getRecentPage());
        assertEquals("title", result.getSearchType());
        assertEquals("", result.getKeyword());
    }

    @Test
    void calculate_appliesConfiguredLimits() {
        PageDTO page = new PageDTO();
        page.setRecentPage(2);

        PageDTO result = PageUtils.calculate(page, 31, 15, 5);

        assertSame(page, result);
        assertEquals(15, result.getDisplayPostLimit());
        assertEquals(3, result.getTotalPage());
        assertEquals(15, result.getPostBeginPoint());
        assertEquals(30, result.getPostEndPoint());
        assertEquals(1, result.getPageBeginPoint());
        assertEquals(3, result.getPageEndPoint());
    }
}