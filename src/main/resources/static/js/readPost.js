showCommentList();

// 공통 fetch 함수
async function fetchData(url, method = 'GET', body = null) {
    const headers = {
        'Content-Type': 'application/json',
        'Accept': 'application/json'
    };

    // GET 또는 HEAD 메서드일 경우 body를 제거
    const config = method === 'GET' || method === 'HEAD' ? { method, headers } : { method, headers, body: JSON.stringify(body) };
    const response = await fetch(url, config);

    if (!response.ok) {
        throw new Error(`Fetch failed: ${response.status}`);
    }

    return await response.json();
}

async function addComment() {
    try {
        const commentContent = document.getElementById("commentContent"); // commentContent를 어디서 가져오는지 명시적으로 추가했습니다.
        const nicknameInput = document.getElementById("commentNickname");
        const passwordInput = document.getElementById("commentPassword");

        let nickname = "";
        let password = "";

        // 비로그인 사용자일 경우 닉네임과 비밀번호 필수 체크
        if (!isLoggedIn) {
            if (nicknameInput.value === "") {
                alert("닉네임을 입력해주세요.");
                return false;
            }
            if (passwordInput.value === "") {
                alert("비밀번호를 입력해주세요.");
                return false;
            }
            nickname = nicknameInput.value;
            password = passwordInput.value;
        }

        if (commentContent.value === "") {
            alert("댓글 내용을 작성해주세요~");
            return false;
        }
        const response = await fetch(boardPath + "/addComment", {
            method: 'POST',
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                postNum: postNum,
                content: commentContent.value,
                nickname: nickname,
                password: password
            })
        });

        if (response.ok) {
            const text = await response.text();  // 먼저 텍스트로 응답을 받습니다.
            try {
                const data = JSON.parse(text);  // 그 다음 JSON으로 파싱을 시도합니다.
                console.log(data);
                await showCommentList();
                commentContent.value = "";
                if (!isLoggedIn) {
                    nicknameInput.value = "";
                    passwordInput.value = "";
                }
            } catch (e) {
                console.error("JSON 파싱 오류:", e);
                console.log("서버에서 받은 응답:", text);
            }
        } else {
            const errorMessage = await extractErrorMessage(response, "댓글 추가 실패");
            alert(errorMessage);
        }
    } catch (error) {
        console.error("댓글 추가 중 오류 발생:", error);
        alert(error.message || "댓글 추가 중 오류가 발생했습니다.");
    }
}

async function extractErrorMessage(response, fallbackMessage) {
    try {
        const result = await response.json();
        return result.message || fallbackMessage;
    } catch (e) {
        return fallbackMessage;
    }
}

// 댓글 목록 표시
async function showCommentList(commentPage) {
    await pageSettingAndLoadComment(commentPage);
}

// 페이지 설정 및 댓글 로드
async function pageSettingAndLoadComment(commentPage) {
    try {
        const response = await fetch(boardPath + "/commentPageSetting", {
            method: 'POST',
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                recentPage: commentPage,
                postNum: postNum
            })
        });

        if (response.ok) {
            const data = await response.json();
            console.log(data);
            await loadCommentFetch(data);
            renderCommentPagination(document.getElementById("comments-page"), data);
        } else {
            console.error("페이지 설정 실패");
        }
    } catch (error) {
        console.error("페이지 설정 중 오류 발생:", error);
    }
}

function renderCommentPagination(container, page) {
    const fragment = document.createDocumentFragment();

    function appendPageLink(label, targetPage) {
        const link = document.createElement("a");
        link.href = "#";
        link.textContent = label;
        link.addEventListener("click", (event) => {
            event.preventDefault();
            void pageSettingAndLoadComment(targetPage);
        });
        fragment.appendChild(link);
    }

    if (page.prevPageSetPoint >= 1) {
        appendPageLink("◁", page.prevPageSetPoint);
    }
    if (page.totalPage > 1) {
        for (let number = page.pageBeginPoint; number <= page.pageEndPoint; number++) {
            if (number == page.recentPage) {
                fragment.appendChild(document.createTextNode(" " + number + " "));
            } else {
                appendPageLink(number + " ", number);
            }
        }
    }
    if (page.nextPageSetPoint <= page.totalPage) {
        appendPageLink("▷", page.nextPageSetPoint);
    }

    container.replaceChildren(fragment);
}

