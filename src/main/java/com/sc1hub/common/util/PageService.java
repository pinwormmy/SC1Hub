package com.sc1hub.common.util;

import lombok.extern.slf4j.Slf4j;
import com.sc1hub.common.dto.PageDTO;

@Slf4j
public class PageService {

    // default값이고, 기준 변경은 개별 서비스에서 아래 set함수 활용해서 진행
    int DISPLAY_POST_LIMIT = 10;
    int PAGESET_LIMIT = 10;

    public void setDISPLAY_POST_LIMIT(int displayPostLimit) {
        this.DISPLAY_POST_LIMIT = displayPostLimit;
    }

    public void setPAGESET_LIMIT(int pageSetLimit) {
        this.PAGESET_LIMIT = pageSetLimit;
    }

    public PageDTO calculatePage(PageDTO page) {

        int postEndPoint = page.getRecentPage() * DISPLAY_POST_LIMIT;
        int postBeginPoint = postEndPoint - DISPLAY_POST_LIMIT;

        int totalPage = (int) Math.ceil((double) page.getTotalPostCount() / DISPLAY_POST_LIMIT);
        int pageBeginPoint = (page.getRecentPage() - 1) / PAGESET_LIMIT * PAGESET_LIMIT + 1;
        int pageEndPoint = pageBeginPoint + PAGESET_LIMIT - 1;
        if (pageEndPoint > totalPage)
            pageEndPoint = totalPage;

        int prevPageSetPoint = pageBeginPoint - 1;
        int nextPageSetPoint = pageBeginPoint + PAGESET_LIMIT;

        page.setPageBeginPoint(pageBeginPoint);
        page.setPostEndPoint(postEndPoint);
        page.setDisplayPostLimit(DISPLAY_POST_LIMIT);
        page.setPostBeginPoint(postBeginPoint);
        page.setPageEndPoint(pageEndPoint);
        page.setPrevPageSetPoint(prevPageSetPoint);
        page.setNextPageSetPoint(nextPageSetPoint);
        page.setTotalPage(totalPage);
        log.debug("페이지 서비스단계 : 페이지 계산 후 : {}", page);
        return page;
    }
}
