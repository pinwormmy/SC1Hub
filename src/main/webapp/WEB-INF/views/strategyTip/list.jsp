<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <title>${koreanTitle}</title>
    <link rel="stylesheet" type="text/css" href="/css/postList.css?v=${applicationScope.assetVersion}">
    <link rel="stylesheet" type="text/css" href="/css/strategyTip.css?v=${applicationScope.assetVersion}">
    <%@include file="../include/header.jspf" %>
</head>
<body>
    <div class="section-inner">
        <div class="container">
            <div class="row">
                <%@include file="../include/latestPosts.jspf" %>
                <%@include file="../include/sidebar.jspf" %>
                <div class="col-sm-9">
                    <div class="sc-panel">
                    <div class="post-heading mb">[한줄 공략]</div>
                    <c:if test="${not empty msg}">
                        <script>window.alert('${msg}');</script>
                    </c:if>

                    <div class="strategy-tip-tabs" aria-label="한줄 공략 분류">
                        <a class="strategy-tip-tab ${empty category ? 'is-active' : ''}" href="/strategy-tips">전체</a>
                        <c:forEach var="tipCategory" items="${categories}">
                            <a class="strategy-tip-tab ${category == tipCategory.code ? 'is-active' : ''}"
                               href="/strategy-tips?category=${tipCategory.code}">${tipCategory.name}</a>
                        </c:forEach>
                    </div>

                    <div class="strategy-tip-list">
                        <c:forEach var="tip" items="${tips}">
                            <article class="strategy-tip-item">
                                <div class="strategy-tip-item__main">
                                    <span class="strategy-tip-item__category">${tip.categoryName}</span>
                                    <p class="strategy-tip-item__content"><c:out value="${tip.content}"/></p>
                                    <div class="strategy-tip-item__meta">
                                        <span>${tip.writer}</span>
                                        <span><fmt:formatDate pattern="MM-dd HH:mm" value="${tip.regDate}"/></span>
                                    </div>
                                </div>
                                <div class="strategy-tip-item__actions">
                                    <button type="button" class="strategy-tip-like" data-tip-num="${tip.tipNum}">
                                        추천 <span>${tip.recommendCount}</span>
                                    </button>
                                    <c:choose>
                                        <c:when test="${not empty member and (member.id == tip.memberId or member.id == 'admin')}">
                                            <form action="/strategy-tips/delete" method="post" onsubmit="return confirm('삭제할까요?');">
                                                <input type="hidden" name="tipNum" value="${tip.tipNum}">
                                                <button type="submit" class="strategy-tip-delete">삭제</button>
                                            </form>
                                        </c:when>
                                        <c:when test="${empty member and empty tip.memberId}">
                                            <form action="/strategy-tips/delete" method="post" onsubmit="return confirmGuestTipDelete(this);">
                                                <input type="hidden" name="tipNum" value="${tip.tipNum}">
                                                <input type="hidden" name="guestPassword" value="">
                                                <button type="submit" class="strategy-tip-delete">삭제</button>
                                            </form>
                                        </c:when>
                                    </c:choose>
                                </div>
                            </article>
                        </c:forEach>
                        <c:if test="${empty tips}">
                            <p class="strategy-tip-empty">아직 등록된 한줄 공략이 없습니다.</p>
                        </c:if>
                    </div>

                    <form class="strategy-tip-form" action="/strategy-tips" method="post">
                        <div class="strategy-tip-form__meta">
                            <select name="category" class="form-control" aria-label="한줄 공략 분류" required>
                                <option value="">분류 선택</option>
                                <c:forEach var="tipCategory" items="${categories}">
                                    <option value="${tipCategory.code}">${tipCategory.name}</option>
                                </c:forEach>
                            </select>
                            <c:choose>
                                <c:when test="${not empty member}">
                                    <span class="strategy-tip-form__writer">${member.nickName}</span>
                                </c:when>
                                <c:otherwise>
                                    <input type="text" name="writer" class="form-control" placeholder="닉네임" required>
                                    <input type="password" name="guestPassword" class="form-control" placeholder="비밀번호" required>
                                </c:otherwise>
                            </c:choose>
                        </div>
                        <div class="strategy-tip-form__body">
                            <textarea name="content" class="form-control" rows="2" maxlength="160"
                                      placeholder="한줄 공략을 입력하세요. 예) 테저전 3배럭 압박 뒤 엔베 타이밍을 늦추지 않기" required></textarea>
                            <button type="submit" class="btn btn-theme">등록</button>
                        </div>
                    </form>

                    <div class="sc-paging strategy-tip-paging">
                        <c:if test="${page.prevPageSetPoint >= 1}">
                            <c:url var="prevPageUrl" value="/strategy-tips">
                                <c:param name="recentPage" value="${page.prevPageSetPoint}" />
                                <c:if test="${not empty category}">
                                    <c:param name="category" value="${category}" />
                                </c:if>
                            </c:url>
                            <a class="sc-paging__nav sc-paging__prev btn btn-theme"
                               href="${prevPageUrl}">[이전]</a>
                        </c:if>
                        <div class="sc-paging__pages">
                            <c:forEach var="countPage" begin="${page.pageBeginPoint}" end="${page.pageEndPoint}">
                                <c:url var="countPageUrl" value="/strategy-tips">
                                    <c:param name="recentPage" value="${countPage}" />
                                    <c:if test="${not empty category}">
                                        <c:param name="category" value="${category}" />
                                    </c:if>
                                </c:url>
                                <a class="sc-paging__page btn btn-theme ${countPage == page.recentPage ? 'is-current' : ''}"
                                   href="${countPageUrl}">[${countPage}]</a>
                            </c:forEach>
                        </div>
                        <c:if test="${page.nextPageSetPoint <= page.totalPage}">
                            <c:url var="nextPageUrl" value="/strategy-tips">
                                <c:param name="recentPage" value="${page.nextPageSetPoint}" />
                                <c:if test="${not empty category}">
                                    <c:param name="category" value="${category}" />
                                </c:if>
                            </c:url>
                            <a class="sc-paging__nav sc-paging__next btn btn-theme"
                               href="${nextPageUrl}">[다음]</a>
                        </c:if>
                    </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
    <%@include file="../include/footer.jspf" %>
    <script>
    function confirmGuestTipDelete(form) {
        var password = window.prompt('작성 시 입력한 비밀번호를 입력해주세요.');
        if (!password) {
            return false;
        }
        form.elements['guestPassword'].value = password;
        return true;
    }

    document.querySelectorAll('.strategy-tip-like').forEach(function(button) {
        button.addEventListener('click', function() {
            var tipNum = button.getAttribute('data-tip-num');
            fetch('/strategy-tips/recommend?tipNum=' + encodeURIComponent(tipNum), { method: 'POST' })
                .then(function(response) { return response.json(); })
                .then(function(data) {
                    if (data.recommendCount !== undefined) {
                        button.querySelector('span').textContent = data.recommendCount;
                    } else if (data.message) {
                        window.alert(data.message);
                    }
                })
                .catch(function() {
                    window.alert('추천 처리 중 문제가 발생했습니다.');
                });
        });
    });
    </script>
</body>
</html>