// 댓글 불러오기
async function loadCommentFetch(pageDTO) {
    try {
        const response = await fetch(boardPath + "/showCommentList", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(pageDTO),
        });

        if (response.ok) {
            const data = await response.json();
            showCommentWithHtml(data);
        } else {
            console.error("댓글 불러오기 실패");
        }
    } catch (error) {
        console.error("댓글 불러오기 중 오류 발생:", error);
    }
}

// 댓글 목록은 서버 값을 HTML 문자열로 조합하지 않고 DOM text node로 렌더링합니다.
function showCommentWithHtml(comments) {
    const commentList = document.getElementById("comments-list");
    const fragment = document.createDocumentFragment();

    for (const comment of Array.isArray(comments) ? comments : []) {
        fragment.appendChild(createCommentElement(comment));
    }
    commentList.replaceChildren(fragment);
}

function createCommentElement(comment) {
    const media = document.createElement("div");
    media.className = "media";

    const mediaBody = document.createElement("div");
    mediaBody.className = "media-body";

    const wrapper = document.createElement("div");
    wrapper.style.margin = "0";
    wrapper.style.padding = "10px";

    const heading = document.createElement("div");
    heading.className = "media-heading";
    heading.appendChild(document.createTextNode(resolveCommentNickname(comment) + " \u00a0 "));

    const meta = document.createElement("small");
    if (comment.deletable) {
        const deleteButton = document.createElement("button");
        deleteButton.type = "button";
        deleteButton.className = "pull btn btn-right cancel-btn";
        deleteButton.textContent = "댓글삭제(-) ";
        deleteButton.addEventListener("click", () => {
            void deleteComment(comment.commentNum, comment.passwordRequired);
        });
        meta.appendChild(deleteButton);
    }
    meta.appendChild(document.createTextNode(comment.regDate || ""));
    heading.appendChild(meta);

    const content = document.createElement("p");
    content.style.margin = "0";
    content.style.padding = "0";
    content.textContent = comment.content || "";

    wrapper.append(heading, content);
    mediaBody.appendChild(wrapper);
    media.appendChild(mediaBody);
    return media;
}

function resolveCommentNickname(comment) {
    if (comment.memberDTO && comment.memberDTO.nickName) {
        return comment.memberDTO.nickName;
    }
    if (comment.nickname) {
        return comment.nickname + " (비회원)";
    }
    return "익명";
}

// 댓글 삭제
async function deleteComment(commentNum, passwordRequired) {
    const formBody = new URLSearchParams({ commentNum: String(commentNum) });
    if (passwordRequired) {
        const password = window.prompt("댓글 비밀번호를 입력해주세요.");
        if (password === null) {
            return;
        }
        if (!password.trim()) {
            alert("비밀번호를 입력해주세요.");
            return;
        }
        formBody.set("password", password);
    }

    try {
        const response = await fetch(boardPath + "/deleteComment", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
            body: formBody
        });
        if (response.ok) {
            await showCommentList();
        } else {
            alert(await extractErrorMessage(response, "댓글 삭제 실패"));
        }
    } catch (error) {
        alert("댓글 삭제 오류");
    }
}

// 추천 기능
let isRecommended = false;

