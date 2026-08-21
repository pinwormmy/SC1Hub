<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
<title>글쓰기 - ${koreanTitle}</title>
<%@include file="../include/head.jspf" %>
<link rel="stylesheet" href="/css/post-editor.css?v=${applicationScope.assetVersion}">
</head>
<body>
<%@include file="../include/header.jspf" %>
<div class="section-inner">
    <div class="container">
        <div class="row">
            <%@include file="../include/latestPosts.jspf" %>
            <div class="col-sm-12">
                <div class="sc-panel">
                    <%@include file="../include/writePostContent.jspf" %>
                </div>
            </div>
        </div>
    </div>
</div>
<%@include file="../include/footer.jspf" %>
<script src="/js/post-editor.js?v=${applicationScope.assetVersion}"></script>
</body>
</html>
