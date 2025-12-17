<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
<title>SC1Hub - 회원가입</title>
<style>
    .infoText {
        color: #808080; /* 회색 폰트 색상 */
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
                <div class="loginMenu">
                    <div class="signUpMenu">
                        <form action="/submitSignUp" name="submitSignUp" id="submitSignUp" method="post">
                            '*'표시된 항목은 꼭 입력해주세요!
                            <div class="inputId">
                                회원ID*>
                                <input type="text" name="id" id="inputId"><br>
                                <span id="idCheckText" class="infoText">ID를 입력해주세요.</span>
                            </div>
                            <div>
                                패스워드*>
                                <input type="password" name="pw" id="pw" placeholder="4~16 자리">
                            </div>
                            <div>
                                패스워드 재입력*>
                                <input type="password" name="pw2" id="pw2">
                            </div>
                            <div>
                                별명*>
                                <input type="text" name="nickName" id="nickName"><br>
                                <span id="nickNameCheckText" class="infoText">별명을 입력해주세요.</span>
                            </div>
                            <br><br>
                            <div>
                                회원명>
                                <input type="text" name="realName" id="realName">
                            </div>
                            <div>
                                연락처>
                                <input type="text" name="phone" id="phone"><br>
                            </div>
                            <div>
                                이메일>
                                <input type="email" id="email" name="email"><br>
                            </div>
                            <br>
                            <button type="button" class="basicButton" onclick="checkSignupForm();" accesskey="s">가입하기(S)</button><br>
                        </form>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>
<%@include file="./include/footer.jspf" %>


<script type="text/javascript">
    let submitSignUpForm = document.getElementById("submitSignUp");
    let isUniqueIdValid = false;
    let isUniqueNickNameValid = false;

    const inputId = document.getElementById("inputId");
    const idCheckText = document.getElementById("idCheckText");
    const nickName = document.getElementById("nickName");
    const nickNameCheckText = document.getElementById("nickNameCheckText");

    const isIdValid = (inputId) => {
      const idPattern = /^[a-z]+[a-z0-9]{3,19}$/g;
      return idPattern.test(inputId);
    };

    inputId.addEventListener("focusout", function () {
        const inputIdValue = submitSignUpForm.elements.id.value;
        if (inputIdValue === "") {
            idCheckText.innerHTML = "아이디를 입력해주세요.";
            isUniqueIdValid = false;
            return false;
        }
        if (!isIdValid(inputIdValue)) {
            idCheckText.innerHTML = "아이디 양식을 확인하세요!";
            isUniqueIdValid = false;
            return false;
        }
        checkUniqueId(inputIdValue);
    });

    const checkUniqueId = (inputId) => {
      fetch("<%=request.getContextPath()%>/checkUniqueId?id=" + inputId)
        .then(response => response.json())
        .then(data => {
          if (data === 0) {
            idCheckText.innerHTML = "해당 ID 사용가능합니다.";
            isUniqueIdValid = true;
          } else {
            idCheckText.innerHTML = "사용할 수 없는 ID입니다.";
            isUniqueIdValid = false;
          }
        })
        .catch(error => {
          alert("(아이디중복검사)서버 요청 실패...", error);
        });
    };

    nickName.addEventListener("focusout", function () {
        const nickNameValue = nickName.value;
        if (nickNameValue === "") {
            nickNameCheckText.innerHTML = "별명을 입력해주세요.";
            isUniqueNickNameValid = false;
            return;
        }
        checkUniqueNickName(nickNameValue);
    });

    const checkUniqueNickName = (nickName) => {
        fetch("<%=request.getContextPath()%>/checkUniqueNickName?nickName=" + nickName)
            .then(response => response.json())
            .then(data => {
                if (data === 0) {
                    nickNameCheckText.innerHTML = "해당 별명 사용 가능합니다.";
                    isUniqueNickNameValid = true;
                } else {
                    nickNameCheckText.innerHTML = "사용할 수 없는 별명입니다.";
                    isUniqueNickNameValid = false;
                }
            })
            .catch(error => {
                alert("서버 요청 실패...", error);
            });
    };

	function checkSignupForm() {
        let isPassword = /^.{4,16}$/;

        if (isUniqueIdValid == false) {
            alert("올바른 ID를 입력하세요!!");
            submitSignUpForm.id.focus();
            return false;
        }
        if (isUniqueNickNameValid == false) {
            alert("올바른 별명을 입력하세요!!");
            nickName.focus();
            return false;
        }
		if (submitSignUpForm.pw.value == "") {
			alert("비밀번호를 입력하세요!!");
			submitSignUpForm.pw.focus();
			return false;
		}
		if (submitSignUpForm.pw2.value == "") {
			alert("비밀번호 확인도 입력하세요!!");
			submitSignUpForm.pw2.focus();
			return false;
		}
		if (submitSignUpForm.pw.value != submitSignUpForm.pw2.value) {
			alert("비밀번호가 일치해야 합니다.");
			submitSignUpForm.pw.focus();
			return false;
		}
		if(!isPassword.test(submitSignUpForm.pw.value)){
			alert("비밀번호 양식을 확인해주세요.");
			submitSignUpForm.pw.focus();
			return false;
		}
		submitSignUpForm.submit();
	}
</script>

</body>
</html>