function updateRecommendButtonText(isRecommended, recommendCount) {
    console.log("updateRecommendButtonText - isRecommended:", isRecommended, "recommendCount:", recommendCount);  // 로그 추가
    const recommendButton = document.querySelector('.recommend-div button');
    if (recommendCount !== undefined) {  // 추천 수가 undefined가 아닌 경우에만 텍스트를 업데이트합니다.
        recommendButton.textContent = isRecommended ? "추천취소(M) : " + recommendCount : "추천(M) : " + recommendCount;
    }
}

async function addRecommend(postNum) {
    if (!isLoggedIn) {
        alert("추천 기능을 사용하려면 로그인이 필요합니다.");
        return;
    }

    // 현재 사용자가 이미 추천했는지 확인
    const checkData = await fetchData(boardPath + "/checkRecommendation?postNum=" + postNum, "GET");
    if (checkData && checkData.checkRecommend !== undefined) {
        isRecommended = checkData.checkRecommend;
    }
    console.log("Initial isRecommended:", isRecommended);  // 디버깅 로그

    let url = isRecommended ? boardPath + "/cancelRecommendation" : boardPath + "/addRecommendation";
    let method = isRecommended ? "POST" : "POST";

    const body = { postNum: postNum };
    await fetchData(url, method, body);  // 추천 상태 변경

    const recommendCount = await fetchRecommendCount(postNum);  // 추천수 업데이트
    console.log("Fetched recommendCount:", recommendCount);  // 디버깅 로그

    // 다시 한번 현재 사용자가 추천했는지 확인
    const updatedCheckData = await fetchData(boardPath + "/checkRecommendation?postNum=" + postNum, "GET");
    if (updatedCheckData && updatedCheckData.checkRecommend !== undefined) {
        isRecommended = updatedCheckData.checkRecommend;
    }
    console.log("Updated isRecommended:", isRecommended);  // 디버깅 로그

    updateRecommendButtonText(isRecommended, recommendCount);
}

async function fetchRecommendCount(postNum) {
    const data = await fetchData(boardPath + "/getRecommendCount?postNum=" + postNum, "GET");
    console.log("fetchRecommendCount 응답:", data);  // 응답 로깅
    if (data !== undefined && data !== null) {  // 조건 수정
        return data;
    } else {
        console.error("추천 수를 가져오는 데 실패했습니다:", data);
        return undefined;
    }
}

window.addEventListener('load', async () => {
    if (isLoggedIn) {
        try {
            const data = await fetchData(boardPath + "/checkRecommendation?postNum=" + postNum, "GET");
            if (data) {
                isRecommended = data.checkRecommend;
                console.log("window.onload - isRecommended:", isRecommended);  // 로그 추가
                updateRecommendButtonText(isRecommended, recommendCount);
            }
        } catch (error) {
            console.error(error);
        }
    }

    if (isAdmin) {
        fetch('/boards/boardList')
            .then(response => response.json())
            .then(data => {
                const selectElement = document.getElementById('moveToBoard');
                data.forEach(board => {
                    const optionElement = document.createElement('option');
                    optionElement.value = board.boardTitle;
                    optionElement.textContent = board.koreanTitle;
                    selectElement.appendChild(optionElement);
                });
            })
            .catch(error => console.error('Error:', error));
    }
});

async function movePost(postNum) {
    const moveToBoard = document.getElementById('moveToBoard').value;

    console.log("게시판이동 기능 디버그 - 게시물번호값: " + postNum);
    console.log("게시판이동 기능 디버그 - 선택게시판: " + moveToBoard);
    if (!moveToBoard) {
        alert('이동할 게시판을 선택해주세요.');
        return;
    }

    try {
        const response = await fetch(boardPath + `/movePost`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                postNum: postNum,
                moveToBoard: moveToBoard
            })
        });
        if (response.ok) {
            alert('게시글이 성공적으로 이동되었습니다.');
            location.reload();  // 페이지 새로고침
        } else {
            alert('게시글 이동에 실패했습니다.');
        }
    } catch (error) {
        console.error('Error:', error);
        alert('게시글 이동 중 오류가 발생했습니다.');
    }
}
