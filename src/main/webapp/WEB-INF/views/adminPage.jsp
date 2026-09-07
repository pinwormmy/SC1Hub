<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
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
.admin-accordion-header {
    cursor: pointer;
    user-select: none;
}
.admin-accordion-indicator {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 8px 10px;
    border: 1px solid rgba(255, 255, 255, 0.35);
    background: rgba(0, 0, 0, 0.2);
    color: rgba(255, 255, 255, 0.85);
    font-size: 16px;
    white-space: nowrap;
}
.admin-accordion-indicator::after {
    content: "▼";
    font-size: 12px;
    opacity: 0.75;
}
.admin-accordion-header[aria-expanded="true"] .admin-accordion-indicator::after {
    content: "▲";
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
    border-color: #FF5555;
    color: #FF5555;
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
.admin-indexing-section {
    margin-top: 14px;
}
.admin-indexing-title {
    margin: 0;
    font-size: 20px;
    color: rgba(255, 255, 255, 0.9);
}
.admin-indexing-row {
    margin-top: 10px;
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
}
.admin-indexing-label {
    color: rgba(255, 255, 255, 0.75);
    font-size: 14px;
}
.admin-indexing-input {
    width: 120px;
    height: 44px;
    border: 1px solid rgba(255, 255, 255, 0.45);
    background: rgba(0, 0, 0, 0.2);
    color: rgba(255, 255, 255, 0.9);
    padding-left: 10px;
}
.admin-indexing-hint {
    margin-top: 8px;
    color: rgba(255, 255, 255, 0.7);
    font-size: 14px;
    line-height: 1.4;
}
.admin-indexing-output {
    margin-top: 10px;
    border: 1px solid rgba(255, 255, 255, 0.25);
    background: rgba(0, 0, 0, 0.35);
    padding: 12px;
    white-space: pre-wrap;
    word-break: break-word;
    max-height: 320px;
    overflow: auto;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
    font-size: 13px;
    color: rgba(255, 255, 255, 0.9);
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

<%@ include file="./include/head.jspf" %>
</head>
<body>
<%@ include file="./include/header.jspf" %>
<div class="section-inner">
    <div class="container">
        <div class="row">
            <%@include file="./include/latestPosts.jspf" %>
            <div class="col-sm-12">
                <div class="admin-page">
                    <div class="admin-card admin-card--visitors">
                        <div class="admin-card-header">
                            <div>
                                <h3 class="admin-card-title">일별 최근 방문자수</h3>
                                <p class="admin-card-subtitle">최근 10일 기준</p>
                            </div>
                            <button type="button" class="admin-btn admin-btn--ghost" onclick="location.href='/myPage'">뒤로가기</button>
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
                    <div class="admin-card admin-card--members">
                        <div class="admin-card-header admin-accordion-header" id="adminMemberAccordionHeader"
                             role="button" tabindex="0" aria-controls="adminMemberAccordionBody" aria-expanded="false">
                            <div>
                                <h2 class="admin-card-title">회원 관리</h2>
                                <p class="admin-card-subtitle">
                                    총 가입회원 <fmt:formatNumber value="${totalMemberCount}" type="number"/>명
                                    <c:if test="${pageInfo.keyword != ''}">
                                        · 검색결과 <fmt:formatNumber value="${pageInfo.totalPostCount}" type="number"/>명
                                    </c:if>
                                    · 회원목록 (최근 가입자 순)
                                </p>
                            </div>
                            <span class="admin-accordion-indicator" id="adminMemberAccordionIndicator">펼치기</span>
                        </div>
                        <div id="adminMemberAccordionBody" style="display: none;">
                            <form action="/adminPage" method="get" class="admin-search-form">
                                <label class="admin-search-label" for="adminKeyword">회원 검색</label>
                                <div class="admin-search-row">
                                    <input class="admin-input" id="adminKeyword" type="text" name="keyword" value="<c:out value='${pageInfo.keyword}'/>" placeholder="ID, 별명, 이름">
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
                                                <td data-label="ID"><c:out value="${member.id}"/></td>
                                                <td data-label="별명"><c:out value="${member.nickName}"/></td>
                                                <td data-label="이메일"><c:out value="${member.email}"/></td>
                                                <td data-label="등급">${member.grade}</td>
                                                <td data-label="가입일"><fmt:formatDate value="${member.regDate}" pattern="yy.MM.dd"/></td>
                                                <td data-label="관리">
                                                    <div class="admin-actions">
                                                        <button type="button" class="admin-btn admin-btn--ghost" data-member-edit data-member-id="<c:out value='${member.id}'/>">수정</button>
                                                        <button type="button" class="admin-btn admin-btn--danger" data-member-delete data-member-id="<c:out value='${member.id}'/>">탈퇴</button>
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
                                            <a class="page-link" href="/adminPage?recentPage=${pageInfo.prevPageSetPoint}&searchType=<c:out value='${pageInfo.searchType}'/>&keyword=<c:out value='${pageInfo.keyword}'/>" aria-label="Previous">
                                                <span aria-hidden="true">&laquo;</span>
                                            </a>
                                        </li>
                                    </c:if>
                                    <c:forEach var="i" begin="${pageInfo.pageBeginPoint}" end="${pageInfo.pageEndPoint}">
                                        <c:choose>
                                            <c:when test="${i == pageInfo.recentPage}">
                                                <li><a class="page-link active" href="/adminPage?recentPage=${i}&searchType=<c:out value='${pageInfo.searchType}'/>&keyword=<c:out value='${pageInfo.keyword}'/>">${i}</a></li>
                                            </c:when>
                                            <c:otherwise>
                                                <li><a class="page-link" href="/adminPage?recentPage=${i}&searchType=<c:out value='${pageInfo.searchType}'/>&keyword=<c:out value='${pageInfo.keyword}'/>">${i}</a></li>
                                            </c:otherwise>
                                        </c:choose>
                                    </c:forEach>
                                    <c:if test="${pageInfo.nextPageSetPoint <= pageInfo.totalPage}">
                                        <li class="page-item">
                                            <a class="page-link" href="/adminPage?recentPage=${pageInfo.nextPageSetPoint}&searchType=<c:out value='${pageInfo.searchType}'/>&keyword=<c:out value='${pageInfo.keyword}'/>" aria-label="Next">
                                                <span aria-hidden="true">&raquo;</span>
                                            </a>
                                        </li>
                                    </c:if>
                                </ul>
                            </nav>
                        </div>
                    </div>
                    <div class="admin-card admin-card--security">
                        <div class="admin-card-header">
                            <div>
                                <h2 class="admin-card-title">보안 운영</h2>
                                <p class="admin-card-subtitle">쓰기 스위치 · 제재(IP 차단/회원 뮤트) · 자동 차단 현황</p>
                            </div>
                            <button type="button" class="admin-btn admin-btn--ghost" id="adminSecurityRefreshBtn">새로고침</button>
                        </div>
                        <div class="admin-indexing-section">
                            <div class="admin-indexing-hint" id="adminSecurityStatus">상태를 불러오는 중…</div>
                            <div class="admin-indexing-row">
                                <button type="button" class="admin-btn admin-btn--danger" data-security-switch="public-writes" data-enabled="false">회원 쓰기 차단(긴급)</button>
                                <button type="button" class="admin-btn" data-security-switch="public-writes" data-enabled="true">회원 쓰기 재개</button>
                                <button type="button" class="admin-btn admin-btn--ghost" data-security-switch="guest-writes" data-enabled="true">비회원 쓰기 허용</button>
                                <button type="button" class="admin-btn admin-btn--ghost" data-security-switch="guest-writes" data-enabled="false">비회원 쓰기 차단</button>
                            </div>
                            <div class="admin-indexing-hint">스위치는 즉시 반영되며 재시작하면 설정 파일의 기본값으로 돌아갑니다.</div>
                        </div>
                        <div class="admin-indexing-section">
                            <h3 class="admin-indexing-title">제재 추가</h3>
                            <form id="adminSanctionForm" class="admin-indexing-row" autocomplete="off">
                                <select class="admin-indexing-input" name="type" aria-label="제재 유형">
                                    <option value="BLOCK_IP">IP 차단</option>
                                    <option value="MUTE">회원 뮤트</option>
                                </select>
                                <input class="admin-indexing-input" name="target" placeholder="IP 또는 회원 ID" style="width: 200px;" required>
                                <input class="admin-indexing-input" name="minutes" type="number" min="0" placeholder="분(비우면 영구)" style="width: 140px;">
                                <input class="admin-indexing-input" name="reason" placeholder="사유" style="width: 220px;" maxlength="200">
                                <button type="submit" class="admin-btn admin-btn--danger">제재 적용</button>
                            </form>
                            <div class="admin-indexing-hint">IP는 공인 주소만 받습니다. 어떤 IP가 보이는지는 "내 접속 정보"로 확인할 수 있습니다.</div>
                            <div class="admin-indexing-row">
                                <button type="button" class="admin-btn admin-btn--ghost" id="adminSecurityEchoBtn">내 접속 정보</button>
                            </div>
                        </div>
                        <div class="admin-indexing-section">
                            <h3 class="admin-indexing-title">활성 제재</h3>
                            <div class="admin-table-wrap">
                                <table class="admin-memberlist" id="adminSanctionTable">
                                    <thead>
                                        <tr>
                                            <th width="12%">유형</th>
                                            <th width="22%">대상</th>
                                            <th width="16%">표시명</th>
                                            <th width="26%">사유</th>
                                            <th width="14%">해제</th>
                                            <th width="10%">관리</th>
                                        </tr>
                                    </thead>
                                    <tbody></tbody>
                                </table>
                            </div>
                        </div>
                        <div class="admin-indexing-section">
                            <h3 class="admin-indexing-title">최근 응답</h3>
                            <pre class="admin-indexing-output" id="adminSecurityOutput" aria-live="polite"></pre>
                        </div>
                    </div>
                    <div class="admin-card">
                        <div class="admin-card-header">
                            <div>
                                <h2 class="admin-card-title">alias_dictionary 관리</h2>
                                <p class="admin-card-subtitle">별칭 등록/수정/삭제 및 검색</p>
                            </div>
                            <button type="button" class="admin-btn admin-btn--ghost" onclick="location.href='/adminPage/aliasDictionary'">관리하기</button>
                        </div>
                    </div>
                    <div class="admin-card">
                        <div class="admin-card-header">
                            <div>
                                <h2 class="admin-card-title">운영 점검</h2>
                                <p class="admin-card-subtitle">자동 발행 설정, 슬롯, 최근 생성 이력</p>
                            </div>
                            <button type="button" class="admin-btn admin-btn--ghost" onclick="location.href='/adminPage/ops'">확인하기</button>
                        </div>
                    </div>
                    <div class="admin-card admin-card--indexing">
                        <div class="admin-card-header">
                            <div>
                                <h2 class="admin-card-title">인덱싱</h2>
                                <p class="admin-card-subtitle">RAG + search_terms 통합 재인덱싱</p>
                            </div>
                        </div>

                        <div class="admin-indexing-section">
                            <h3 class="admin-indexing-title">통합 실행</h3>
                            <div class="admin-indexing-row">
                                <button type="button" class="admin-btn admin-btn--ghost" id="adminIndexStatusBtn" data-admin-indexing-action="true">상태</button>
                                <button type="button" class="admin-btn" id="adminIndexReindexBtn" data-admin-indexing-action="true">reindex</button>
                                <button type="button" class="admin-btn admin-btn--ghost" id="adminIndexUpdateBtn" data-admin-indexing-action="true">update</button>
                            </div>
                            <div class="admin-indexing-hint">reindex: RAG reindex(비동기). update: RAG update. search_terms는 alias_dictionary 변경 시 즉시 백그라운드 재인덱싱됩니다. 필요 시 /api/assistant/search-terms/reindex 로 수동 재인덱싱할 수 있습니다.</div>
                        </div>

                        <div class="admin-indexing-section">
                            <h3 class="admin-indexing-title">최근 응답</h3>
                            <pre class="admin-indexing-output" id="adminIndexingOutput" aria-live="polite"></pre>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>
<%@ include file="/WEB-INF/views/include/footer.jspf" %>

<script>
// 회원 ID를 인라인 핸들러 대신 data 속성으로 넘겨받아 스크립트 삽입 경로를 없앤다.
document.addEventListener('click', function (event) {
    var target = event.target instanceof Element ? event.target.closest('[data-member-id]') : null;
    if (!target) {
        return;
    }
    var id = target.getAttribute('data-member-id') || '';
    if (target.hasAttribute('data-member-edit')) {
        location.href = '/modifyMemberByAdmin?id=' + encodeURIComponent(id);
    } else if (target.hasAttribute('data-member-delete')) {
        confirmDelete(id);
    }
});

async function confirmDelete(id) {
    if(confirm("정말로 탈퇴시키겠습니까?")) {
        try {
            var response = await fetch('/deleteMember', {
                method: 'POST',
                credentials: 'same-origin',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                    'X-Requested-With': 'XMLHttpRequest'
                },
                body: new URLSearchParams({ id: id }).toString()
            });
            var data = await response.json();
            if (response.ok && data.success) {
                alert("탈퇴가 완료되었습니다.");
                location.href = '/adminPage';
                return;
            }
        } catch (error) {
            console.error('회원 탈퇴 처리 실패', error);
        }
        alert("탈퇴 처리 중 오류가 발생했습니다.");
    }
}

(function() {
    var header = document.getElementById('adminMemberAccordionHeader');
    var body = document.getElementById('adminMemberAccordionBody');
    var indicator = document.getElementById('adminMemberAccordionIndicator');
    var storageKey = 'sc1hub_admin_member_accordion_open';

    if (!header || !body) {
        return;
    }

    function applyExpanded(expanded) {
        header.setAttribute('aria-expanded', expanded ? 'true' : 'false');
        body.style.display = expanded ? 'block' : 'none';
        if (indicator) {
            indicator.textContent = expanded ? '접기' : '펼치기';
        }
    }

    function toggle() {
        var nextExpanded = header.getAttribute('aria-expanded') !== 'true';
        applyExpanded(nextExpanded);
        try {
            localStorage.setItem(storageKey, nextExpanded ? '1' : '0');
        } catch (e) {
            // ignore
        }
    }

    header.addEventListener('click', toggle);
    header.addEventListener('keydown', function(e) {
        if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            toggle();
        }
    });

    var keywordInput = document.getElementById('adminKeyword');
    var hasKeyword = !!(keywordInput && keywordInput.value && keywordInput.value.trim());

    try {
        var stored = localStorage.getItem(storageKey);
        if (stored === '1') {
            applyExpanded(true);
            return;
        }
        if (stored === '0') {
            applyExpanded(false);
            return;
        }
    } catch (e) {
        // ignore
    }

    applyExpanded(hasKeyword);
})();

