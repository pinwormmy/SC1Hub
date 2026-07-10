<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <c:set var="pageTitle" value="${not empty pageTitle ? pageTitle : '안내 - SC1Hub'}" />
    <c:set var="robots" value="noindex,nofollow,noarchive" />
    <c:set var="metaDescription" value="요청을 처리할 수 없습니다." />
    <title><c:out value="${pageTitle}"/></title>
    <%@include file="./include/head.jspf" %>
</head>
<body>
    <main class="section-inner">
        <div class="container">
            <div class="sc-panel text-center">
                <h1>요청을 처리할 수 없습니다</h1>
                <p><c:out value="${msg}"/></p>
                <p><a class="btn btn-theme" href="<c:out value='${url}'/>">돌아가기</a></p>
            </div>
        </div>
    </main>
</body>
</html>
