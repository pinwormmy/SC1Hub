package com.sc1hub.board.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.member.dto.MemberDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommentDTOJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void passwordIsWriteOnlyAndClientCannotSupplyMemberId() throws Exception {
        CommentDTO request = objectMapper.readValue(
                "{\"postNum\":7,\"id\":\"forged\",\"password\":\"secret\",\"content\":\"댓글\"}",
                CommentDTO.class);

        assertNull(request.getId());
        assertEquals("secret", request.getPassword());

        MemberDTO member = new MemberDTO();
        member.setId("private-id");
        member.setNickName("공개 닉네임");
        request.setId("server-owner");
        request.setMemberDTO(member);

        String response = objectMapper.writeValueAsString(request);

        assertFalse(response.contains("secret"));
        assertFalse(response.contains("server-owner"));
        assertFalse(response.contains("private-id"));
        assertTrue(response.contains("공개 닉네임"));
    }
}
