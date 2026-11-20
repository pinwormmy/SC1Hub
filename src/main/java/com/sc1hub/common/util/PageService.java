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
        int recentPage = Math.max(1, page.getRecentPage());
        int displayLimit = Math.max(1, DISPLAY_POST_LIMIT);
        int pageSetLimit = Math.max(1, PAGESET_LIMIT);
        int totalPostCount = Math.max(0, page.getTotalPostCount());

        int totalPage = totalPostCount == 0
                ? 0
                : (int) (((long) totalPostCount + displayLimit - 1L) / displayLimit);
        long postEndLong = (long) recentPage * displayLimit;
        long postBeginLong = postEndLong - displayLimit;
        int postBeginPoint = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, postBeginLong));
        int postEndPoint = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, postEndLong));

        long pageBeginLong = ((long) (recentPage - 1) / pageSetLimit) * pageSetLimit + 1L;
        int pageBeginPoint = (int) Math.min(Integer.MAX_VALUE, pageBeginLong);
        long pageEndLong = pageBeginLong + pageSetLimit - 1L;
        int pageEndPoint = (int) Math.min(totalPage, pageEndLong);

        int prevPageSetPoint = pageBeginPoint - 1;
        long nextPageSetLong = pageBeginLong + pageSetLimit;
        int nextPageSetPoint = (int) Math.min(Integer.MAX_VALUE, nextPageSetLong);

        page.setRecentPage(recentPage);
        page.setPageBeginPoint(pageBeginPoint);
        page.setPostEndPoint(postEndPoint);
        page.setDisplayPostLimit(displayLimit);
        page.setPostBeginPoint(postBeginPoint);
        page.setPageEndPoint(pageEndPoint);
        page.setPrevPageSetPoint(prevPageSetPoint);
        page.setNextPageSetPoint(nextPageSetPoint);
        page.setTotalPage(totalPage);
        log.debug("페이지 서비스단계 : 페이지 계산 후 : {}", page);
        return page;
    }
}
