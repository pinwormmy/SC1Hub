<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <c:set var="pageTitle" value="SC1Hub - AI 한줄 공략 검토" />
    <c:set var="robots" value="noindex,nofollow" />
    <title><c:out value="${pageTitle}" /></title>
    <%@include file="./include/head.jspf" %>
    <link rel="stylesheet" href="/css/adminStrategyTipAi.css?v=${applicationScope.assetVersion}">
</head>
<body>
<%@include file="./include/header.jspf" %>

<main class="section-inner ai-review-page" id="main-content">
    <div class="container">
        <div class="row">
            <%@include file="./include/latestPosts.jspf" %>
            <div class="col-sm-12">
                <div class="ai-review-workspace">
                    <c:if test="${not empty msg}">
                        <div class="ai-review-flash" role="status" aria-live="polite">
                            <c:out value="${msg}" />
                        </div>
                    </c:if>

                    <header class="ai-review-header">
                        <div class="ai-review-header__copy">
                            <span class="ai-review-eyebrow">ADMIN</span>
                            <h1 class="ai-review-title">AI 한줄 공략</h1>
                            <p class="ai-review-intro">초안을 확인하고 승인하거나 반려합니다.</p>
                        </div>
                        <div class="ai-review-header__actions">
                            <a class="ai-review-button ai-review-button--ghost" href="/adminPage">관리자 홈</a>
                            <form class="ai-review-generate" action="/adminPage/strategy-tips/ai/generate"
                                  method="post"
                                  onsubmit="if (!window.confirm('Gemini API 요금이 청구될 수 있습니다. 초안 3건을 생성할까요?')) return false; this.setAttribute('aria-busy', 'true'); this.querySelector('button[type=submit]').disabled = true; this.querySelector('button[type=submit]').textContent = '생성 중…'; return true;">
                                <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}' />">
                                <button class="ai-review-button ai-review-button--primary" type="submit"
                                        <c:if test="${not aiStatus.enabled}">disabled aria-disabled="true"</c:if>>
                                    초안 3건 생성
                                </button>
                                <span class="ai-review-generate__help">
                                    <c:choose>
                                        <c:when test="${not aiStatus.enabled}">AI 생성 비활성화</c:when>
                                        <c:otherwise>호출 시 API 요금이 청구될 수 있습니다.</c:otherwise>
                                    </c:choose>
                                </span>
                            </form>
                        </div>
                    </header>

                    <div class="ai-review-summary" aria-label="AI 초안 현황">
                        <span><strong><c:out value="${empty aiStatus.pendingCount ? 0 : aiStatus.pendingCount}" /></strong>건 대기</span>
                        <span><c:out value="${empty aiStatus.model ? '모델 미설정' : aiStatus.model}" /></span>
                    </div>

                    <c:if test="${not empty aiStatus.lastError}">
                        <p class="ai-review-last-error">최근 실패: <c:out value="${aiStatus.lastError}" /></p>
                    </c:if>

                    <section class="ai-review-section" aria-labelledby="pending-drafts-title">
                        <div class="ai-review-section-heading">
                            <h2 id="pending-drafts-title">검토 대기</h2>
                            <span class="ai-review-count"><c:out value="${empty aiStatus.pendingCount ? 0 : aiStatus.pendingCount}" /></span>
                        </div>

                        <div class="ai-review-draft-list">
                            <c:forEach var="draft" items="${pendingDrafts}" varStatus="draftLoop">
                                <article class="ai-review-draft" aria-labelledby="draft-title-${draftLoop.index}">
                                    <header class="ai-review-draft__header">
                                        <div>
                                            <h3 id="draft-title-${draftLoop.index}">
                                                <c:choose>
                                                    <c:when test="${not empty draft.categoryName}"><c:out value="${draft.categoryName}" /></c:when>
                                                    <c:otherwise>분류 미지정</c:otherwise>
                                                </c:choose>
                                            </h3>
                                            <p class="ai-review-draft__meta">
                                                <c:out value="${draft.generationDate}" /> · #<c:out value="${draft.draftId}" />
                                            </p>
                                        </div>
                                        <span class="ai-review-badge ai-review-badge--pending">대기</span>
                                    </header>

                                    <div class="ai-review-decision">
                                        <form class="ai-review-approve-form"
                                              action="/adminPage/strategy-tips/ai/approve"
                                              method="post"
                                              onsubmit="return window.confirm('이 문장으로 승인할까요? 승인 즉시 공개됩니다.');">
                                            <input type="hidden" name="draftId" value="<c:out value='${draft.draftId}' />">
                                            <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}' />">
                                            <input type="hidden" name="category" value="<c:out value='${draft.category}' />">
                                            <label class="sc-visually-hidden" for="draft-content-${draftLoop.index}">공개 문장</label>
                                            <textarea id="draft-content-${draftLoop.index}" name="content" rows="3"
                                                      maxlength="160" required data-ai-content
                                                      data-counter-id="draft-count-${draftLoop.index}"
                                                      aria-describedby="draft-count-${draftLoop.index}"><c:out value="${draft.content}" /></textarea>
                                            <div class="ai-review-actions">
                                                <span id="draft-count-${draftLoop.index}" class="ai-review-character-count" aria-live="polite">0 / 160</span>
                                                <button class="ai-review-button ai-review-button--approve" type="submit">승인</button>
                                            </div>
                                        </form>

                                        <form class="ai-review-reject-form"
                                              action="/adminPage/strategy-tips/ai/reject"
                                              method="post"
                                              onsubmit="return window.confirm('이 초안을 반려할까요?');">
                                            <input type="hidden" name="draftId" value="<c:out value='${draft.draftId}' />">
                                            <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}' />">
                                            <button class="ai-review-button ai-review-button--reject" type="submit">반려</button>
                                        </form>
                                    </div>
                                </article>
                            </c:forEach>

                            <c:if test="${empty pendingDrafts}">
                                <div class="ai-review-empty">
                                    <h3>검토할 초안이 없습니다.</h3>
                                </div>
                            </c:if>
                        </div>
                    </section>

                    <section class="ai-review-section ai-review-history-section" aria-labelledby="recent-drafts-title">
                        <div class="ai-review-section-heading">
                            <h2 id="recent-drafts-title">최근 처리</h2>
                        </div>
                        <ul class="ai-review-history-list">
                            <c:forEach var="draft" items="${recentDrafts}">
                                <li>
                                    <div>
                                        <span class="ai-review-history__category"><c:out value="${draft.categoryName}" /></span>
                                        <p class="ai-review-history__content"><c:out value="${draft.content}" /></p>
                                    </div>
                                    <span class="ai-review-history__status"><c:out value="${draft.status}" /></span>
                                </li>
                            </c:forEach>
                            <c:if test="${empty recentDrafts}">
                                <li class="ai-review-history__empty">처리 이력이 없습니다.</li>
                            </c:if>
                        </ul>
                    </section>
                </div>
            </div>
        </div>
    </div>
</main>

<%@include file="/WEB-INF/views/include/footer.jspf" %>

<script>
(function () {
    'use strict';

    var contentFields = document.querySelectorAll('[data-ai-content]');
    Array.prototype.forEach.call(contentFields, function (field) {
        var counter = document.getElementById(field.getAttribute('data-counter-id'));
        if (!counter) {
            return;
        }
        var updateCount = function () {
            var maximum = field.maxLength > 0 ? field.maxLength : 160;
            counter.textContent = field.value.length + ' / ' + maximum;
            counter.classList.toggle('is-near-limit', field.value.length >= maximum - 20);
        };
        field.addEventListener('input', updateCount);
        updateCount();
    });
})();
</script>
</body>
</html>
