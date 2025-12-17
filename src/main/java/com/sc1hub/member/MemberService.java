package com.sc1hub.member;

import com.sc1hub.common.dto.PageDTO;

import java.util.List;

public interface MemberService {
    String isUniqueId(String id) throws Exception;

    void submitSignUp(MemberDTO memberDTO) throws Exception;

    MemberDTO checkLoginData(MemberDTO memberDTO) throws Exception;

    void submitModifyMyInfo(MemberDTO member) throws Exception;

    String isUniqueEmail(String email);

    PageDTO pageSetting(PageDTO page) throws Exception;

    MemberDTO getMemberInfo(String id);

    List<MemberDTO> getMemberList(PageDTO page) throws Exception;

    int getTotalMemberCount(PageDTO page) throws Exception;

    void submitModifyMemberByAdmin(MemberDTO memberDTO);

    boolean findCredentials(String email);

    String findIdByNameAndEmail(String userName, String email);

    String findPassword(String userId, String email);

    void deleteMember(String id);

    String isUniqueNickName(String nickName);

    List<VisitorsDTO> getRecentVisitors();
}
