<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
<title>SC1Hub - [관리자 모드]회원정보 수정</title>
<%@ include file="/WEB-INF/views/include/header.jspf" %>
</head>
<body>
<div class="section-inner">
    <div class="container">
        <div class="row">
            <%@include file="./include/latestPosts.jspf" %>
            <%@include file="./include/sidebar.jspf" %>
            <div class="col-sm-9">
                <form action="/submitModifyMemberByAdmin" method="post">
                    ID> <input type="text" id="id" name="id" value="${member.id}" readonly title="해당 항목은 수정할 수 없습니다"><br>
                    닉네임> <input type="text" id="nickName" name="nickName" value="${member.nickName}"><br>
                    실명> <input type="text" id="realName" name="realName" value="${member.realName}"><br>
                    이메일> <input type="text" id="email" name="email" value="${member.email}" readonly title="해당 항목은 수정할 수 없습니다"><br>
                    연락처> <input type="text" id="phone" name="phone" value="${member.phone}"><br>
                    회원 등급> <input type="text" id="grade" name="grade" value="${member.grade}"><br>
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
