package com.sc1hub.assistant.mapper;

import com.sc1hub.assistant.dto.AliasDictionaryDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AliasDictionaryMapper {
    List<AliasDictionaryDTO> selectAll();
}