(function() {
    var outputEl = document.getElementById('adminIndexingOutput');
    if (!outputEl) {
        return;
    }

    var actionEls = Array.prototype.slice.call(document.querySelectorAll('[data-admin-indexing-action]'));

    function setBusy(busy) {
        actionEls.forEach(function(el) {
            el.disabled = !!busy;
        });
    }

    function writeOutput(title, result) {
        var timestamp = new Date().toLocaleString();
        var body = '';
        if (result && result.json) {
            body = JSON.stringify(result.json, null, 2);
        } else if (result && typeof result.text === 'string') {
            body = result.text;
        } else if (result && result.error) {
            body = String(result.error);
        }

        var header = '[' + timestamp + '] ' + title;
        if (result && typeof result.status === 'number') {
            header += ' (' + result.status + ')';
        }
        outputEl.textContent = header + '\n' + (body || '') + '\n';
    }

    async function request(method, url) {
        var response = await fetch(url, {
            method: method,
            headers: { Accept: 'application/json' },
            credentials: 'include',
        });
        var contentType = response.headers.get('content-type') || '';
        var text = await response.text().catch(function() { return ''; });
        var json = null;
        if (contentType.indexOf('application/json') !== -1 && text) {
            try {
                json = JSON.parse(text);
            } catch (e) {
                json = null;
            }
        }
        return { ok: response.ok, status: response.status, contentType: contentType, text: text, json: json };
    }

    function bind(id, handler) {
        var el = document.getElementById(id);
        if (!el) {
            return;
        }
        el.addEventListener('click', handler);
    }

    bind('adminIndexStatusBtn', async function() {
        setBusy(true);
        try {
            var result = await request('GET', '/api/assistant/index/status');
            writeOutput('GET /api/assistant/index/status', result);
        } catch (e) {
            writeOutput('GET /api/assistant/index/status', { error: e });
        } finally {
            setBusy(false);
        }
    });

    bind('adminIndexReindexBtn', async function() {
        if (!confirm('통합 reindex를 실행할까요? (RAG reindex + search_terms 재인덱싱)')) {
            return;
        }
        setBusy(true);
        try {
            var result = await request('POST', '/api/assistant/index/reindex');
            writeOutput('POST /api/assistant/index/reindex', result);
        } catch (e) {
            writeOutput('POST /api/assistant/index/reindex', { error: e });
        } finally {
            setBusy(false);
        }
    });

    bind('adminIndexUpdateBtn', async function() {
        if (!confirm('통합 update를 실행할까요? (RAG update + search_terms 재인덱싱)')) {
            return;
        }
        setBusy(true);
        try {
            var result = await request('POST', '/api/assistant/index/update');
            writeOutput('POST /api/assistant/index/update', result);
        } catch (e) {
            writeOutput('POST /api/assistant/index/update', { error: e });
        } finally {
            setBusy(false);
        }
    });
})();

