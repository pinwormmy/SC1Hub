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
                            <div class="sc-col-3">
                                <fieldset class="terran-field">
                                    <legend style="color:#75f94c;"> 테란 네트워크 </legend>
                                    <table class="boardList" style="width: 100%;">
                                        <tr>
                                            <td><a href="/boards/tVsZBoard" style="color:yellow;">21. 테저전 게시판</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/tVsZBoard/readPost?postNum=3">22. 테저전 정석 운영</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/tVsZBoard/readPost?postNum=2">23. 선엔베 업테란 운영</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/tVsZBoard/readPost?postNum=9">24. 초보추천! 5팩 골리앗</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/tVsZBoard/readPost?postNum=6">25. 테저전 심시티 모음</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/tVsPBoard" style="color:yellow;">26. 테프전 게시판</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/tVsPBoard/readPost?postNum=7">27. 공1업 5팩 타이밍</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/tVsPBoard/readPost?postNum=3">28. 타이밍 러쉬정리</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/tVsPBoard/readPost?postNum=11">29. 안전한 팩더블</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/tVsPBoard/readPost?postNum=10">30. 쉽고 센 투팩 찌르기</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/tVsTBoard" style="color:yellow;">31. 테테전 게시판</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/tVsTBoard/readPost?postNum=3">32. 빨리 끝내는 법</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/tVsTBoard/readPost?postNum=2">33. 테테전 기본 정석</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/tVsTBoard/readPost?postNum=5">34. 테테전 후반 운영법</a></td>
                                        </tr>
                                    </table>
                                </fieldset>
                            </div>
                            <div class="sc-col-3">
                                <fieldset class="zerg-field">
                                    <legend style="color:#75f94c;">저그 네트워크</legend>
                                    <table class="boardList" style="width: 100%;">
                                        <tr>
                                            <td><a href="/boards/zVsTBoard" style="color:yellow;">35. 저테전 게시판</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/zVsTBoard/readPost?postNum=3">36. 저테전 정석 운영</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/zVsTBoard/readPost?postNum=6">37. 뮤탈짤짤이 방법</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/zVsTBoard/readPost?postNum=8">38. 5럴커 뚫기 전략</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/zVsTBoard/readPost?postNum=13">39. 쉬운 운영. 미친 저그</a>
                                            </td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/zVsPBoard" style="color:yellow;">40. 저프전 게시판</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/zVsPBoard/readPost?postNum=2">41. 개사기 973 빌드</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/zVsPBoard/readPost?postNum=3">42. 저프전 정석 운영</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/zVsPBoard/readPost?postNum=9">43. 8겟뽕 대처하기</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/zVsPBoard/readPost?postNum=4">44. 하이브 운영법</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/zVsZBoard" style="color:yellow;">45. 저저전 게시판</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/zVsZBoard/readPost?postNum=2">46. 저저전 빌드 상성</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/zVsZBoard/readPost?postNum=4">47. 4드론 대신할 날먹</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/zVsZBoard/readPost?postNum=3">48. 스커지 잡기 컨트롤</a></td>
                                        </tr>
                                    </table>
                                </fieldset>
                            </div>
                            <div class="sc-col-3">
                                <fieldset class="protoss-field">
                                    <legend style="color:#75f94c;">프로토스 네트워크</legend>
                                    <table class="boardList" style="width: 100%;">
                                        <tr>
                                            <td><a href="/boards/pVsTBoard" style="color:yellow;">49. 프테전 게시판</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/pVsTBoard/readPost?postNum=2">50. 23넥 아비터 운영</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/pVsTBoard/readPost?postNum=3">51. 대각 생넥 캐리어</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/pVsTBoard/readPost?postNum=4">52. 전진로보 걸리버</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/pVsTBoard/readPost?postNum=5">53. 테란전 정석 운영</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/pVsZBoard" style="color:yellow;">54. 프저전 게시판</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/pVsZBoard/readPost?postNum=4">55. 프저전 정석 운영</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/pVsZBoard/readPost?postNum=3">56. 개같은 973 대처하기</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/pVsZBoard/readPost?postNum=20">57. 프저전 심시티 모음</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/pVsZBoard/readPost?postNum=8">58. 최강! 8겟뽕 빌드</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/pVsPBoard" style="color:yellow;">59. 프프전 게시판</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/pVsPBoard/readPost?postNum=4">60. 프프전 빌드 상성</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/pVsPBoard/readPost?postNum=2">61. 프프전 정석 빌드</a></td>
                                        </tr>
                                        <tr>
                                            <td><a href="/boards/pVsPBoard/readPost?postNum=5">62. 투게이트 대처법</a></td>
                                        </tr>
                                    </table>
                                </fieldset>
                            </div>
                </div> <!-- End sc-row -->
            </div>
        </div>
        <%@include file="./include/footer.jspf" %>
    </body>

    </html>
