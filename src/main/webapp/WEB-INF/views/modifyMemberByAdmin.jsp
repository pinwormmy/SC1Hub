<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
<title>SC1Hub - [관리자 모드]회원정보 수정</title>
<%@ include file="/WEB-INF/views/include/head.jspf" %>
</head>
<body>
<%@ include file="/WEB-INF/views/include/header.jspf" %>
<div class="section-inner">
    <div class="container">
        <div class="row">
            <%@include file="./include/latestPosts.jspf" %>
            <div class="col-sm-12">
                <form action="/submitModifyMemberByAdmin" method="post">
                    ID> <input type="text" id="id" name="id" value="<c:out value='${member.id}'/>" readonly title="해당 항목은 수정할 수 없습니다"><br>
                    닉네임> <input type="text" id="nickName" name="nickName" value="<c:out value='${member.nickName}'/>"><br>
                    실명> <input type="text" id="realName" name="realName" value="<c:out value='${member.realName}'/>"><br>
                    이메일> <input type="text" id="email" name="email" value="<c:out value='${member.email}'/>" readonly title="해당 항목은 수정할 수 없습니다"><br>
                    연락처> <input type="text" id="phone" name="phone" value="<c:out value='${member.phone}'/>"><br>
                    회원 등급> <input type="text" id="grade" name="grade" value="<c:out value='${member.grade}'/>"><br>
                    <button type="submit">수정</button>
                    <button type="button" onclick="location.href='/adminPage'">취소</button>
                </form>
            </div>
        </div>
    </div>
</div>
<%@ include file="/WEB-INF/views/include/footer.jspf" %>
</body>
</html>
