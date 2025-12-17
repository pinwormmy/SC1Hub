<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
<title>글쓰기 - ${koreanTitle}</title>
<%@include file="../include/header.jspf" %>
</head>
<body>
<div class="section-inner">
    <div class="container">
        <div class="row">
            <%@include file="../include/latestPosts.jspf" %>
            <%@include file="../include/sidebar.jspf" %>
            <div class="col-sm-9">
                <%@include file="../include/writePostContent.jspf" %>
            </div>
        </div>
    </div>
</div>
<%@include file="../include/footer.jspf" %>
</body>
</html>
