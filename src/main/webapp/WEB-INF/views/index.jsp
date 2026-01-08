<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
    <!DOCTYPE html>
    <html>

    <head>
        <title>SC1Hub - 스타크래프트1 전문 커뮤니티</title>
        <style>
            .boardList {
                border-collapse: collapse;
                overflow: hidden;
                margin: 10px 0 10px 0;
                width: 100%;
            }

            .terran-field {
                background-image: url('http://sc1hub.cdn1.cafe24.com/marine1.jpg');
                background-size: cover;
                background-repeat: no-repeat;
                background-position: center;
            }

            .zerg-field {
                background-image: url('http://sc1hub.cdn1.cafe24.com/hydralisk_center1.jpg');
                background-size: cover;
                background-repeat: no-repeat;
                background-position: center;
            }

            .protoss-field {
                background-image: url('http://sc1hub.cdn1.cafe24.com/zeratull1.jpg');
                background-size: cover;
                background-repeat: no-repeat;
                background-position: center;
            }
        </style>
        <%@include file="./include/header.jspf" %>
    </head>

    <body>
        <div class="section-inner">
            <div class="sc-container">
                <div class="sc-row">
                    <%@include file="./include/latestPosts.jspf" %>
                        <%@include file="./include/sidebar.jspf" %>
                            <c:set var="menuIndex" value="21" />
                            <c:forEach var="section" items="${popularSections}">
                                <div class="sc-col-3">
                                    <fieldset class="${section.cssClass}">
                                        <legend style="color:${section.legendColor};">
                                            <c:out value="${section.title}" />
                                        </legend>
                                        <table class="boardList sc-ellipsis-table" style="width: 100%;">
                                            <c:forEach var="board" items="${section.boards}">
                                                <c:forEach var="post" items="${board.posts}" end="4">
                                                    <tr>
                                                        <td class="sc-title-cell">
                                                            <a class="sc-title-clip"
                                                               href="/boards/${board.boardTitle}/readPost?postNum=${post.postNum}"
                                                               data-menu-number="${menuIndex}">
                                                                <span class="sc-title-slide">${menuIndex}. <c:out value="${post.title}" /></span>
                                                            </a>
                                                        </td>
                                                    </tr>
                                                    <c:set var="menuIndex" value="${menuIndex + 1}" />
                                                </c:forEach>
                                            </c:forEach>
                                        </table>
                                    </fieldset>
                                </div>
                            </c:forEach>
                </div> <!-- End sc-row -->
            </div>
        </div>
        <%@include file="./include/footer.jspf" %>
    </body>

    </html>
