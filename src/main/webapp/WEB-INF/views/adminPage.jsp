<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
<title>SC1Hub - 관리자 페이지</title>
<style>
.leftbar-ul li a {
    color: white;
    font-size: 20px;
    font-weight: 400;
}
.page-navigation {
    text-align: center;
}
.admin-memberlist td {
    overflow: auto;
    padding: 3px;
}
.page-list {
    display: flex;
    list-style: none;
    padding: 0;
}
.daily-count {
    overflow: auto;
}
.daily-count{
    border: 2px solid white;
    border-collapse : collapse;
}
.daily-count th {
    border-bottom: 1px solid white;
}
</style>

<%@ include file="./include/header.jspf" %>
</head>
<body>
<div class="section-inner">
    <div class="container">
        <div class="row">
            <%@include file="./include/latestPosts.jspf" %>
            <div class="col-sm-12">
                <form action="/adminPage" method="get">
                    <div class="div-search">
                        회원검색>
                        <input type="text" name="keyword" value="${pageInfo.keyword}">
                        <button type="submit" style="visibility:hidden">검색</button>
                        <c:if test="${pageInfo.keyword != ''}">
                            <button type="button" class="pull btn btn-theme cancel-btn" onclick="location.href='/adminPage'" accesskey="c">검색취소(C)</button>
                        </c:if>
                        <hr>
                    </div>
                </form>
                <table class="admin-memberlist">
                    <thead>
                        <tr>
                            <th width="15%">ID</th>
                            <th width="15%">별명</th>
                            <th width="30%">이메일</th>
                            <th width="10%">등급</th>
                            <th width="10%">가입일</th>
                            <th width="20%">관리</th>
                        </tr>
                    </thead>
                    <tbody>
                        <c:forEach items="${memberList}" var="member">
                            <tr>
                                <td>${member.id}</td>
                                <td>${member.nickName}</td>
                                <td>${member.email}</td>
                                <td align="center">${member.grade}</td>
                                <td><fmt:formatDate value="${member.regDate}" pattern="yy.MM.dd"/></td>
                                <td>
                                    <button style="margin: 0px;" onclick="location.href='/modifyMemberByAdmin?id=${member.id}'">수정</button>
                                    <button style="margin: 0;" onclick="confirmDelete('${member.id}')">탈퇴</button>
                                </td>
                            </tr>
                        </c:forEach>
                        <c:if test="${empty memberList}"><td colspan="8">조회 결과가 없습니다..</td></c:if>
                    </tbody>
                </table>
                <nav>
                    <ul class="page-list">
                        <c:if test="${pageInfo.prevPageSetPoint != 0}">
                            <li class="page-item">
                                <a class="page-link" href="/adminPage?recentPage=${pageInfo.prevPageSetPoint}&searchType=${pageInfo.searchType}&keyword=${pageInfo.keyword}" aria-label="Previous">
                                    <span aria-hidden="true">&laquo;</span>
                                </a>
                            </li>
                        </c:if>
                        <c:forEach var="i" begin="${pageInfo.pageBeginPoint}" end="${pageInfo.pageEndPoint}">
                            <c:choose>
                                <c:when test="${i == pageInfo.recentPage}">
                                    <li><a class="page-link" href="/adminPage?recentPage=${i}&searchType=${pageInfo.searchType}&keyword=${pageInfo.keyword}">${i}</a></li>
                                </c:when>
                                <c:otherwise>
                                    <li><a class="page-link" href="/adminPage?recentPage=${i}&searchType=${pageInfo.searchType}&keyword=${pageInfo.keyword}">${i}</a></li>
                                </c:otherwise>
                            </c:choose>
                        </c:forEach>
                        <c:if test="${pageInfo.nextPageSetPoint <= pageInfo.totalPage}">
                            <li class="page-item">
                                <a href="/adminPage?recentPage=${pageInfo.nextPageSetPoint}&searchType=${pageInfo.searchType}&keyword=${pageInfo.keyword}" aria-label="Next">
                                    <span aria-hidden="true">&raquo;</span>
                                </a>
                            </li>
                        </c:if>
                    </ul>
                </nav>
                <hr>
                <div>
                    <p>최근 방문자수</p>
                    <table class="daily-count">
                        <tr>
                            <c:forEach var="visitors" items="${recentVisitors}">
                                <th><fmt:formatDate pattern="MM.dd" value="${visitors.date}"/></th>
                            </c:forEach>
                        </tr>
                        <tr>
                            <c:forEach var="visitors" items="${recentVisitors}">
                                <th>${visitors.dailyCount}</th>
                            </c:forEach>
                        </tr>
                    </table>
                </div>
                <button style="margin: 10px;" onclick="location.href='/myPage'">뒤로가기</button>
            </div>
        </div>
    </div>
</div>
<%@ include file="/WEB-INF/views/include/footer.jspf" %>

<script>
function confirmDelete(id) {
    if(confirm("정말로 탈퇴시키겠습니까?")) {
        // AJAX 요청
        $.ajax({
            url: '/deleteMember',
            type: 'POST',
            data: { id: id },
            success: function(data) {
                // 응답 처리
                if(data.success) {
                    alert("탈퇴가 완료되었습니다.");
                    location.href = '/adminPage';
                } else {
                    alert("탈퇴 처리 중 오류가 발생했습니다.");
                }
            }
        });
    }
}
</script>

</body>
</html>
