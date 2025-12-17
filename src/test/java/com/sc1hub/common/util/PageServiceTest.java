package com.sc1hub.common.util;

import com.sc1hub.common.dto.PageDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PageServiceTest {

    @Test
    void calculatePage_setsPaginationFields() {
        PageService service = new PageService();

        PageDTO page = new PageDTO();
        page.setRecentPage(1);
        page.setTotalPostCount(95);

        PageDTO result = service.calculatePage(page);

        assertSame(page, result);
        assertEquals(1, result.getPageBeginPoint());
        assertEquals(10, result.getPageEndPoint());
        assertEquals(0, result.getPrevPageSetPoint());
        assertEquals(11, result.getNextPageSetPoint());
        assertEquals(10, result.getTotalPage());
        assertEquals(0, result.getPostBeginPoint());
        assertEquals(10, result.getPostEndPoint());
        assertEquals(10, result.getDisplayPostLimit());
    }

    @Test
    void calculatePage_respectsCustomLimits() {
        PageService service = new PageService();
        service.setDISPLAY_POST_LIMIT(15);
        service.setPAGESET_LIMIT(5);

        PageDTO page = new PageDTO();
        page.setRecentPage(2);
        page.setTotalPostCount(31);

        PageDTO result = service.calculatePage(page);

        assertEquals(15, result.getDisplayPostLimit());
        assertEquals(3, result.getTotalPage());
        assertEquals(15, result.getPostBeginPoint());
        assertEquals(30, result.getPostEndPoint());
        assertEquals(1, result.getPageBeginPoint());
        assertEquals(3, result.getPageEndPoint());
        assertEquals(0, result.getPrevPageSetPoint());
        assertEquals(6, result.getNextPageSetPoint());
    }
}

