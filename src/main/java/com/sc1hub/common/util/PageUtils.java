package com.sc1hub.common.util;

import com.sc1hub.common.dto.PageDTO;

public final class PageUtils {

    private PageUtils() {
    }

    public static PageDTO normalize(PageDTO page, String defaultSearchType) {
        PageDTO target = page == null ? new PageDTO() : page;

        if (target.getRecentPage() < 1) {
            target.setRecentPage(1);
        }
        if (target.getSearchType() == null || target.getSearchType().trim().isEmpty()) {
            target.setSearchType(defaultSearchType);
        }
        if (target.getKeyword() == null) {
            target.setKeyword("");
        }

        return target;
    }

    public static PageDTO calculate(PageDTO page, int totalPostCount, int displayPostLimit, int pageSetLimit) {
        PageDTO target = page == null ? new PageDTO() : page;
        target.setTotalPostCount(totalPostCount);

        PageService pageService = new PageService();
        pageService.setDISPLAY_POST_LIMIT(displayPostLimit);
        pageService.setPAGESET_LIMIT(pageSetLimit);

        return pageService.calculatePage(target);
    }
}
