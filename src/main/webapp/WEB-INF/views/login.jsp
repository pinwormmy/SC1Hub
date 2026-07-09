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
                <style>
                .sc-login-card {
                    max-width: 420px;
                    margin: clamp(24px, 6vh, 64px) auto 0;
                }
                .sc-login-card .sc-login-field {
                    display: flex;
                    flex-direction: column;
                    gap: 4px;
                    margin: 10px 4px;
                }
                .sc-login-card .sc-login-field label {
                    color: var(--sc-text-dim);
                    font-size: 0.9rem;
                }
                .sc-login-card input {
                    width: 100%;
                    margin: 0;
                }
                .sc-login-card button {
                    width: 100%;
                    margin: 14px 0 4px;
                }
                .sc-login-card .subMenu {
                    text-align: center;
                    margin-top: 10px;
                    padding-top: 10px;
                    border-top: 1px dashed var(--sc-line-soft);
                    color: var(--sc-text-dim);
                    font-size: 0.92rem;
                }
                </style>
                <div class="loginMenu">
                    <fieldset class="sc-login-card">
                        <legend>[ 로그인 ]</legend>
                        <form action="/submitLogin" method="post">
                            <div class="sc-login-field">
                                <label for="id">회원ID&gt;</label>
                                <input type="text" name="id" id="id" autocomplete="username">
                            </div>
                            <div class="sc-login-field">
                                <label for="pw">패스워드&gt;</label>
                                <input type="password" name="pw" id="pw" autocomplete="current-password">
                            </div>
                            <button id="loginButton" accesskey="l">로그인(L)</button>
                        </form>
                        <div class="subMenu">
                            <div id="right-menu">
                                <a href="/findId">ID 찾기</a> | <a href="/findPassword">패스워드 찾기</a>
                            </div>
                        </div>
                    </fieldset>
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
