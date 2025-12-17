<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix = "c" uri = "http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<title>SC1Hub - 로그인</title>
<%@include file="./include/header.jspf" %>
</head>
<body>
<div class="section-inner">
    <div class="container">
        <div class="row">
            <%@include file="./include/latestPosts.jspf" %>
            <div class="col-sm-12">
                <div class="loginMenu">
                    <div class="signUpMenu">
                        <div class="loginTitle">
                        </div>
                        <form action="/submitLogin" method="post">
                            <div class="inputId">
                                회원ID>
                                <input type="text" name="id" id="id">
                            </div>
                            <div>
                                패스워드> <input type="password" name="pw" id="pw">
                            </div>
                            <button id="loginButton" accesskey="l">로그인(L)</button><br>
                            <hr>
                        </form>
                        <div class="subMenu">
                            <div id="right-menu">
                                <a href="/findId">ID 찾기</a> | <a href="/findPassword">패스워드 찾기</a>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>
<%@include file="./include/footer.jspf" %>

<script>
    <c:if test="${not empty message}">
        alert("${message}");
        history.back();
    </c:if>
</script>
</body>
</html>