(function() {
    var outputEl = document.getElementById('adminSecurityOutput');
    var statusEl = document.getElementById('adminSecurityStatus');
    var tableBody = document.querySelector('#adminSanctionTable tbody');
    if (!outputEl || !statusEl || !tableBody) {
        return;
    }

    function writeOutput(title, result) {
        var body = result && result.json ? JSON.stringify(result.json, null, 2)
            : (result && typeof result.text === 'string' ? result.text : String((result && result.error) || ''));
        outputEl.textContent = '[' + new Date().toLocaleString() + '] ' + title
            + (result && typeof result.status === 'number' ? ' (' + result.status + ')' : '') + '\n' + body + '\n';
    }

    async function call(method, url, payload) {
        var options = { method: method, credentials: 'same-origin', headers: { Accept: 'application/json' } };
        if (payload !== undefined) {
            options.headers['Content-Type'] = 'application/json';
            options.body = JSON.stringify(payload);
        }
        var response = await fetch(url, options);
        var text = await response.text().catch(function() { return ''; });
        var json = null;
        try { json = text ? JSON.parse(text) : null; } catch (e) { json = null; }
        return { ok: response.ok, status: response.status, text: text, json: json };
    }

    function cell(text) {
        var td = document.createElement('td');
        td.textContent = text == null ? '' : String(text);
        return td;
    }

    function renderSanctions(rows) {
        tableBody.textContent = '';
        if (!rows || !rows.length) {
            var tr = document.createElement('tr');
            var td = cell('활성 제재가 없습니다.');
            td.className = 'admin-empty';
            td.colSpan = 6;
            tr.appendChild(td);
            tableBody.appendChild(tr);
            return;
        }
        rows.forEach(function(row) {
            var tr = document.createElement('tr');
            tr.appendChild(cell(row.type === 'MUTE' ? '회원 뮤트' : 'IP 차단'));
            tr.appendChild(cell(row.target));
            tr.appendChild(cell(row.nickname));
            tr.appendChild(cell(row.reason));
            tr.appendChild(cell(row.expiresAtText));
            var actions = document.createElement('td');
            var btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'admin-btn admin-btn--ghost';
            btn.textContent = '해제';
            btn.setAttribute('data-sanction-revoke', String(row.id));
            actions.appendChild(btn);
            tr.appendChild(actions);
            tableBody.appendChild(tr);
        });
    }

    function renderStatus(s) {
        if (!s) {
            statusEl.textContent = '상태를 불러오지 못했습니다.';
            return;
        }
        statusEl.textContent = '회원 쓰기: ' + (s.publicWritesEnabled ? '허용' : '차단')
            + ' · 비회원 쓰기: ' + (s.guestWritesEnabled ? '허용' : '차단')
            + ' · 활성 제재: ' + s.activeSanctionCount + '건'
            + ' · 최근 10분 거부: ' + s.recentRejections10Min + '회'
            + ' · 자동 차단(기동 후): ' + s.autoBansSinceStart + '건'
            + ' · 평문 비밀번호: ' + s.legacyPasswordCount + '건';
    }

    async function refresh() {
        try {
            var status = await call('GET', '/api/admin/security/status');
            renderStatus(status.json);
            var sanctions = await call('GET', '/api/admin/security/sanctions');
            renderSanctions(Array.isArray(sanctions.json) ? sanctions.json : []);
        } catch (e) {
            renderStatus(null);
        }
    }

    document.getElementById('adminSecurityRefreshBtn').addEventListener('click', refresh);

    document.addEventListener('click', async function(event) {
        var target = event.target instanceof Element ? event.target : null;
        if (!target) {
            return;
        }
        var switchEl = target.closest('[data-security-switch]');
        if (switchEl) {
            var name = switchEl.getAttribute('data-security-switch');
            var enabled = switchEl.getAttribute('data-enabled') === 'true';
            if (name === 'public-writes' && !enabled && !confirm('회원 글·댓글·채팅·가입을 즉시 차단할까요?')) {
                return;
            }
            var result = await call('POST', '/api/admin/security/' + name, { enabled: enabled });
            writeOutput('POST /api/admin/security/' + name, result);
            refresh();
            return;
        }
        var revokeEl = target.closest('[data-sanction-revoke]');
        if (revokeEl) {
            var id = revokeEl.getAttribute('data-sanction-revoke');
            if (!confirm('이 제재를 해제할까요?')) {
                return;
            }
            var revoked = await call('DELETE', '/api/admin/security/sanctions/' + encodeURIComponent(id));
            writeOutput('DELETE /api/admin/security/sanctions/' + id, revoked);
            refresh();
        }
    });

    document.getElementById('adminSanctionForm').addEventListener('submit', async function(event) {
        event.preventDefault();
        var form = event.target;
        var payload = {
            type: form.elements.type.value,
            target: form.elements.target.value.trim(),
            minutes: form.elements.minutes.value ? Number(form.elements.minutes.value) : null,
            reason: form.elements.reason.value.trim()
        };
        if (!payload.target) {
            return;
        }
        if (!confirm((payload.type === 'MUTE' ? '회원 뮤트' : 'IP 차단') + ' 제재를 적용할까요? 대상: ' + payload.target)) {
            return;
        }
        var result = await call('POST', '/api/admin/security/sanctions', payload);
        writeOutput('POST /api/admin/security/sanctions', result);
        if (result.ok) {
            form.reset();
        }
        refresh();
    });

    document.getElementById('adminSecurityEchoBtn').addEventListener('click', async function() {
        var result = await call('GET', '/api/admin/security/request-echo');
        writeOutput('GET /api/admin/security/request-echo', result);
    });

    refresh();
})();
</script>

</body>
</html>
