package com.sc1hub.strategytip.service;

import com.sc1hub.common.dto.PageDTO;
import com.sc1hub.member.dto.MemberDTO;
import com.sc1hub.strategytip.dto.StrategyTipCategoryDTO;
import com.sc1hub.strategytip.dto.StrategyTipDTO;

import java.util.List;

public interface StrategyTipService {

    List<StrategyTipCategoryDTO> getCategories();

    List<StrategyTipDTO> getTips(PageDTO page, String category);

    PageDTO pageSetting(PageDTO page, String category);

    void addTip(StrategyTipDTO tip, MemberDTO member);

    void deleteTip(int tipNum, String guestPassword, MemberDTO member);

    int recommend(int tipNum);
}
