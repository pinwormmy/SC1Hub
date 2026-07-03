package com.sc1hub.chat.mapper;

import com.sc1hub.chat.dto.ChatMessageDTO;
import com.sc1hub.chat.dto.ChatSanctionDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMapper {

    void insertMessages(@Param("messages") List<ChatMessageDTO> messages);

    List<ChatMessageDTO> selectRecentMessages(@Param("limit") int limit);

    Long selectMaxId();

    void markDeleted(@Param("id") long id);

    void insertSanction(ChatSanctionDTO sanction);

    List<ChatSanctionDTO> selectActiveSanctions();

    void revokeSanction(@Param("id") long id);
}
