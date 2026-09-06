package com.sc1hub.member.mapper;

import com.sc1hub.member.dto.VisitorsDTO;
import com.sc1hub.common.dto.PageDTO;
import com.sc1hub.member.dto.MemberDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MemberMapper {

    String isUniqueId(String id) throws Exception;

    String isUniqueNickName(String nickName);

    String isUniqueEmail(String email);

    void submitSignUp(MemberDTO memberDTO) throws Exception;

    void submitModifyMyInfo(MemberDTO member) throws Exception;

    int getTotalMemberCount(PageDTO page);

    MemberDTO getMemberInfo(String id);

    List<MemberDTO> getMemberList(PageDTO pageDTO);

    void submitModifyMemberByAdmin(MemberDTO memberDTO);


    void updatePassword(MemberDTO member);

    void deleteMember(String id);

    List<VisitorsDTO> getRecentVisitors();
}
