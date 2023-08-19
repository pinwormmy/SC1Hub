package com.sc1hub.mapper;

import com.sc1hub.util.PageDTO;
import com.sc1hub.member.MemberDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MemberMapper {

    String isUniqueId(String id) throws Exception;

    void submitSignUp(MemberDTO memberDTO) throws Exception;

    MemberDTO checkLoginData(MemberDTO memberDTO) throws Exception;

    void submitModifyMyInfo(MemberDTO member) throws Exception;

    String isUniqueEmail(String email);

    int getTotalMemberCount(PageDTO page);

    MemberDTO getMemberInfo(String id);

    List<MemberDTO> getMemberList(PageDTO pageDTO);

    void submitModifyMemberByAdmin(MemberDTO memberDTO);

    MemberDTO findByEmail(String email);

    void updatePassword(MemberDTO member);

    String getIdByNameAndEmail(@Param("userName") String userName, @Param("email") String email);

    MemberDTO findByUserIdAndEmail(@Param("userId") String userId, @Param("email") String email);

    void deleteMember(String id);
}
