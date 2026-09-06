package com.sc1hub.member.mapper;

import com.sc1hub.member.dto.VisitorsDTO;
import com.sc1hub.common.dto.PageDTO;
import com.sc1hub.member.dto.MemberDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

    /** BCrypt 접두사가 아닌 저장값을 가진 회원(id, pw 만). 일괄 승격 전용. */
    List<MemberDTO> selectLegacyPasswordMembers();

    int countLegacyPasswordMembers();

    /** 읽었을 때의 값과 같을 때만 갱신하는 낙관적 UPDATE. 갱신된 행 수를 돌려준다. */
    int updatePasswordIfUnchanged(@Param("id") String id, @Param("expectedPw") String expectedPw,
                                  @Param("newPw") String newPw);

    void deleteMember(String id);

    List<VisitorsDTO> getRecentVisitors();
}
