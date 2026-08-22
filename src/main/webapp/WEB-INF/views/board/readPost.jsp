<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt"  prefix="fmt"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<title><c:out value="${pageTitle}"/></title>
<link rel="stylesheet" type="text/css" href="/css/readPost.css?v=${applicationScope.assetVersion}">
<%@include file="../include/head.jspf" %>
</head>
<body>
<%@include file="../include/header.jspf" %>
<div class="section-inner">
    <div class="container">
        <div class="row">
            <%@include file="../include/latestPosts.jspf" %>
            <div class="col-sm-12">
                <div class="sc-panel">
                    <%@include file="../include/readPostContent.jspf" %>
                </div>
            </div>
        </div>
    </div>
</div>
<%@include file="../include/readPostScript.jspf" %>
<script src="/js/readPost.js?v=${applicationScope.assetVersion}"></script>
<%@include file="../include/footer.jspf" %>
</body>
</html>
