<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
<title>SC1Hub - 약관 동의</title>
<style>
.terms-section {
    margin-bottom: 20px;
}
.terms-content {
    height: 200px;
    overflow-y: scroll;
    border: 1px solid #ccc;
    padding: 10px;
}
.checkbox-section label {
    vertical-align: middle;
    margin-bottom: 0;
}
</style>
<%@include file="./include/header.jspf" %>
</head>
<body>
    <div class="section-inner">
        <div class="container">
            <div class="row">
                <%@include file="./include/latestPosts.jspf" %>
                <div class="col-sm-12">
                    <h1>약관 동의</h1>

                    <!-- 서비스 이용 약관 -->
                    <div class="terms-section">
                        <div class="terms-header">서비스 이용 약관</div>
                        <div class="terms-content">
                            <%@include file="./include/terms.jspf" %>
                        </div>
                        <div class="checkbox-section" >
                            <input type="checkbox" id="terms1" name="terms1" style="vertical-align: middle;">
                            <label for="terms1">동의</label>
                        </div>
                    </div>

                    <!-- 개인정보처리방침 -->
                    <div class="terms-section">
                        <div class="terms-header">개인정보처리방침</div>
                        <div class="terms-content">
                            <%@include file="./include/privacy.jspf" %>
                        </div>
                        <div class="checkbox-section">
                            <input type="checkbox" id="terms2" name="terms2" style="vertical-align: middle;">
                            <label for="terms2">동의</label>
                        </div>
                    </div>

                    <!-- 다음으로 버튼 -->
                    <button type="button" onclick="goNext()">다음으로</button>
                </div>
            </div>
        </div>
    </div>
<%@include file="./include/footer.jspf" %>

    <script>
        // 다음 페이지로 이동하는 함수
        function goNext() {
            // 체크박스 동의 확인 로직 추가
            if(document.getElementById('terms1').checked && document.getElementById('terms2').checked) {
                // 모든 약관에 동의한 경우 다음 페이지로 이동
                window.location.href = "/signUp";
            } else {
                alert("모든 약관에 동의해야 다음 단계로 진행할 수 있습니다.");
            }
        }
    </script>
</body>
</html>
