package com.sc1hub.member.service;

import com.sc1hub.common.dto.PageDTO;
import com.sc1hub.member.mapper.MemberMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceImplPageSettingTest {

    @Mock
    private EmailService emailService;

    @Mock
    private MemberMapper memberMapper;

    @InjectMocks
    private MemberServiceImpl memberService;

    @Test
    void pageSetting_setsDefaults_andCalculates() {
        PageDTO page = new PageDTO();
        page.setRecentPage(0);
        page.setSearchType(null);
        page.setKeyword(null);

        when(memberMapper.getTotalMemberCount(any(PageDTO.class))).thenReturn(23);

        PageDTO result = memberService.pageSetting(page);

        assertSame(page, result);
        assertEquals(1, result.getRecentPage());
        assertEquals("id", result.getSearchType());
        assertEquals("", result.getKeyword());
        assertEquals(10, result.getDisplayPostLimit());
        assertEquals(3, result.getTotalPage());
        assertEquals(0, result.getPostBeginPoint());
        assertEquals(10, result.getPostEndPoint());
        verify(memberMapper).getTotalMemberCount(page);
    }
}