<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
<title>SC1Hub - 마이 페이지</title>
<%@include file="./include/header.jspf" %>
</head>
<body>
<div class="section-inner">
    <div class="container">
        <div class="row">
            <%@include file="./include/latestPosts.jspf" %>
            <div class="col-sm-12">
                <div class="media">
                    <div class="media-body">
                        <div class="member-info" style="font-size: 20px; line-height: 50px;">
                            <span>ID : ${member.id}</span><br>
                            <span>실명 : ${member.realName}</span><br>
                            <span>별명 : ${member.nickName}</span><br>
                            <span>연락처 : ${member.phone}</span><br>
                            <span>이메일 : ${member.email}</span><br>
                            <hr>
                        </div>
                    </div>
                </div>
                <div>
                    <button onclick="location.href='/modifyMyInfo'">내 정보 수정</button>
                </div>
                <c:if test="${member.grade == 3}">
                    <div>
                        <button onclick="location.href='/adminPage'">관리자 모드</button>
                    </div>
                </c:if>
            </div>
        </div>
    </div>
</div>
<%@include file="./include/footer.jspf" %>
<script>
    function comingSoon() {
        alert("업데이트 예정입니다.");
    }
</script>
</body>
</html>
