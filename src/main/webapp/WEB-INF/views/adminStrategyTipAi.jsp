<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
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
                            <span class="ai-review-flash__mark" aria-hidden="true">&gt;</span>
                            <c:out value="${msg}" />
                        </div>
                    </c:if>

                    <c:set var="dailyLimitReached"
                           value="${aiStatus.generatedToday ge aiStatus.dailyDraftLimit}" />
                    <c:set var="pendingLimitReached"
                           value="${aiStatus.pendingCount ge aiStatus.maxPendingDrafts}" />
                    <c:set var="apiCallLimitReached"
                           value="${aiStatus.apiCallCount ge aiStatus.maxDailyApiCalls}" />
                    <c:set var="generationBlocked"
                           value="${not aiStatus.enabled or dailyLimitReached or pendingLimitReached or apiCallLimitReached}" />

                    <header class="ai-review-header">
                        <div class="ai-review-header__copy">
                            <span class="ai-review-eyebrow">ADMIN / AI EDITORIAL QUEUE</span>
                            <h1 class="ai-review-title">AI 한줄 공략 검토</h1>
                            <p class="ai-review-intro">
                                사이트 내부 공략 글만 근거로 AI가 초안을 만들고, 운영자가 확인한 최종 문장만 공개합니다.
                            </p>
                        </div>
                        <div class="ai-review-header__actions">
                            <a class="ai-review-button ai-review-button--ghost" href="/adminPage">관리자 홈</a>
                            <form class="ai-review-generate" action="/adminPage/strategy-tips/ai/generate"
                                  method="post"
                                  onsubmit="if (!window.confirm('오늘 남은 AI 한줄 공략 초안을 수동 생성할까요? 1회의 API 배치 호출이 발생합니다.')) return false; this.setAttribute('aria-busy', 'true'); this.querySelector('button[type=submit]').disabled = true; this.querySelector('button[type=submit]').textContent = '생성 요청 중…'; this.querySelector('.ai-review-generate__help').textContent = '요청을 처리하고 있습니다. 잠시만 기다려 주세요.'; return true;">
                                <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}' />">
                                <button class="ai-review-button ai-review-button--primary" type="submit"
                                        <c:if test="${generationBlocked}">disabled aria-disabled="true"</c:if>>
                                    오늘 남은 AI 초안 수동 생성
                                </button>
                                <span class="ai-review-generate__help">
                                    <c:choose>
                                        <c:when test="${not aiStatus.enabled}">AI 생성 기능이 비활성화되어 있습니다.</c:when>
                                        <c:when test="${apiCallLimitReached}">오늘의 API 호출 상한을 모두 사용했습니다.</c:when>
                                        <c:when test="${pendingLimitReached}">대기 초안이 최대치에 도달했습니다.</c:when>
                                        <c:when test="${dailyLimitReached}">오늘 생성 한도를 모두 사용했습니다.</c:when>
                                        <c:otherwise>수동 1회 호출로 오늘 남은 초안을 최대 3건까지 만듭니다.</c:otherwise>
                                    </c:choose>
                                </span>
                            </form>
                        </div>
                    </header>

                    <section class="ai-review-status" aria-labelledby="ai-status-title">
                        <div class="ai-review-section-heading ai-review-section-heading--compact">
                            <div>
                                <span class="ai-review-kicker">SYSTEM STATUS</span>
                                <h2 id="ai-status-title">오늘의 생성 현황</h2>
                            </div>
                            <span class="ai-review-runtime ${not generationBlocked ? 'is-online' : 'is-offline'}">
                                <span class="ai-review-runtime__dot" aria-hidden="true"></span>
                                <c:choose>
                                    <c:when test="${not generationBlocked}">생성 가능</c:when>
                                    <c:otherwise>생성 중지</c:otherwise>
                                </c:choose>
                            </span>
                        </div>

                        <dl class="ai-review-metrics">
                            <div class="ai-review-metric">
                                <dt>오늘 생성</dt>
                                <dd>
                                    <fmt:formatNumber value="${empty aiStatus.generatedToday ? 0 : aiStatus.generatedToday}" type="number" />
                                    <span>/ <fmt:formatNumber value="${empty aiStatus.dailyDraftLimit ? 3 : aiStatus.dailyDraftLimit}" type="number" /></span>
                                </dd>
                            </div>
                            <div class="ai-review-metric">
                                <dt>검토 대기</dt>
                                <dd>
                                    <fmt:formatNumber value="${empty aiStatus.pendingCount ? 0 : aiStatus.pendingCount}" type="number" />
                                    <span>/ <fmt:formatNumber value="${empty aiStatus.maxPendingDrafts ? 3 : aiStatus.maxPendingDrafts}" type="number" /></span>
                                </dd>
                            </div>
                            <div class="ai-review-metric">
                                <dt>사용 모델</dt>
                                <dd class="ai-review-metric__text">
                                    <c:choose>
                                        <c:when test="${not empty aiStatus.model}"><c:out value="${aiStatus.model}" /></c:when>
                                        <c:otherwise>미설정</c:otherwise>
                                    </c:choose>
                                </dd>
                            </div>
                            <div class="ai-review-metric">
                                <dt>API 호출</dt>
                                <dd>
                                    <fmt:formatNumber value="${empty aiStatus.apiCallCount ? 0 : aiStatus.apiCallCount}" type="number" />
                                    <span>/ <fmt:formatNumber value="${empty aiStatus.maxDailyApiCalls ? 2 : aiStatus.maxDailyApiCalls}" type="number" />회</span>
                                </dd>
                            </div>
                            <div class="ai-review-metric">
                                <dt>입력 토큰</dt>
                                <dd><fmt:formatNumber value="${empty aiStatus.inputTokens ? 0 : aiStatus.inputTokens}" type="number" /></dd>
                            </div>
                            <div class="ai-review-metric">
                                <dt>출력+사고 토큰</dt>
                                <dd><fmt:formatNumber value="${empty aiStatus.outputTokens ? 0 : aiStatus.outputTokens}" type="number" /></dd>
                            </div>
                            <div class="ai-review-metric">
                                <dt>근거 범위</dt>
                                <dd class="ai-review-metric__text">사이트 내부</dd>
                            </div>
                        </dl>

                        <div class="ai-review-last-run">
                            <span>최근 실행</span>
                            <strong>
                                <c:choose>
                                    <c:when test="${not empty aiStatus.lastAttemptAt}"><c:out value="${aiStatus.lastAttemptAt}" /></c:when>
                                    <c:otherwise>실행 기록 없음</c:otherwise>
                                </c:choose>
                            </strong>
                            <span class="ai-review-last-run__status">
                                <c:choose>
                                    <c:when test="${not empty aiStatus.lastStatus}"><c:out value="${aiStatus.lastStatus}" /></c:when>
                                    <c:otherwise>-</c:otherwise>
                                </c:choose>
                            </span>
                        </div>
                        <c:if test="${not empty aiStatus.lastError}">
                            <p class="ai-review-last-error">
                                최근 실패: <c:out value="${aiStatus.lastError}" />
                            </p>
                        </c:if>
                    </section>

                    <aside class="ai-review-policy" aria-label="AI 초안 공개 정책">
                        <span class="ai-review-policy__icon" aria-hidden="true">검</span>
                        <div>
                            <strong>승인 전 초안은 사이트에 공개되지 않습니다.</strong>
                            <p>Google Search를 사용하지 않고 연결된 내부 원문만 근거로 삼으며, 하루 생성량과 대기 초안은 각각 최대 3개입니다.</p>
                        </div>
                    </aside>

                    <section class="ai-review-section" aria-labelledby="pending-drafts-title">
                        <div class="ai-review-section-heading">
                            <div>
                                <span class="ai-review-kicker">REVIEW QUEUE</span>
                                <h2 id="pending-drafts-title">검토 대기 초안</h2>
                                <p>연결된 사이트 내부 원문과 초안 내용을 대조한 뒤 승인하세요.</p>
                            </div>
                            <span class="ai-review-count" aria-label="검토 대기 초안 수">
                                <fmt:formatNumber value="${empty aiStatus.pendingCount ? 0 : aiStatus.pendingCount}" type="number" />개 대기
                            </span>
                        </div>

                        <div class="ai-review-draft-list">
                            <c:forEach var="draft" items="${pendingDrafts}" varStatus="draftLoop">
                                <article class="ai-review-draft" aria-labelledby="draft-title-${draftLoop.index}">
                                    <header class="ai-review-draft__header">
                                        <div>
                                            <span class="ai-review-draft__sequence">
                                                <c:out value="${draft.generationDate}" />
                                                <span aria-hidden="true">·</span>
                                                SLOT <c:out value="${draft.slotNo}" />
                                            </span>
                                            <h3 id="draft-title-${draftLoop.index}">
                                                <c:choose>
                                                    <c:when test="${not empty draft.categoryName}"><c:out value="${draft.categoryName}" /></c:when>
                                                    <c:otherwise>분류 미지정</c:otherwise>
                                                </c:choose>
                                            </h3>
                                        </div>
                                        <div class="ai-review-draft__header-side">
                                            <span class="ai-review-badge ai-review-badge--pending">검토 대기</span>
                                            <span class="ai-review-draft__id">ID <c:out value="${draft.draftId}" /></span>
                                        </div>
                                    </header>

                                    <div class="ai-review-draft__meta" aria-label="초안 생성 정보">
                                        <span>생성 <c:out value="${draft.createdAt}" /></span>
                                        <span>모델 <c:out value="${draft.model}" /></span>
                                        <span>상태 <c:out value="${draft.status}" /></span>
                                    </div>

                                    <div class="ai-review-source-grid">
                                        <section class="ai-review-source" aria-labelledby="source-title-${draftLoop.index}">
                                            <div class="ai-review-source__heading">
                                                <h4 id="source-title-${draftLoop.index}">사이트 내부 근거</h4>
                                                <c:if test="${not empty draft.sourceBoard and draft.sourcePostNum gt 0}">
                                                    <c:url var="sourceUrl" value="/boards/${draft.sourceBoard}/readPost">
                                                        <c:param name="postNum" value="${draft.sourcePostNum}" />
                                                    </c:url>
                                                    <a href="<c:out value='${sourceUrl}' />" target="_blank" rel="noopener noreferrer">
                                                        원문 열기 <span class="sc-visually-hidden">(새 창)</span><span aria-hidden="true">↗</span>
                                                    </a>
                                                </c:if>
                                            </div>
                                            <c:choose>
                                                <c:when test="${not empty draft.sourceTitle}">
                                                    <p class="ai-review-source__title"><c:out value="${draft.sourceTitle}" /></p>
                                                </c:when>
                                                <c:when test="${draft.sourcePostNum le 0}">
                                                    <p class="ai-review-source__warning">내부 근거가 없는 과거 초안입니다. 승인하지 말고 반려하세요.</p>
                                                </c:when>
                                                <c:otherwise>
                                                    <p class="ai-review-source__warning">연결된 출처 제목이 없습니다. 승인 전에 원문을 확인하세요.</p>
                                                </c:otherwise>
                                            </c:choose>
                                            <c:if test="${not empty draft.sourceExcerpt}">
                                                <blockquote><c:out value="${draft.sourceExcerpt}" /></blockquote>
                                            </c:if>
                                            <p class="ai-review-source__reference">
                                                <c:out value="${draft.sourceBoard}" />
                                                <c:if test="${draft.sourcePostNum gt 0}">#<c:out value="${draft.sourcePostNum}" /></c:if>
                                            </p>
                                            <c:if test="${not empty draft.evidenceSummary}">
                                                <p class="ai-review-source__evidence">원문 근거 구절: <c:out value="${draft.evidenceSummary}" /></p>
                                            </c:if>
                                        </section>

                                        <c:if test="${not empty draft.externalSourceUrl}">
                                            <section class="ai-review-evidence" aria-labelledby="evidence-title-${draftLoop.index}">
                                                <div class="ai-review-source__heading">
                                                    <h4 id="evidence-title-${draftLoop.index}">과거 방식의 외부 근거</h4>
                                                    <a href="<c:out value='${draft.externalSourceUrl}' />"
                                                       target="_blank" rel="noopener noreferrer">
                                                        외부 원문 열기 <span class="sc-visually-hidden">(새 창)</span><span aria-hidden="true">↗</span>
                                                    </a>
                                                </div>
                                                <c:if test="${not empty draft.externalSourceTitle and not empty draft.externalEvidenceSummary}">
                                                    <p class="ai-review-source__title"><c:out value="${draft.externalSourceTitle}" /></p>
                                                    <p><c:out value="${draft.externalEvidenceSummary}" /></p>
                                                    <p class="ai-review-source__reference"><c:out value="${draft.externalSourceUrl}" /></p>
                                                </c:if>
                                            </section>
                                        </c:if>
                                    </div>

                                    <div class="ai-review-decision">
                                        <form class="ai-review-approve-form"
                                              action="/adminPage/strategy-tips/ai/approve"
                                              method="post"
                                              onsubmit="return window.confirm('검토한 내용으로 한줄 공략을 승인할까요? 승인 즉시 사이트에 공개됩니다.');">
                                            <input type="hidden" name="draftId" value="<c:out value='${draft.draftId}' />">
                                            <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}' />">
                                            <div class="ai-review-edit-grid">
                                                <div class="ai-review-field">
                                                    <label for="draft-category-${draftLoop.index}">공개 분류</label>
                                                    <input type="hidden" name="category"
                                                           value="<c:out value='${draft.category}' />">
                                                    <input id="draft-category-${draftLoop.index}"
                                                           class="ai-review-field__readonly" type="text"
                                                           value="<c:out value='${draft.categoryName}' />" readonly>
                                                    <span class="ai-review-field__note">근거와 연결된 분류는 변경할 수 없습니다.</span>
                                                </div>
                                                <div class="ai-review-field ai-review-field--content">
                                                    <label for="draft-content-${draftLoop.index}">공개 문장</label>
                                                    <textarea id="draft-content-${draftLoop.index}" name="content" rows="3"
                                                              maxlength="160" required
                                                              data-ai-content
                                                              data-counter-id="draft-count-${draftLoop.index}"
                                                              aria-describedby="draft-help-${draftLoop.index} draft-count-${draftLoop.index}"><c:out value="${draft.content}" /></textarea>
                                                    <div class="ai-review-field__meta">
                                                        <span id="draft-help-${draftLoop.index}">원문 근거 구절 전체를 그대로 남긴 채 다듬어 주세요.</span>
                                                        <span id="draft-count-${draftLoop.index}" class="ai-review-character-count" aria-live="polite">0 / 160</span>
                                                    </div>
                                                </div>
                                            </div>
                                            <div class="ai-review-approve-bar">
                                                <span>승인하면 일반 한줄 공략 목록에 즉시 공개됩니다.</span>
                                                <button class="ai-review-button ai-review-button--approve" type="submit">검토 완료 · 승인</button>
                                            </div>
                                        </form>

                                        <form class="ai-review-reject-form"
                                              action="/adminPage/strategy-tips/ai/reject"
                                              method="post"
                                              onsubmit="return window.confirm('이 초안을 반려할까요? 반려한 초안은 공개되지 않습니다.');">
                                            <input type="hidden" name="draftId" value="<c:out value='${draft.draftId}' />">
                                            <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}' />">
                                            <button class="ai-review-button ai-review-button--reject" type="submit">이 초안 반려</button>
                                        </form>
                                    </div>
                                </article>
                            </c:forEach>

                            <c:if test="${empty pendingDrafts}">
                                <div class="ai-review-empty">
                                    <span aria-hidden="true">✓</span>
                                    <h3>검토할 초안이 없습니다.</h3>
                                    <p>새 초안을 생성해도 승인 전까지는 공개되지 않습니다.</p>
                                </div>
                            </c:if>
                        </div>
                    </section>

                    <section class="ai-review-section ai-review-history-section" aria-labelledby="recent-drafts-title">
                        <div class="ai-review-section-heading">
                            <div>
                                <span class="ai-review-kicker">RECENT DECISIONS</span>
                                <h2 id="recent-drafts-title">최근 처리 이력</h2>
                                <p>승인·반려 결과와 공개된 한줄 공략 번호를 확인합니다.</p>
                            </div>
                        </div>

                        <div class="ai-review-history-wrap">
                            <table class="ai-review-history-table">
                                <caption class="sc-visually-hidden">최근 AI 한줄 공략 초안 처리 이력</caption>
                                <thead>
                                    <tr>
                                        <th scope="col">처리 상태</th>
                                        <th scope="col">초안 내용</th>
                                        <th scope="col">검토 정보</th>
                                        <th scope="col">내부 근거</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <c:forEach var="draft" items="${recentDrafts}">
                                        <c:set var="statusTone" value="neutral" />
                                        <c:set var="statusLabel" value="${draft.status}" />
                                        <c:choose>
                                            <c:when test="${draft.status eq 'APPROVED' or draft.status eq 'PUBLISHED'}">
                                                <c:set var="statusTone" value="approved" />
                                                <c:set var="statusLabel" value="승인" />
                                            </c:when>
                                            <c:when test="${draft.status eq 'REJECTED'}">
                                                <c:set var="statusTone" value="rejected" />
                                                <c:set var="statusLabel" value="반려" />
                                            </c:when>
                                            <c:when test="${draft.status eq 'PENDING'}">
                                                <c:set var="statusTone" value="pending" />
                                                <c:set var="statusLabel" value="검토 대기" />
                                            </c:when>
                                        </c:choose>
                                        <tr>
                                            <td data-label="처리 상태">
                                                <span class="ai-review-badge ai-review-badge--<c:out value='${statusTone}' />">
                                                    <c:choose>
                                                        <c:when test="${not empty statusLabel}"><c:out value="${statusLabel}" /></c:when>
                                                        <c:otherwise>상태 없음</c:otherwise>
                                                    </c:choose>
                                                </span>
                                                <span class="ai-review-history__id">ID <c:out value="${draft.draftId}" /></span>
                                            </td>
                                            <td data-label="초안 내용">
                                                <span class="ai-review-history__category"><c:out value="${draft.categoryName}" /></span>
                                                <p class="ai-review-history__content"><c:out value="${draft.content}" /></p>
                                                <p class="ai-review-history__meta">
                                                    <c:out value="${draft.generationDate}" /> · SLOT <c:out value="${draft.slotNo}" />
                                                    · <c:out value="${draft.model}" /> · <c:out value="${draft.createdAt}" />
                                                </p>
                                                <c:if test="${not empty draft.evidenceSummary or not empty draft.externalEvidenceSummary or not empty draft.sourceExcerpt}">
                                                    <details class="ai-review-history__details">
                                                        <summary>검토 근거 보기</summary>
                                                        <c:if test="${not empty draft.evidenceSummary}">
                                                            <p>내부: <c:out value="${draft.evidenceSummary}" /></p>
                                                        </c:if>
                                                        <c:if test="${not empty draft.externalEvidenceSummary}">
                                                            <p>과거 외부 근거: <c:out value="${draft.externalEvidenceSummary}" /></p>
                                                        </c:if>
                                                        <c:if test="${not empty draft.sourceExcerpt}">
                                                            <blockquote><c:out value="${draft.sourceExcerpt}" /></blockquote>
                                                        </c:if>
                                                    </details>
                                                </c:if>
                                            </td>
                                            <td data-label="검토 정보">
                                                <p class="ai-review-history__reviewed">
                                                    <c:choose>
                                                        <c:when test="${not empty draft.reviewedAt}"><c:out value="${draft.reviewedAt}" /></c:when>
                                                        <c:otherwise>미처리</c:otherwise>
                                                    </c:choose>
                                                </p>
                                                <p class="ai-review-history__meta">
                                                    검토자
                                                    <c:choose>
                                                        <c:when test="${not empty draft.reviewedBy}"><c:out value="${draft.reviewedBy}" /></c:when>
                                                        <c:otherwise>-</c:otherwise>
                                                    </c:choose>
                                                </p>
                                                <c:if test="${not empty draft.publishedTipNum}">
                                                    <a class="ai-review-history__published" href="/strategy-tips">
                                                        공개 #<c:out value="${draft.publishedTipNum}" />
                                                    </a>
                                                </c:if>
                                            </td>
                                            <td data-label="내부 근거">
                                                <c:choose>
                                                    <c:when test="${not empty draft.sourceBoard and draft.sourcePostNum gt 0}">
                                                        <c:url var="recentSourceUrl" value="/boards/${draft.sourceBoard}/readPost">
                                                            <c:param name="postNum" value="${draft.sourcePostNum}" />
                                                        </c:url>
                                                        <a class="ai-review-history__source"
                                                           href="<c:out value='${recentSourceUrl}' />"
                                                           target="_blank" rel="noopener noreferrer">
                                                            <c:choose>
                                                                <c:when test="${not empty draft.sourceTitle}"><c:out value="${draft.sourceTitle}" /></c:when>
                                                                <c:otherwise><c:out value="${draft.sourceBoard}" /> #<c:out value="${draft.sourcePostNum}" /></c:otherwise>
                                                            </c:choose>
                                                            <span class="sc-visually-hidden">(새 창)</span><span aria-hidden="true">↗</span>
                                                        </a>
                                                        <p class="ai-review-history__meta">
                                                            <c:out value="${draft.sourceBoard}" /> #<c:out value="${draft.sourcePostNum}" />
                                                        </p>
                                                    </c:when>
                                                    <c:otherwise>
                                                        <span class="ai-review-history__meta">출처 정보 없음</span>
                                                    </c:otherwise>
                                                </c:choose>
                                                <c:if test="${not empty draft.externalSourceUrl}">
                                                    <a class="ai-review-history__source ai-review-history__source--external"
                                                       href="<c:out value='${draft.externalSourceUrl}' />"
                                                       target="_blank" rel="noopener noreferrer">
                                                        <c:choose>
                                                            <c:when test="${not empty draft.externalSourceTitle}"><c:out value="${draft.externalSourceTitle}" /></c:when>
                                                            <c:otherwise>외부 근거 열기</c:otherwise>
                                                        </c:choose>
                                                        <span class="sc-visually-hidden">(새 창)</span><span aria-hidden="true">↗</span>
                                                    </a>
                                                    <p class="ai-review-history__meta">과거 방식의 외부 인용</p>
                                                </c:if>
                                            </td>
                                        </tr>
                                    </c:forEach>
                                    <c:if test="${empty recentDrafts}">
                                        <tr>
                                            <td class="ai-review-history__empty" colspan="4">아직 처리된 AI 초안이 없습니다.</td>
                                        </tr>
                                    </c:if>
                                </tbody>
                            </table>
                        </div>
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
        var counterId = field.getAttribute('data-counter-id');
        var counter = counterId ? document.getElementById(counterId) : null;
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
