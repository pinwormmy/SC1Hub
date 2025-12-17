<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
<title>글수정 - ${koreanTitle}</title>
<%@include file="../include/header.jspf" %>
</head>
<body>
<div class="section-inner">
    <div class="container">
        <div class="row">
            <%@include file="../include/latestPosts.jspf" %>
            <%@include file="../include/sidebar.jspf" %>
            <div class="col-sm-9">
                <%@include file="../include/modifyPostContent.jspf" %>
            </div>
        </div>
    </div>
</div>
<%@include file="../include/footer.jspf" %>

<script>
let noticeChecked = document.getElementById("noticeChecked");
let noticeUnchecked = document.getElementById("noticeUnchecked");

function checkboxForNotice() {
    if(noticeChecked.checked) { noticeUnchecked.disabled = true; }
    else { noticeUnchecked.disabled = false; }
}
</script>

</body>
</html>
