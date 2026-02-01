package com.sc1hub.assistant.mapper;

import com.sc1hub.assistant.dto.AliasDictionaryDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AliasDictionaryMapper {
    List<AliasDictionaryDTO> selectAll();

    List<AliasDictionaryDTO> search(@Param("keyword") String keyword);

    AliasDictionaryDTO selectById(@Param("id") long id);

    int insert(AliasDictionaryDTO alias);

    int update(AliasDictionaryDTO alias);

    int delete(@Param("id") long id);
}
