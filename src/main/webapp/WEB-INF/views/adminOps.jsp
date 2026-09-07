<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<title>SC1Hub - 운영 점검</title>
<style>
.ops-page {
    display: flex;
    flex-direction: column;
    gap: 18px;
}
.ops-card {
    border: 1px solid rgba(255, 255, 255, 0.35);
    background: rgba(0, 0, 0, 0.45);
    padding: 18px;
}
.ops-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
}
.ops-title {
    margin: 0;
    font-size: 28px;
}
.ops-subtitle {
    margin: 4px 0 0;
    color: rgba(255, 255, 255, 0.72);
}
.ops-btn {
    height: 42px;
    padding: 0 14px;
    border: 1px solid rgba(255, 255, 255, 0.65);
    background: rgba(0, 0, 0, 0.2);
}
.ops-grid {
    margin-top: 14px;
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
    gap: 10px;
}
.ops-stat {
    border: 1px solid rgba(255, 255, 255, 0.22);
    background: rgba(0, 0, 0, 0.3);
    padding: 10px;
}
.ops-label {
    color: rgba(255, 255, 255, 0.68);
    font-size: 14px;
}
.ops-value {
    margin-top: 4px;
    font-size: 18px;
    word-break: break-word;
}
.ops-table-wrap {
    margin-top: 14px;
    overflow-x: auto;
    border: 1px solid rgba(255, 255, 255, 0.25);
}
.ops-table {
    width: 100%;
    min-width: 840px;
    border-collapse: collapse;
}
.ops-table th,
.ops-table td {
    padding: 10px 12px;
    text-align: left;
    border-bottom: 1px solid rgba(255, 255, 255, 0.2);
    vertical-align: top;
}
.ops-table thead {
    background: rgba(0, 0, 0, 0.35);
}
.ops-empty {
    padding: 16px;
    text-align: center;
    color: rgba(255, 255, 255, 0.75);
}
.ops-note {
    margin-top: 10px;
    color: rgba(255, 255, 255, 0.72);
    font-size: 14px;
}
@media (max-width: 768px) {
    .ops-card {
        padding: 14px;
    }
    .ops-title {
        font-size: 24px;
    }
    .ops-table {
        min-width: 720px;
    }
}
</style>
<%@ include file="./include/head.jspf" %>
</head>
<body>
<%@ include file="./include/header.jspf" %>
<div class="section-inner">
    <div class="container">
        <div class="row">
            <%@include file="./include/latestPosts.jspf" %>
            <div class="col-sm-12">
                <div class="ops-page">
                    <div class="ops-card">
                        <div class="ops-header">
                            <div>
                                <h2 class="ops-title">운영 점검</h2>
                                <p class="ops-subtitle">자동 발행 설정, 슬롯, 최근 생성 이력</p>
                            </div>
                            <button type="button" class="ops-btn" onclick="location.href='/adminPage'">뒤로가기</button>
                        </div>
                        <div class="ops-grid">
                            <div class="ops-stat">
                                <div class="ops-label">enabled</div>
                                <div class="ops-value"><c:out value="${autoPublishStatus.enabled}"/></div>
                            </div>
                            <div class="ops-stat">
                                <div class="ops-label">autoPublishEnabled</div>
                                <div class="ops-value"><c:out value="${autoPublishStatus.autoPublishEnabled}"/></div>
                            </div>
                            <div class="ops-stat">
                                <div class="ops-label">catchUpEnabled</div>
                                <div class="ops-value"><c:out value="${autoPublishStatus.autoPublishCatchUpEnabled}"/></div>
                            </div>
                            <div class="ops-stat">
                                <div class="ops-label">serverNow</div>
                                <div class="ops-value"><c:out value="${autoPublishStatus.serverNow}"/></div>
                            </div>
                            <div class="ops-stat">
                                <div class="ops-label">zone</div>
                                <div class="ops-value"><c:out value="${autoPublishStatus.autoPublishZone}"/></div>
                            </div>
                            <div class="ops-stat">
                                <div class="ops-label">dailyLimit</div>
                                <div class="ops-value">post <c:out value="${autoPublishStatus.postDailyLimit}"/> / comment <c:out value="${autoPublishStatus.commentDailyLimit}"/> / chat <c:out value="${autoPublishStatus.chatDailyLimit}"/></div>
                            </div>
                        </div>
                    </div>

                    <div class="ops-card">
                        <h3 class="ops-title">페르소나별 상태</h3>
                        <div class="ops-table-wrap">
                            <table class="ops-table">
                                <thead>
                                    <tr>
                                        <th>persona</th>
                                        <th>board</th>
                                        <th>model</th>
                                        <th>handled</th>
                                        <th>next</th>
                                        <th>due</th>
                                        <th>waiting</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <c:forEach var="persona" items="${autoPublishStatus.personas}">
                                        <tr>
                                            <td><c:out value="${persona.personaName}"/><br>(<c:out value="${persona.publishChannel}"/>)</td>
                                            <td><c:out value="${persona.boardTitle}"/></td>
                                            <td><c:out value="${persona.model}"/></td>
                                            <td>post <c:out value="${persona.handledPostToday}"/>, comment <c:out value="${persona.handledCommentToday}"/>, chat <c:out value="${persona.handledChatToday}"/></td>
                                            <td>post <c:out value="${persona.nextPostSlot}"/><br>comment <c:out value="${persona.nextCommentSlot}"/><br>chat <c:out value="${persona.nextChatSlot}"/></td>
                                            <td><c:out value="${persona.dueModes}"/></td>
                                            <td><c:out value="${persona.waitingDetail}"/></td>
                                        </tr>
                                    </c:forEach>
                                    <c:if test="${empty autoPublishStatus.personas}">
                                        <tr><td class="ops-empty" colspan="7">상태 데이터가 없습니다.</td></tr>
                                    </c:if>
                                </tbody>
                            </table>
                        </div>
                    </div>

                    <div class="ops-card">
                        <div class="ops-header">
                            <div>
                                <h3 class="ops-title">최근 <c:out value="${days}"/>일 요약</h3>
                                <p class="ops-subtitle">persona, board, mode, status 기준</p>
                            </div>
                        </div>
                        <div class="ops-table-wrap">
                            <table class="ops-table">
                                <thead>
                                    <tr>
                                        <th>persona</th>
                                        <th>board</th>
                                        <th>mode</th>
                                        <th>status</th>
                                        <th>count</th>
                                        <th>latest</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <c:forEach var="item" items="${historySummary}">
                                        <tr>
                                            <td><c:out value="${item.personaName}"/></td>
                                            <td><c:out value="${item.boardTitle}"/></td>
                                            <td><c:out value="${item.generationMode}"/></td>
                                            <td><c:out value="${item.status}"/></td>
                                            <td><c:out value="${item.count}"/></td>
                                            <td><c:out value="${item.latestCreatedAt}"/></td>
                                        </tr>
                                    </c:forEach>
                                    <c:if test="${empty historySummary}">
                                        <tr><td class="ops-empty" colspan="6">최근 요약 데이터가 없습니다.</td></tr>
                                    </c:if>
                                </tbody>
                            </table>
                        </div>
                    </div>

                    <div class="ops-card">
                        <h3 class="ops-title">최근 이력 <c:out value="${limit}"/>건</h3>
                        <div class="ops-table-wrap">
                            <table class="ops-table">
                                <thead>
                                    <tr>
                                        <th>createdAt</th>
                                        <th>persona</th>
                                        <th>board</th>
                                        <th>mode</th>
                                        <th>status</th>
                                        <th>published</th>
                                        <th>title</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <c:forEach var="item" items="${history}">
                                        <tr>
                                            <td><c:out value="${item.createdAt}"/></td>
                                            <td><c:out value="${item.personaName}"/></td>
                                            <td><c:out value="${item.boardTitle}"/></td>
                                            <td><c:out value="${item.generationMode}"/></td>
                                            <td><c:out value="${item.status}"/></td>
                                            <td><c:out value="${item.publishedPostNum}"/></td>
                                            <td><c:out value="${item.draftTitle}"/></td>
                                        </tr>
                                    </c:forEach>
                                    <c:if test="${empty history}">
                                        <tr><td class="ops-empty" colspan="7">최근 이력이 없습니다.</td></tr>
                                    </c:if>
                                </tbody>
                            </table>
                        </div>
                        <div class="ops-note">조회 범위는 URL의 days, limit 파라미터로 조정할 수 있습니다.</div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>
<%@ include file="/WEB-INF/views/include/footer.jspf" %>
</body>
</html>
