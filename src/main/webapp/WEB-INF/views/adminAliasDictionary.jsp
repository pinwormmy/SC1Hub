<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html>
<head>
<title>SC1Hub - alias_dictionary 관리</title>
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
    padding: 0 16px;
    border: 1px solid rgba(255, 255, 255, 0.45);
    background: rgba(255, 255, 255, 0.08);
    color: rgba(255, 255, 255, 0.9);
    cursor: pointer;
}
.admin-btn--ghost {
    background: rgba(255, 255, 255, 0.03);
}
.admin-btn--danger {
    background: rgba(255, 70, 70, 0.18);
    border-color: rgba(255, 70, 70, 0.55);
}
.alias-form {
    margin-top: 14px;
    display: grid;
    gap: 12px;
}
.alias-grid {
    display: grid;
    grid-template-columns: 160px 1fr;
    gap: 10px 16px;
    align-items: start;
}
.alias-label {
    font-weight: 600;
    color: rgba(255, 255, 255, 0.9);
}
.alias-textarea,
.alias-input {
    width: 100%;
    border: 1px solid rgba(255, 255, 255, 0.45);
    background: rgba(0, 0, 0, 0.15);
    color: rgba(255, 255, 255, 0.9);
    padding: 10px 12px;
}
.alias-textarea {
    min-height: 90px;
    resize: vertical;
}
.alias-help {
    grid-column: 2 / -1;
    font-size: 14px;
    color: rgba(255, 255, 255, 0.65);
}
.alias-checkbox-group {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
}
.alias-checkbox-item {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    color: rgba(255, 255, 255, 0.92);
}
.alias-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
}
.alias-divider {
    height: 1px;
    background: rgba(255, 255, 255, 0.15);
    margin: 16px 0;
}
.alias-table-wrap {
    overflow-x: auto;
    margin-top: 12px;
}
.alias-table {
    width: 100%;
    border-collapse: collapse;
    min-width: 820px;
}
.alias-table th,
.alias-table td {
    border-bottom: 1px solid rgba(255, 255, 255, 0.12);
    padding: 10px 8px;
    text-align: left;
    vertical-align: top;
}
.alias-table th {
    color: rgba(255, 255, 255, 0.8);
    font-weight: 600;
}
.alias-muted {
    color: rgba(255, 255, 255, 0.65);
    font-size: 14px;
}
.alias-message {
    margin-top: 10px;
    padding: 10px 12px;
    border: 1px solid rgba(255, 255, 255, 0.3);
    background: rgba(0, 0, 0, 0.35);
}
@media (max-width: 768px) {
    .alias-grid {
        grid-template-columns: 1fr;
    }
    .alias-help {
        grid-column: 1 / -1;
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
                    <div class="admin-card">
                        <div class="admin-card-header">
                            <div>
                                <h2 class="admin-card-title">alias_dictionary 관리</h2>
                                <p class="admin-card-subtitle">별칭 등록/수정/삭제 및 검색</p>
                            </div>
                            <button type="button" class="admin-btn admin-btn--ghost" onclick="location.href='/adminPage'">뒤로가기</button>
                        </div>

                        <c:if test="${not empty message}">
                            <div class="alias-message">${message}</div>
                        </c:if>

                        <form action="/adminPage/aliasDictionary" method="get" class="admin-search-form">
                            <label class="admin-search-label" for="aliasKeyword">검색</label>
                            <div class="admin-search-row">
                                <input class="admin-input" id="aliasKeyword" type="text" name="keyword" value="${keyword}" placeholder="alias, canonical_terms, 대상 게시판">
                                <button type="submit" class="admin-btn">검색</button>
                                <c:if test="${not empty keyword}">
                                    <button type="button" class="admin-btn admin-btn--ghost" onclick="location.href='/adminPage/aliasDictionary'">검색취소</button>
                                </c:if>
                            </div>
                        </form>

                        <div class="alias-divider"></div>

                        <h3 class="admin-card-title" style="font-size: 22px;">새 별칭 등록</h3>
                        <form action="/adminPage/aliasDictionary/create" method="post" class="alias-form">
                            <div class="alias-grid">
                                <label class="alias-label" for="createAlias">alias *</label>
                                <input class="alias-input" id="createAlias" type="text" name="alias" value="${createForm.alias}" placeholder="커공발">

                                <label class="alias-label" for="createCanonical">canonical_terms *</label>
                                <textarea class="alias-textarea" id="createCanonical" name="canonicalTerms" placeholder="줄바꿈 또는 콤마로 구분">${createForm.canonicalTerms}</textarea>
                                <div class="alias-help">예) 커피공장, 커피공장 빌드, 커피공장 운영</div>

                                <label class="alias-label">대상 게시판</label>
                                <div class="alias-checkbox-group">
                                    <label class="alias-checkbox-item">
                                        <input type="checkbox" name="boardTargets" value="pvspboard"
                                               <c:if test="${fn:contains(createBoardTargetsText, ',pvspboard,')}">checked</c:if>>
                                        프프전
                                    </label>
                                    <label class="alias-checkbox-item">
                                        <input type="checkbox" name="boardTargets" value="pvstboard"
                                               <c:if test="${fn:contains(createBoardTargetsText, ',pvstboard,')}">checked</c:if>>
                                        프테전
                                    </label>
                                    <label class="alias-checkbox-item">
                                        <input type="checkbox" name="boardTargets" value="pvszboard"
                                               <c:if test="${fn:contains(createBoardTargetsText, ',pvszboard,')}">checked</c:if>>
                                        프저전
                                    </label>
                                    <label class="alias-checkbox-item">
                                        <input type="checkbox" name="boardTargets" value="tvspboard"
                                               <c:if test="${fn:contains(createBoardTargetsText, ',tvspboard,')}">checked</c:if>>
                                        테프전
                                    </label>
                                    <label class="alias-checkbox-item">
                                        <input type="checkbox" name="boardTargets" value="tvstboard"
                                               <c:if test="${fn:contains(createBoardTargetsText, ',tvstboard,')}">checked</c:if>>
                                        테테전
                                    </label>
                                    <label class="alias-checkbox-item">
                                        <input type="checkbox" name="boardTargets" value="tvszboard"
                                               <c:if test="${fn:contains(createBoardTargetsText, ',tvszboard,')}">checked</c:if>>
                                        테저전
                                    </label>
                                    <label class="alias-checkbox-item">
                                        <input type="checkbox" name="boardTargets" value="zvspboard"
                                               <c:if test="${fn:contains(createBoardTargetsText, ',zvspboard,')}">checked</c:if>>
                                        저프전
                                    </label>
                                    <label class="alias-checkbox-item">
                                        <input type="checkbox" name="boardTargets" value="zvstboard"
                                               <c:if test="${fn:contains(createBoardTargetsText, ',zvstboard,')}">checked</c:if>>
                                        저테전
                                    </label>
                                    <label class="alias-checkbox-item">
                                        <input type="checkbox" name="boardTargets" value="zvszboard"
                                               <c:if test="${fn:contains(createBoardTargetsText, ',zvszboard,')}">checked</c:if>>
                                        저저전
                                    </label>
                                    <label class="alias-checkbox-item">
                                        <input type="checkbox" name="boardTargets" value="tipboard"
                                               <c:if test="${fn:contains(createBoardTargetsText, ',tipboard,')}">checked</c:if>>
                                        꿀팁 게시판
                                    </label>
                                </div>
                                <div class="alias-help">VS 게시판은 개별 선택 가능하며, 하나만 선택하거나 여러 개를 함께 선택할 수 있습니다.</div>
                            </div>
                            <input type="hidden" name="keyword" value="${keyword}">
                            <div class="alias-actions">
                                <button type="submit" class="admin-btn">등록</button>
                            </div>
                        </form>

                        <c:if test="${not empty editForm}">
                            <div class="alias-divider"></div>
                            <h3 class="admin-card-title" style="font-size: 22px;">별칭 수정</h3>
                            <form action="/adminPage/aliasDictionary/update" method="post" class="alias-form">
                                <input type="hidden" name="id" value="${editForm.id}">
                                <div class="alias-grid">
                                    <label class="alias-label" for="editAlias">alias *</label>
                                    <input class="alias-input" id="editAlias" type="text" name="alias" value="${editForm.alias}">

                                    <label class="alias-label" for="editCanonical">canonical_terms *</label>
                                    <textarea class="alias-textarea" id="editCanonical" name="canonicalTerms">${editForm.canonicalTerms}</textarea>
                                    <div class="alias-help">줄바꿈 또는 콤마로 구분</div>

                                    <label class="alias-label">대상 게시판</label>
                                    <div class="alias-checkbox-group">
                                        <label class="alias-checkbox-item">
                                            <input type="checkbox" name="boardTargets" value="pvspboard"
                                                   <c:if test="${fn:contains(editBoardTargetsText, ',pvspboard,')}">checked</c:if>>
                                            프프전
                                        </label>
                                        <label class="alias-checkbox-item">
                                            <input type="checkbox" name="boardTargets" value="pvstboard"
                                                   <c:if test="${fn:contains(editBoardTargetsText, ',pvstboard,')}">checked</c:if>>
                                            프테전
                                        </label>
                                        <label class="alias-checkbox-item">
                                            <input type="checkbox" name="boardTargets" value="pvszboard"
                                                   <c:if test="${fn:contains(editBoardTargetsText, ',pvszboard,')}">checked</c:if>>
                                            프저전
                                        </label>
                                        <label class="alias-checkbox-item">
                                            <input type="checkbox" name="boardTargets" value="tvspboard"
                                                   <c:if test="${fn:contains(editBoardTargetsText, ',tvspboard,')}">checked</c:if>>
                                            테프전
                                        </label>
                                        <label class="alias-checkbox-item">
                                            <input type="checkbox" name="boardTargets" value="tvstboard"
                                                   <c:if test="${fn:contains(editBoardTargetsText, ',tvstboard,')}">checked</c:if>>
                                            테테전
                                        </label>
                                        <label class="alias-checkbox-item">
                                            <input type="checkbox" name="boardTargets" value="tvszboard"
                                                   <c:if test="${fn:contains(editBoardTargetsText, ',tvszboard,')}">checked</c:if>>
                                            테저전
                                        </label>
                                        <label class="alias-checkbox-item">
                                            <input type="checkbox" name="boardTargets" value="zvspboard"
                                                   <c:if test="${fn:contains(editBoardTargetsText, ',zvspboard,')}">checked</c:if>>
                                            저프전
                                        </label>
                                        <label class="alias-checkbox-item">
                                            <input type="checkbox" name="boardTargets" value="zvstboard"
                                                   <c:if test="${fn:contains(editBoardTargetsText, ',zvstboard,')}">checked</c:if>>
                                            저테전
                                        </label>
                                        <label class="alias-checkbox-item">
                                            <input type="checkbox" name="boardTargets" value="zvszboard"
                                                   <c:if test="${fn:contains(editBoardTargetsText, ',zvszboard,')}">checked</c:if>>
                                            저저전
                                        </label>
                                        <label class="alias-checkbox-item">
                                            <input type="checkbox" name="boardTargets" value="tipboard"
                                                   <c:if test="${fn:contains(editBoardTargetsText, ',tipboard,')}">checked</c:if>>
                                            꿀팁 게시판
                                        </label>
                                    </div>
                                    <div class="alias-help">VS 게시판은 개별 선택 가능하며, 하나만 선택하거나 여러 개를 함께 선택할 수 있습니다.</div>
                                </div>
                                <input type="hidden" name="keyword" value="${keyword}">
                                <div class="alias-actions">
                                    <button type="submit" class="admin-btn">수정</button>
                                    <button type="button" class="admin-btn admin-btn--ghost" onclick="location.href='/adminPage/aliasDictionary?keyword=${keyword}'">수정취소</button>
                                </div>
                            </form>
                        </c:if>

                        <div class="alias-divider"></div>

                        <h3 class="admin-card-title" style="font-size: 22px;">별칭 목록</h3>
                        <div class="alias-table-wrap">
                            <table class="alias-table">
                                <thead>
                                    <tr>
                                        <th width="5%">ID</th>
                                        <th width="15%">alias</th>
                                        <th width="35%">canonical_terms</th>
                                        <th width="25%">대상 게시판</th>
                                        <th width="20%">관리</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <c:forEach items="${aliasList}" var="alias">
                                        <tr>
                                            <td>${alias.id}</td>
                                            <td>${alias.alias}</td>
                                            <td>
                                                <div>${canonicalDisplay[alias.id]}</div>
                                            </td>
                                            <td>
                                                <div>${boardTargetDisplay[alias.id]}</div>
                                            </td>
                                            <td>
                                                <div class="alias-actions">
                                                    <button type="button" class="admin-btn admin-btn--ghost" onclick="location.href='/adminPage/aliasDictionary?editId=${alias.id}&keyword=${keyword}'">수정</button>
                                                    <form action="/adminPage/aliasDictionary/delete" method="post" onsubmit="return confirm('삭제하시겠습니까?');">
                                                        <input type="hidden" name="id" value="${alias.id}">
                                                        <input type="hidden" name="keyword" value="${keyword}">
                                                        <button type="submit" class="admin-btn admin-btn--danger">삭제</button>
                                                    </form>
                                                </div>
                                            </td>
                                        </tr>
                                    </c:forEach>
                                    <c:if test="${empty aliasList}">
                                        <tr>
                                            <td colspan="5" class="alias-muted">조회 결과가 없습니다.</td>
                                        </tr>
                                    </c:if>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>
<%@ include file="/WEB-INF/views/include/footer.jspf" %>
</body>
</html>
