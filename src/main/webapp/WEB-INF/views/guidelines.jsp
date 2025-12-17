<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
<title>SC1Hub - 이용안내</title>
<%@include file="./include/header.jspf" %>
</head>
<body>
    <div class="section-inner">
        <div class="container">
            <div class="row">
                <%@include file="./include/latestPosts.jspf" %>
                <%@include file="./include/sidebar.jspf" %>
                <div class="col-lg-9">
                    <div class="board-info">
                        <h3>게시판 안내</h3>
                        <p>게시판은 크게 두 가지 형태로 운영됩니다.</p>
                        <p>ㄴ이용자가 자유롭게 작성이 가능한 '공개형 게시판'</p>
                        <p>ㄴ이용자는 조회, 추천, 댓글 작성만 가능한 '열람형 게시판'</p>
                        <p><a href="/boards/userGuideBoard">'유저 공략'</a> 게시판에 작성된 글 중 우수게시물은 채택하여 열람형 게시판에 게시될 수 있습니다.</p>
                        <p>※글 작성은 각 게시판 용도에 맞추어 작성 부탁드립니다.</p>
                        <p>운영진 판단 하에 주제에 맞지 않는 글은 이동될 수 있습니다.</p>
                    </div>
                    <!-- License Information -->
                    <div class="license-section">
                        <h3>라이센스 정보</h3>
                        <!-- CKEditor License -->
                        <div class="license-info ckeditor-license">
                            <p>This website is using CKEditor which is copyrighted by CKSource Holding sp. z o.o., licensed under the Mozilla Public License.</p>
                            <p>For more license information, please refer <a href="https://ckeditor.com/legal/ckeditor-oss-license/" target="_blank">here</a>.</p>
                        </div>
                        <!-- Bootstrap License -->
                        <div class="license-info bootstrap-license">
                            <p>
                                This website uses Bootstrap, Copyright © 2011-2022 by The Bootstrap Authors and Twitter, Inc.
                            </p>
                            <p>
                                Licensed under the MIT License (MIT).<br>
                                <a href="https://getbootstrap.com/docs/5.0/about/license/" target="_blank">
                                    View Bootstrap License.
                                </a>
                            </p>
                        </div>
                        <!-- Additional licenses can be added below in a similar format -->
                    </div>
                    <!-- End of License Information -->
                </div>
            </div>
        </div>
    </div>
<%@include file="./include/footer.jspf" %>
</body>
</html>
