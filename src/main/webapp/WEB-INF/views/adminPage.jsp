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
.admin-page {
    display: flex;
    flex-direction: column;
    gap: 20px;
}
.admin-card {
    border: 1px solid rgba(255, 255, 255, 0.35);
    background: rgba(0, 0, 0, 0.45);
    padding: 18px;
}
.admin-card-header {
    display: flex;
    flex-wrap: wrap;
    justify-content: space-between;
    align-items: center;
    gap: 10px;
}
.admin-card-title {
    margin: 0;
    font-size: 28px;
}
.admin-card-subtitle {
    margin: 2px 0 0;
    color: rgba(255, 255, 255, 0.7);
    font-size: 18px;
}
.admin-search-form {
    margin-top: 12px;
}
.admin-search-label {
    display: block;
    margin-bottom: 6px;
}
.admin-search-row {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
}
.admin-input {
    flex: 1;
    min-width: 200px;
    height: 44px;
    border: 1px solid rgba(255, 255, 255, 0.45);
    padding-left: 12px;
}
.admin-btn {
    height: 44px;
    padding: 0 14px;
    border: 1px solid rgba(255, 255, 255, 0.7);
    background: rgba(0, 0, 0, 0.2);
}
.admin-btn--ghost {
    border-color: rgba(255, 255, 255, 0.4);
}
.admin-btn--danger {
    border-color: #ff6b6b;
    color: #ff6b6b;
}
.admin-table-wrap {
    margin-top: 14px;
    border: 1px solid rgba(255, 255, 255, 0.25);
    overflow-x: auto;
}
.admin-memberlist {
    width: 100%;
    min-width: 720px;
    border-collapse: collapse;
}
.admin-memberlist th,
.admin-memberlist td {
    padding: 10px 12px;
    text-align: left;
    border-bottom: 1px solid rgba(255, 255, 255, 0.2);
}
.admin-memberlist thead {
    background: rgba(0, 0, 0, 0.35);
}
.admin-memberlist tbody tr:hover {
    background: rgba(255, 255, 255, 0.04);
}
.admin-actions {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
}
.admin-empty {
    text-align: center;
    padding: 18px 10px;
    color: rgba(255, 255, 255, 0.8);
}
.admin-pagination {
    margin-top: 12px;
}
.page-list {
    display: flex;
    justify-content: center;
    gap: 6px;
    flex-wrap: wrap;
    list-style: none;
    padding: 0;
}
.page-list li {
    margin: 0;
}
.page-link {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 38px;
    height: 36px;
    border: 1px solid rgba(255, 255, 255, 0.35);
    padding: 0 10px;
}
.page-link.active {
    background: rgba(255, 255, 255, 0.12);
}
.visitor-grid {
    margin-top: 12px;
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
    gap: 10px;
}
.visitor-item {
    border: 1px solid rgba(255, 255, 255, 0.25);
    padding: 10px;
    text-align: center;
    background: rgba(0, 0, 0, 0.35);
}
.visitor-date {
    color: rgba(255, 255, 255, 0.7);
    margin-bottom: 6px;
}
.visitor-count {
    font-size: 22px;
}
@media (max-width: 768px) {
    .admin-card {
        padding: 14px;
    }
    .admin-card-title {
        font-size: 24px;
    }
    .admin-card-subtitle {
        font-size: 16px;
    }
    .admin-memberlist {
        min-width: 0;
    }
    .admin-table-wrap {
        border: none;
        overflow: visible;
    }
    .admin-memberlist thead {
        display: none;
    }
    .admin-memberlist tbody,
    .admin-memberlist tr,
    .admin-memberlist td {
        display: block;
        width: 100%;
    }
    .admin-memberlist tr {
        border: 1px solid rgba(255, 255, 255, 0.25);
        margin-bottom: 12px;
        padding: 10px;
        background: rgba(0, 0, 0, 0.4);
    }
    .admin-memberlist td {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 8px 0;
        border-bottom: 1px dashed rgba(255, 255, 255, 0.15);
    }
    .admin-memberlist td:last-child {
        border-bottom: none;
    }
    .admin-memberlist td::before {
        content: attr(data-label);
        color: rgba(255, 255, 255, 0.7);
        margin-right: 10px;
    }
    .admin-memberlist td.admin-empty::before {
        content: none;
    }
    .admin-actions {
        justify-content: flex-end;
        width: 100%;
    }
    .page-link {
        min-width: 34px;
        height: 34px;
    }
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
                <div class="admin-page">
                    <div class="admin-card admin-card--members">
                        <div class="admin-card-header">
                            <div>
                                <h2 class="admin-card-title">회원 관리</h2>
                                <p class="admin-card-subtitle">회원목록 (최근 가입자 순)</p>
                            </div>
                            <button type="button" class="admin-btn admin-btn--ghost" onclick="location.href='/myPage'">뒤로가기</button>
                        </div>
                        <form action="/adminPage" method="get" class="admin-search-form">
                            <label class="admin-search-label" for="adminKeyword">회원 검색</label>
                            <div class="admin-search-row">
                                <input class="admin-input" id="adminKeyword" type="text" name="keyword" value="${pageInfo.keyword}" placeholder="ID, 별명, 이름">
                                <button type="submit" class="admin-btn">검색</button>
                                <c:if test="${pageInfo.keyword != ''}">
                                    <button type="button" class="admin-btn admin-btn--ghost" onclick="location.href='/adminPage'" accesskey="c">검색취소(C)</button>
                                </c:if>
                            </div>
                        </form>
                        <div class="admin-table-wrap">
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
                                            <td data-label="ID">${member.id}</td>
                                            <td data-label="별명">${member.nickName}</td>
                                            <td data-label="이메일">${member.email}</td>
                                            <td data-label="등급">${member.grade}</td>
                                            <td data-label="가입일"><fmt:formatDate value="${member.regDate}" pattern="yy.MM.dd"/></td>
                                            <td data-label="관리">
                                                <div class="admin-actions">
                                                    <button type="button" class="admin-btn admin-btn--ghost" onclick="location.href='/modifyMemberByAdmin?id=${member.id}'">수정</button>
                                                    <button type="button" class="admin-btn admin-btn--danger" onclick="confirmDelete('${member.id}')">탈퇴</button>
                                                </div>
                                            </td>
                                        </tr>
                                    </c:forEach>
                                    <c:if test="${empty memberList}">
                                        <tr>
                                            <td class="admin-empty" colspan="6">조회 결과가 없습니다.</td>
                                        </tr>
                                    </c:if>
                                </tbody>
                            </table>
                        </div>
                        <nav class="admin-pagination">
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
                                            <li><a class="page-link active" href="/adminPage?recentPage=${i}&searchType=${pageInfo.searchType}&keyword=${pageInfo.keyword}">${i}</a></li>
                                        </c:when>
                                        <c:otherwise>
                                            <li><a class="page-link" href="/adminPage?recentPage=${i}&searchType=${pageInfo.searchType}&keyword=${pageInfo.keyword}">${i}</a></li>
                                        </c:otherwise>
                                    </c:choose>
                                </c:forEach>
                                <c:if test="${pageInfo.nextPageSetPoint <= pageInfo.totalPage}">
                                    <li class="page-item">
                                        <a class="page-link" href="/adminPage?recentPage=${pageInfo.nextPageSetPoint}&searchType=${pageInfo.searchType}&keyword=${pageInfo.keyword}" aria-label="Next">
                                            <span aria-hidden="true">&raquo;</span>
                                        </a>
                                    </li>
                                </c:if>
                            </ul>
                        </nav>
                    </div>
                    <div class="admin-card admin-card--visitors">
                        <div class="admin-card-header">
                            <div>
                                <h3 class="admin-card-title">일별 최근 방문자수</h3>
                                <p class="admin-card-subtitle">최근 10일 기준</p>
                            </div>
                        </div>
                        <c:choose>
                            <c:when test="${empty recentVisitors}">
                                <div class="admin-empty">집계된 방문자 데이터가 없습니다.</div>
                            </c:when>
                            <c:otherwise>
                                <div class="visitor-grid">
                                    <c:forEach var="visitors" items="${recentVisitors}">
                                        <div class="visitor-item">
                                            <div class="visitor-date"><fmt:formatDate pattern="MM.dd" value="${visitors.date}"/></div>
                                            <div class="visitor-count">${visitors.dailyCount}명</div>
                                        </div>
                                    </c:forEach>
                                </div>
                            </c:otherwise>
                        </c:choose>
                    </div>
                </div>
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
