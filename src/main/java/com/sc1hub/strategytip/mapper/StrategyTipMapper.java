package com.sc1hub.strategytip.mapper;

import com.sc1hub.common.dto.PageDTO;
import com.sc1hub.strategytip.dto.StrategyTipCategoryDTO;
import com.sc1hub.strategytip.dto.StrategyTipDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StrategyTipMapper {

    List<StrategyTipCategoryDTO> selectCategories();

    List<StrategyTipDTO> selectTips(@Param("page") PageDTO page, @Param("category") String category);

    int countTips(@Param("page") PageDTO page, @Param("category") String category);

    StrategyTipDTO selectTip(int tipNum);

    void insertTip(StrategyTipDTO tip);

    void deleteTip(int tipNum);

    int incrementRecommendCount(int tipNum);
}
