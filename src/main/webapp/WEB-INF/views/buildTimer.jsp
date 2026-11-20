<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<title><c:out value="${pageTitle}"/></title>
<%@include file="./include/head.jspf" %>
<link rel="stylesheet" href="/css/build-timer.css?v=${applicationScope.assetVersion}">
</head>
<body>
<%@include file="./include/header.jspf" %>
    <div class="section-inner">
        <div class="container">
            <main class="sc-bt">
                <div class="sc-bt-intro">
                    <h1>빌드오더 연습 타이머</h1>
                    <p>게임 시작과 함께 타이머를 켜고, 한 단계를 끝낼 때마다 <kbd>Space</kbd>나 <strong>다음 단계</strong>를 누르세요. 다음 할 일을 읽어 주고, 원글에 시간이 적힌 체크포인트는 미리 알려 줍니다.</p>
                </div>

                <section class="sc-bt-panel" aria-labelledby="scBtControlsTitle">
                    <div class="sc-bt-panel__header">
                        <h2 id="scBtControlsTitle">빌드 선택</h2>
                    </div>
                    <div class="sc-bt-panel__body">
                        <div class="sc-bt-pick">
                            <label for="scBtBuild" class="sc-visually-hidden">연습할 빌드</label>
                            <select id="scBtBuild">
                                <optgroup label="테란">
                                    <c:forEach var="build" items="${builds}"><c:if test="${build.race eq 'terran'}">
                                        <option value="<c:out value='${build.id}'/>"><c:out value="${build.matchup}"/> · <c:out value="${build.name}"/></option>
                                    </c:if></c:forEach>
                                </optgroup>
                                <optgroup label="저그">
                                    <c:forEach var="build" items="${builds}"><c:if test="${build.race eq 'zerg'}">
                                        <option value="<c:out value='${build.id}'/>"><c:out value="${build.matchup}"/> · <c:out value="${build.name}"/></option>
                                    </c:if></c:forEach>
                                </optgroup>
                                <optgroup label="프로토스">
                                    <c:forEach var="build" items="${builds}"><c:if test="${build.race eq 'protoss'}">
                                        <option value="<c:out value='${build.id}'/>"><c:out value="${build.matchup}"/> · <c:out value="${build.name}"/></option>
                                    </c:if></c:forEach>
                                </optgroup>
                            </select>
                            <a id="scBtSource" class="sc-bt-source" href="<c:out value='${builds[0].sourceUrl}'/>">원문 보기</a>
                        </div>
                        <p id="scBtSummary" class="sc-bt-summary"><c:out value="${builds[0].summary}"/></p>
                    </div>
                </section>

                <section class="sc-bt-panel sc-bt-stage" aria-label="타이머">
                    <div class="sc-bt-stage__clock">
                        <span id="scBtClock" class="sc-bt-clock" role="timer" aria-live="off">0:00</span>
                        <div class="sc-bt-adjust" role="group" aria-label="시계 보정">
                            <button type="button" id="scBtMinus" title="1초 빼기 (←)">−1초</button>
                            <button type="button" id="scBtPlus" title="1초 더하기 (→)">+1초</button>
                        </div>
                    </div>
                    <div class="sc-bt-stage__now">
                        <p class="sc-bt-label">지금 할 것</p>
                        <p id="scBtNow" class="sc-bt-now" aria-live="polite">시작을 누르세요</p>
                        <p class="sc-bt-label">그다음</p>
                        <p id="scBtNext" class="sc-bt-next">-</p>
                        <p id="scBtAlert" class="sc-bt-alert" aria-live="assertive" hidden></p>
                    </div>
                    <div class="sc-bt-actions">
                        <button type="button" id="scBtStart" class="btn-primary">시작</button>
                        <button type="button" id="scBtStep" class="sc-bt-step" disabled>다음 단계</button>
                        <button type="button" id="scBtUndo" disabled>이전</button>
                        <button type="button" id="scBtReset">처음부터</button>
                    </div>
                    <div class="sc-bt-options">
                        <label><input type="checkbox" id="scBtVoice" checked> 음성 안내</label>
                        <label>체크포인트 미리 알림
                            <select id="scBtLead">
                                <option value="0">제때</option>
                                <option value="3">3초 전</option>
                                <option value="5" selected>5초 전</option>
                                <option value="10">10초 전</option>
                            </select>
                        </label>
                    </div>
                    <p class="sc-bt-keys"><kbd>Space</kbd> 시작·다음 단계 <kbd>Backspace</kbd> 이전 <kbd>←</kbd><kbd>→</kbd> 1초 보정 <kbd>R</kbd> 처음부터</p>
                </section>

                <div class="sc-bt-columns">
                    <section class="sc-bt-panel" aria-labelledby="scBtStepsTitle">
                        <div class="sc-bt-panel__header">
                            <h2 id="scBtStepsTitle">빌드 순서</h2>
                            <span class="sc-bt-hint">내 기록 / 지난번</span>
                        </div>
                        <ol id="scBtSteps" class="sc-bt-steps"></ol>
                    </section>
                    <section class="sc-bt-panel" aria-labelledby="scBtCheckpointsTitle">
                        <div class="sc-bt-panel__header">
                            <h2 id="scBtCheckpointsTitle">체크포인트</h2>
                        </div>
                        <ul id="scBtCheckpoints" class="sc-bt-checkpoints"></ul>
                        <p id="scBtNote" class="sc-bt-note" hidden></p>
                    </section>
                </div>

                <section class="sc-bt-panel sc-bt-help" aria-labelledby="scBtHelpTitle">
                    <div class="sc-bt-panel__header">
                        <h2 id="scBtHelpTitle">실제 게임과 맞추는 법</h2>
                    </div>
                    <div class="sc-bt-panel__body">
                        <ul>
                            <li>타이머는 실제 시간으로 흐릅니다. 게임 화면의 시계와 어긋나면 <kbd>←</kbd> <kbd>→</kbd>로 1초씩 맞추세요.</li>
                            <li>빌드는 시간이 아니라 인구수로 따라가는 게 기본입니다. 단계는 직접 넘기고, 시계는 체크포인트 확인용으로 보세요.</li>
                            <li>체크포인트 시간은 원문 기준이며 맵, 스폰 위치, 정찰 결과에 따라 앞뒤로 달라집니다. 원문에 시간이 없는 빌드는 체크포인트가 없습니다.</li>
                            <li>단계마다 누른 시간이 기록되고, 같은 빌드를 다시 하면 지난번 기록과 나란히 보여 줍니다. 기록은 이 브라우저에만 남습니다.</li>
                        </ul>
                    </div>
                </section>

                <section class="sc-bt-panel sc-bt-catalog" aria-labelledby="scBtCatalogTitle">
                    <div class="sc-bt-panel__header">
                        <h2 id="scBtCatalogTitle">수록 빌드 전체 보기</h2>
                    </div>
                    <div class="sc-bt-panel__body">
                        <c:forEach var="build" items="${builds}">
                            <details>
                                <summary><c:out value="${build.matchup}"/> · <c:out value="${build.name}"/></summary>
                                <p><c:out value="${build.summary}"/></p>
                                <p class="sc-bt-catalog__order">
                                    <c:forEach var="step" items="${build.steps}" varStatus="status"><c:if test="${not status.first}"> - </c:if><c:if test="${not empty step.supply}"><c:out value="${step.supply}"/> </c:if><c:out value="${step.action}"/></c:forEach>
                                </p>
                                <div class="sc-bt-catalog__links">
                                    <button type="button" class="sc-bt-catalog__pick" data-bt-select="<c:out value='${build.id}'/>">이 빌드 연습</button>
                                    <a href="<c:out value='${build.sourceUrl}'/>"><c:out value="${build.sourceTitle}"/></a>
                                </div>
                            </details>
                        </c:forEach>
                    </div>
                </section>
            </main>
        </div>
    </div>
<script type="application/json" id="scBtData"><c:out value="${buildsJson}" escapeXml="false"/></script>
<script src="/js/build-timer.js?v=${applicationScope.assetVersion}" defer></script>
<%@include file="./include/footer.jspf" %>
</body>
</html>
