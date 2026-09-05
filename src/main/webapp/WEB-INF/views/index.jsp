<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
    <!DOCTYPE html>
    <html lang="ko">

    <head>
        <title><c:out value="${pageTitle}"/></title>
        <style>
            /* 모바일에서는 종족 네트워크 최신글을 게시판당 2개만 보여 세로 스크롤을 줄인다 */
            @media screen and (max-width: 600px) {
                .sc-home-post-extra {
                    display: none;
                }
            }
        </style>
        <%@include file="./include/head.jspf" %>
    </head>

    <body class="sc-home-page">
        <%@include file="./include/header.jspf" %>
        <section class="sc-home-intro" aria-labelledby="scHomeTitle">
            <div class="sc-container sc-home-intro__inner">
                <div>
                    <p class="sc-home-eyebrow">SC1Hub 공략실</p>
                    <h1 id="scHomeTitle">스타크래프트1 빌드와 실전 운영</h1>
                    <p>내 종족과 상대 종족에 맞는 공략을 찾아보세요.</p>
                </div>
                <a class="btn sc-primary-action" href="/strategy-tips">한줄 공략 모아보기 <span aria-hidden="true">→</span></a>
            </div>
        </section>
        <div class="section-inner">
            <div class="sc-container">
                <div class="sc-row">
                    <%@include file="./include/latestPosts.jspf" %>
                        <%@include file="./include/sidebar.jspf" %>
                            <main class="sc-col-9 sc-home-main" id="main-content">
                            <div class="sc-content-heading"><h2>종족별 공략</h2><span>테란 · 저그 · 프로토스</span></div>
                            <div class="sc-home-feeds">
                            <c:set var="menuIndex" value="22" />
                            <c:forEach var="section" items="${popularSections}">
                                <section class="sc-home-feed ${section.cssClass}">
                                    <h3 class="sc-race-heading"><c:out value="${fn:replace(section.title, '네트워크', '공략')}" /></h3>
                                    <div class="sc-matchups">
                                        <c:forEach var="board" items="${section.boards}">
                                            <c:choose>
                                                <c:when test="${fn:contains(board.boardTitle, 'vst')}"><c:set var="opponentName" value="테란" /></c:when>
                                                <c:when test="${fn:contains(board.boardTitle, 'vsz')}"><c:set var="opponentName" value="저그" /></c:when>
                                                <c:otherwise><c:set var="opponentName" value="프로토스" /></c:otherwise>
                                            </c:choose>
                                            <div class="sc-matchup">
                                                <h4><a href="/boards/${board.boardTitle}"><c:out value="${opponentName}" /> 상대하기 <span aria-hidden="true">→</span></a></h4>
                                                <ul class="sc-home-posts">
                                                    <c:forEach var="post" items="${board.posts}" end="2" varStatus="postStatus">
                                                        <li class="${postStatus.index ge 2 ? 'sc-home-post-extra' : ''}">
                                                            <a href="/boards/${board.boardTitle}/readPost?postNum=${post.postNum}"
                                                               data-menu-number="${menuIndex}">
                                                                <span class="sc-home-post-number">${menuIndex}</span>
                                                                <span class="sc-home-post-title"><c:out value="${post.title}" /></span>
                                                            </a>
                                                        </li>
                                                        <c:set var="menuIndex" value="${menuIndex + 1}" />
                                                    </c:forEach>
                                                </ul>
                                            </div>
                                        </c:forEach>
                                    </div>
                                </section>
                            </c:forEach>
                            </div>
                            <div class="sc-home-note"><span aria-hidden="true">ⓘ</span> 상대 종족별 게시판에서 빌드 순서와 대응법을 찾아보세요. <a href="/boards/tipboard">꿀팁보급고 →</a></div>
                            </main>
                </div> <!-- End sc-row -->
            </div>
        </div>
        <%@include file="./include/footer.jspf" %>
    </body>

    </html>
