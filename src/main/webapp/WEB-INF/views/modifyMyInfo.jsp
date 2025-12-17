<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
<title>SC1Hub - 회원정보 수정</title>
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
                        <div class="member-info" style="line-height: 50px;">
                            <form action="/submitModifyMyInfo" id="modifyMyInfo" method="post">
                                <table>
                                    <tr>
                                        <td>ID> </td>
                                        <td>${member.id}<input type="hidden" name="id" value="${member.id}"></td>
                                    </tr>
                                    <tr>
                                        <td>별명> </td>
                                        <td><input type="text" name="nickName" value="${member.nickName}"></td>
                                    </tr>
                                    <tr>
                                        <td>비밀번호></td>
                                        <td><input type="password" name="pw" placeholder="비밀번호를 입력하세요" value=""></td>
                                    </tr>
                                    <tr>
                                        <td>비밀번호확인></td>
                                        <td><input type="password" name="pw2" placeholder="비밀번호를 다시 입력하세요" value=""></td>
                                    </tr>
                                    <tr>
                                        <td>이메일> </td>
                                        <td><input type="email" name="email" value="${member.email}"></td>
                                    </tr>
                                </table>
                                <div style="margin-top: 20px;">
                                    <button type="button" onclick="checkSignupForm();">수정하기</button>
                                    <button type="button" onclick="location.href='/myPage'">취소</button>
                                    <button type="button" onclick="confirmDelete();">탈퇴하기</button>
                               </div>
                           </form>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>
<%@include file="./include/footer.jspf" %>

<script>
let modifyMyInfoForm = document.getElementById("modifyMyInfo");

function checkSignupForm() {
    const pw = modifyMyInfoForm.pw.value;
    const pw2 = modifyMyInfoForm.pw2.value;

    if (pw.length < 4 || pw.length > 16) {
        alert("비밀번호는 4~16자리로 설정해주세요.");
        modifyMyInfoForm.pw.focus();
        return false;
    }

    if (pw !== pw2) {
        alert("비밀번호가 일치하지 않습니다!");
        modifyMyInfoForm.pw2.focus();
        return false;
    }

    modifyMyInfoForm.submit();
}

function confirmDelete() {
    if(confirm("정말로 탈퇴하시겠습니까? 탈퇴 시 모든 데이터가 삭제됩니다.")) {
        $.ajax({
            url: '/deleteMyAccount',
            type: 'POST',
            contentType: 'application/json',
            dataType: 'json',
            success: function(data) {
                if(data.success) {
                    alert("탈퇴가 완료되었습니다. 감사합니다.");
                    location.href = '/';
                } else {
                    alert("탈퇴 처리 중 오류가 발생했습니다. 다시 시도해주세요.");
                }
            },
            error: function(err) {
                console.error(err); // 오류 발생 시 콘솔에 오류 로그 출력
                alert("탈퇴 처리 중 오류가 발생했습니다. 다시 시도해주세요.");
            }
        });
    }
}
</script>
</body>
</html>
