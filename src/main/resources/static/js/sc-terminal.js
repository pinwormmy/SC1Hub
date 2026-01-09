(() => {
    const terminalEl = document.getElementById('scTerminal');
    if (!terminalEl) {
        return;
    }

    const feedEnabled = false;
    let titleSlideRafId = 0;

    function findBodyRow(sectionInnerEl) {
        for (const child of Array.from(sectionInnerEl.children)) {
            if (!child.classList.contains('container') && !child.classList.contains('sc-container')) {
                continue;
            }
            for (const row of Array.from(child.children)) {
                if (row.classList.contains('row') || row.classList.contains('sc-row')) {
                    return row;
                }
            }
        }
        return sectionInnerEl.querySelector('.row, .sc-row');
    }

    function ensureFeedListEl() {
        if (!feedEnabled) {
            return null;
        }

        const sectionInnerEl = document.querySelector('.section-inner');
        if (!sectionInnerEl) {
            return null;
        }

        const targetRow = findBodyRow(sectionInnerEl);
        if (!targetRow) {
            return null;
        }

        let feedEl = document.getElementById('scFeed');
        if (!feedEl) {
            feedEl = document.createElement('div');
            feedEl.id = 'scFeed';
            feedEl.className = targetRow.classList.contains('sc-row') ? 'sc-col-12 sc-feed' : 'col-sm-12 sc-feed';

            const listEl = document.createElement('div');
            listEl.id = 'scFeedList';
            listEl.className = 'sc-feed__list';
            feedEl.appendChild(listEl);

            targetRow.appendChild(feedEl);
        }

        const feedListEl = feedEl.querySelector('#scFeedList');
        ensureTerminalNoticeInFeed(feedListEl);
        return feedListEl;
    }

    const outputEl = document.getElementById('scTerminalOutput');
    const inputEl = document.getElementById('scTerminalInput');
    const mirrorEl = document.getElementById('scTerminalMirror');

    if (!outputEl || !inputEl) {
        return;
    }

    function updateTitleSlideOverflow() {
        titleSlideRafId = 0;
        const slideEls = document.querySelectorAll('.sc-title-slide');
        slideEls.forEach((slideEl) => {
            const containerEl = slideEl.closest('.sc-title-cell') || slideEl.closest('.title');
            if (!containerEl) {
                return;
            }
            const isOverflowing = slideEl.scrollWidth > slideEl.clientWidth + 1;
            containerEl.classList.toggle('is-overflowing', isOverflowing);
        });
    }

    function scheduleTitleSlideUpdate() {
        if (titleSlideRafId) {
            cancelAnimationFrame(titleSlideRafId);
        }
        titleSlideRafId = requestAnimationFrame(updateTitleSlideOverflow);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', scheduleTitleSlideUpdate);
    } else {
        scheduleTitleSlideUpdate();
    }

    window.addEventListener('resize', scheduleTitleSlideUpdate);

    if (document.fonts && document.fonts.ready) {
        document.fonts.ready.then(scheduleTitleSlideUpdate).catch(() => {});
    }

    const COLLAPSED_CLASS = 'is-collapsed';

    let feedModeEnabled = false;

    const coarsePointerMql = window.matchMedia ? window.matchMedia('(hover: none) and (pointer: coarse)') : null;
    const narrowViewportMql = window.matchMedia ? window.matchMedia('(max-width: 768px)') : null;

    function isTouchDevice() {
        return Boolean(
            (typeof navigator !== 'undefined' && navigator.maxTouchPoints && navigator.maxTouchPoints > 0)
                || (typeof window !== 'undefined' && 'ontouchstart' in window)
        );
    }

    function isNarrowViewport() {
        if (narrowViewportMql) {
            return Boolean(narrowViewportMql.matches);
        }
        return typeof window !== 'undefined' && typeof window.innerWidth === 'number' && window.innerWidth <= 768;
    }

    function isCoarsePointer() {
        if (coarsePointerMql) {
            return Boolean(coarsePointerMql.matches);
        }
        return isTouchDevice();
    }

    function shouldSuppressProgrammaticFocus() {
        return isCoarsePointer() || isNarrowViewport();
    }

    function focusIfAllowed(targetEl) {
        if (!targetEl || shouldSuppressProgrammaticFocus()) {
            return;
        }
        targetEl.focus();
    }

    function scrollIntoViewIfNeeded(targetEl) {
        if (!targetEl) {
            return;
        }
        targetEl.scrollIntoView({ block: 'end', behavior: shouldSuppressProgrammaticFocus() ? 'smooth' : 'auto' });
    }

    function syncTerminalMirror() {
        if (!mirrorEl) {
            return;
        }
        mirrorEl.textContent = inputEl.value || '';
    }

    if (mirrorEl) {
        syncTerminalMirror();
        inputEl.addEventListener('input', syncTerminalMirror);
        inputEl.addEventListener('focus', syncTerminalMirror);
        inputEl.addEventListener('blur', syncTerminalMirror);
    }

    let mobileTerminalInputEnabled = false;

    function disableMobileTerminalInput() {
        mobileTerminalInputEnabled = false;
        inputEl.setAttribute('inputmode', 'none');
        inputEl.setAttribute('readonly', 'readonly');
        inputEl.setAttribute('disabled', 'disabled');
        inputEl.style.pointerEvents = 'none';
        if (document.activeElement === inputEl) {
            inputEl.blur();
        }
    }

    function enableMobileTerminalInput() {
        mobileTerminalInputEnabled = true;
        inputEl.removeAttribute('disabled');
        inputEl.removeAttribute('readonly');
        inputEl.setAttribute('inputmode', 'text');
        inputEl.style.pointerEvents = '';
        inputEl.focus();
    }

    if (shouldSuppressProgrammaticFocus()) {
        disableMobileTerminalInput();
    } else {
        inputEl.removeAttribute('disabled');
        inputEl.removeAttribute('readonly');
        inputEl.setAttribute('inputmode', 'text');
    }

    window.addEventListener('pageshow', () => {
        if (shouldSuppressProgrammaticFocus()) {
            disableMobileTerminalInput();
        }
    });

    inputEl.addEventListener('focus', () => {
        if (shouldSuppressProgrammaticFocus() && !mobileTerminalInputEnabled) {
            inputEl.blur();
        }
    });

    inputEl.addEventListener('blur', () => {
        if (shouldSuppressProgrammaticFocus()) {
            disableMobileTerminalInput();
        }
    });

    const promptEl = terminalEl.querySelector('.sc-terminal__prompt');
    if (promptEl) {
        promptEl.addEventListener('click', () => {
            if (!shouldSuppressProgrammaticFocus()) {
                return;
            }
            if (mobileTerminalInputEnabled) {
                return;
            }
            enableMobileTerminalInput();
        });
    }

    function activateTerminalAccessKeys(activeItemEl) {
        outputEl.querySelectorAll('[accesskey]').forEach((el) => el.removeAttribute('accesskey'));
        if (!activeItemEl) {
            return;
        }
        activeItemEl.querySelectorAll('[data-sc-accesskey]').forEach((el) => {
            const key = el.getAttribute('data-sc-accesskey') || '';
            if (!key) {
                return;
            }
            el.accessKey = key;
        });
    }

    function enableFeedMode() {
        if (!feedEnabled || feedModeEnabled) {
            return;
        }
        feedModeEnabled = true;

        const footerCoupangEl = document.getElementById('scFooterCoupang');
        if (footerCoupangEl) {
            footerCoupangEl.hidden = true;
        }

        const footerMetaEl = document.getElementById('scFooterMeta');
        if (footerMetaEl) {
            footerMetaEl.hidden = true;
        }
    }

    function openTerminal() {
        terminalEl.classList.remove(COLLAPSED_CLASS);
    }

    function closeTerminal() {
        terminalEl.classList.add(COLLAPSED_CLASS);
    }

    function scrollOutputToBottom() {
        outputEl.scrollTop = outputEl.scrollHeight;
        terminalEl.scrollIntoView({ block: 'end' });
    }

    function getMemberMeta() {
        const metaEl = document.getElementById('scMemberMeta');
        const id = metaEl ? metaEl.dataset.id || '' : '';
        const grade = metaEl ? Number.parseInt(metaEl.dataset.grade || '0', 10) : 0;
        const isLoggedIn = metaEl ? metaEl.dataset.loggedIn === 'true' : false;
        const nickname = metaEl ? metaEl.dataset.nickname || '' : '';
        return { id, grade, isLoggedIn, nickname };
    }

    let boardTitleMapPromise = null;

    async function getBoardTitleMap() {
        if (!boardTitleMapPromise) {
            boardTitleMapPromise = fetch('/boards/boardList', {
                method: 'GET',
                headers: { Accept: 'application/json' },
            })
                .then((response) => (response.ok ? response.json() : []))
                .then((data) => {
                    const map = new Map();
                    (data || []).forEach((board) => {
                        if (board?.boardTitle && board?.koreanTitle) {
                            map.set(String(board.boardTitle).toLowerCase(), board.koreanTitle);
                        }
                    });
                    return map;
                })
                .catch(() => new Map());
        }
        return boardTitleMapPromise;
    }

    async function getBoardDisplayName(boardTitle) {
        const map = await getBoardTitleMap();
        const key = String(boardTitle ?? '').toLowerCase();
        return map.get(key) || boardTitle;
    }

    function sanitizeHtml(html) {
        const parser = new DOMParser();
        const doc = parser.parseFromString(`<div>${html ?? ''}</div>`, 'text/html');
        doc.querySelectorAll('script, style').forEach((el) => el.remove());
        return doc.body.firstElementChild ? doc.body.firstElementChild.innerHTML : '';
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text ?? '';
        return div.innerHTML;
    }

    function createDivider() {
        const dividerEl = document.createElement('hr');
        dividerEl.className = 'sc-divider';
        return dividerEl;
    }

    function createCoupangBannerClone() {
        const bannerEl = document.querySelector('.ad-banners');
        const textEl = document.querySelector('.coupang-banner-text');
        if (!bannerEl && !textEl) {
            return null;
        }

        const wrapperEl = document.createElement('div');
        wrapperEl.className = 'sc-feed__ad';
        if (bannerEl) {
            wrapperEl.appendChild(bannerEl.cloneNode(true));
        }
        if (textEl) {
            wrapperEl.appendChild(textEl.cloneNode(true));
        }
        return wrapperEl;
    }

    function appendLatestPostsInFeed(itemEl) {
        const latestPostsEl = document.getElementById('latestPosts');
        if (!latestPostsEl) {
            return;
        }
        itemEl.appendChild(latestPostsEl);
        itemEl.appendChild(createDivider());
        if (window.scLatestPosts && typeof window.scLatestPosts.render === 'function') {
            window.scLatestPosts.render();
        }
    }

    function appendFeedAdAndLatestPosts(itemEl) {
        const coupangEl = createCoupangBannerClone();
        if (coupangEl) {
            itemEl.appendChild(coupangEl);
            itemEl.appendChild(createDivider());
        }
        appendLatestPostsInFeed(itemEl);
    }

    function formatMmDd(dateText) {
        const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(dateText ?? ''));
        if (!match) {
            return String(dateText ?? '');
        }
        return `${match[2]}-${match[3]}`;
    }

    function createActionLink(label, href, options = {}) {
        const anchorEl = document.createElement('a');
        anchorEl.className = options.className || 'pull btn btn-right cancel-btn';
        anchorEl.href = href;
        if (options.accessKey) {
            anchorEl.setAttribute('data-sc-accesskey', options.accessKey);
        }
        if (options.target) {
            anchorEl.target = options.target;
        }
        if (options.rel) {
            anchorEl.rel = options.rel;
        }
        if (options.bypassTerminal) {
            anchorEl.setAttribute('data-sc-terminal-bypass', '1');
        }
        anchorEl.textContent = label;
        return anchorEl;
    }

    async function fetchRecommendState(boardTitle, postNum) {
        const url = `/boards/${encodeURIComponent(boardTitle)}/checkRecommendation?postNum=${encodeURIComponent(postNum)}`;
        const response = await fetch(url, {
            method: 'GET',
            headers: { Accept: 'application/json' },
        });
        if (response.status === 401) {
            return { isRecommended: false };
        }
        if (!response.ok) {
            throw new Error(`Failed to check recommendation: ${response.status}`);
        }
        const data = await response.json();
        return { isRecommended: Boolean(data?.checkRecommend) };
    }

    async function fetchRecommendCount(boardTitle, postNum) {
        const url = `/boards/${encodeURIComponent(boardTitle)}/getRecommendCount?postNum=${encodeURIComponent(postNum)}`;
        const response = await fetch(url, {
            method: 'GET',
            headers: { Accept: 'application/json' },
        });
        if (!response.ok) {
            throw new Error(`Failed to fetch recommend count: ${response.status}`);
        }
        const data = await response.json();
        const count = Number.parseInt(String(data ?? ''), 10);
        return Number.isNaN(count) ? 0 : count;
    }

    async function setRecommendation(boardTitle, postNum, isRecommended) {
        const url = isRecommended
            ? `/boards/${encodeURIComponent(boardTitle)}/cancelRecommendation`
            : `/boards/${encodeURIComponent(boardTitle)}/addRecommendation`;

        const response = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
            body: JSON.stringify({ postNum }),
        });
        if (!response.ok) {
            throw new Error(`Failed to update recommendation: ${response.status}`);
        }
    }

    async function fetchCommentPageSetting(boardTitle, postNum, recentPage) {
        const url = `/boards/${encodeURIComponent(boardTitle)}/commentPageSetting`;
        const response = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
            body: JSON.stringify({ recentPage, postNum }),
        });
        if (!response.ok) {
            throw new Error(`Failed to load comment page setting: ${response.status}`);
        }
        return response.json();
    }

    async function fetchCommentList(boardTitle, pageDto) {
        const url = `/boards/${encodeURIComponent(boardTitle)}/showCommentList`;
        const response = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
            body: JSON.stringify(pageDto),
        });
        if (!response.ok) {
            throw new Error(`Failed to load comments: ${response.status}`);
        }
        return response.json();
    }

    async function addComment(boardTitle, payload) {
        const url = `/boards/${encodeURIComponent(boardTitle)}/addComment`;
        const response = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
            body: JSON.stringify(payload),
        });
        if (!response.ok) {
            throw new Error(`Failed to add comment: ${response.status}`);
        }
        return response.json().catch(() => ({}));
    }

    async function deleteComment(boardTitle, commentNum) {
        const url = `/boards/${encodeURIComponent(boardTitle)}/deleteComment?commentNum=${encodeURIComponent(commentNum)}`;
        const response = await fetch(url, { method: 'POST' });
        if (!response.ok) {
            throw new Error(`Failed to delete comment: ${response.status}`);
        }
    }

    async function updateCommentCount(boardTitle, postNum) {
        const url = `/boards/${encodeURIComponent(boardTitle)}/updateCommentCount?postNum=${encodeURIComponent(postNum)}`;
        const response = await fetch(url, { method: 'PUT' });
        if (!response.ok) {
            throw new Error(`Failed to update comment count: ${response.status}`);
        }
    }

    function appendPostToFeed(boardTitle, boardDisplayName, postNum, post) {
        const feedListEl = ensureFeedListEl();
        if (!feedListEl) {
            appendSystemMessage('피드 영역을 찾을 수 없습니다.');
            return;
        }

        const memberMeta = getMemberMeta();
        const canEdit = memberMeta.isLoggedIn && (memberMeta.grade === 3 || memberMeta.nickname === post.writer);

        const itemEl = document.createElement('article');
        itemEl.className = 'sc-feed__item';

        const titleEl = document.createElement('div');
        titleEl.className = 'sc-feed__title';
        titleEl.textContent = `[${boardDisplayName}] ${post.postNum}번 | ${post.title}`;

        const infoEl = document.createElement('div');
        infoEl.className = 'sc-feed__meta';
        infoEl.textContent = `작성자: ${post.writer} | 날짜: ${post.regDate} | 조회: ${post.views}`;

        const contentEl = document.createElement('div');
        contentEl.className = 'sc-feed__content';
        contentEl.innerHTML = sanitizeHtml(post.content ?? '');

        let recommendCount = Number.parseInt(String(post.recommendCount ?? '0'), 10);
        recommendCount = Number.isNaN(recommendCount) ? 0 : recommendCount;
        let isRecommended = false;

        let commentCount = Number.parseInt(String(post.commentCount ?? '0'), 10);
        commentCount = Number.isNaN(commentCount) ? 0 : commentCount;

        const commentsEl = document.createElement('div');
        commentsEl.className = 'sc-feed__comments';

        const commentsViewEl = document.createElement('div');
        commentsViewEl.className = 'sc-feed__comments-view';
        commentsViewEl.hidden = true;

        const commentsTitleEl = document.createElement('div');
        commentsTitleEl.className = 'sc-feed__comments-title';
        commentsTitleEl.textContent = '[댓 글]';

        const commentsListEl = document.createElement('div');
        commentsListEl.className = 'sc-feed__comments-list';

        const commentsPageEl = document.createElement('div');
        commentsPageEl.className = 'sc-feed__comments-page';

        commentsViewEl.appendChild(commentsTitleEl);
        commentsViewEl.appendChild(commentsListEl);
        commentsViewEl.appendChild(commentsPageEl);

        const commentFormEl = document.createElement('div');
        commentFormEl.className = 'sc-feed__comment-form';
        commentFormEl.hidden = true;

        const authorRowEl = document.createElement('div');
        authorRowEl.className = 'sc-feed__comment-author';

        let nicknameInputEl = null;
        let passwordInputEl = null;
        if (memberMeta.isLoggedIn) {
            authorRowEl.textContent = `작성자 : ${memberMeta.nickname}`;
        } else {
            nicknameInputEl = document.createElement('input');
            nicknameInputEl.type = 'text';
            nicknameInputEl.placeholder = '닉네임 (필수)';
            nicknameInputEl.className = 'sc-feed__comment-input';

            passwordInputEl = document.createElement('input');
            passwordInputEl.type = 'password';
            passwordInputEl.placeholder = '비밀번호 (필수)';
            passwordInputEl.className = 'sc-feed__comment-input';

            authorRowEl.appendChild(nicknameInputEl);
            authorRowEl.appendChild(passwordInputEl);
        }

        const commentTextareaEl = document.createElement('textarea');
        commentTextareaEl.rows = 2;
        commentTextareaEl.placeholder = '댓글을 작성합니다';
        commentTextareaEl.className = 'sc-feed__comment-textarea';

        const commentSubmitWrapEl = document.createElement('div');
        commentSubmitWrapEl.className = 'sc-feed__comment-submit';

        const commentSubmitButtonEl = document.createElement('button');
        commentSubmitButtonEl.type = 'button';
        commentSubmitButtonEl.className = 'sc-feed__comment-register';
        commentSubmitButtonEl.textContent = '등록';

        commentSubmitWrapEl.appendChild(commentSubmitButtonEl);

        commentFormEl.appendChild(authorRowEl);
        commentFormEl.appendChild(commentTextareaEl);
        commentFormEl.appendChild(commentSubmitWrapEl);

        commentsEl.appendChild(commentsViewEl);
        commentsEl.appendChild(commentFormEl);

        async function loadComments(recentPage) {
            try {
                const pageDto = await fetchCommentPageSetting(boardTitle, postNum, recentPage);
                const updatedCount = Number.parseInt(String(pageDto?.totalPostCount ?? '0'), 10);
                commentCount = Number.isNaN(updatedCount) ? 0 : updatedCount;
                renderCommentViewLink();
                const comments = await fetchCommentList(boardTitle, pageDto);

                commentsListEl.innerHTML = '';
                (comments || []).forEach((comment) => {
                    const commentEl = document.createElement('div');
                    commentEl.className = 'sc-feed__comment';

                    const headerEl = document.createElement('div');
                    headerEl.className = 'sc-feed__comment-meta';

                    const nickname =
                        comment?.memberDTO?.nickName
                            ? comment.memberDTO.nickName
                            : comment?.nickname
                                ? `${comment.nickname} (비회원)`
                                : '익명';
                    const regDate = comment?.regDate ?? '';
                    headerEl.textContent = `${nickname}  ${regDate}`;

                    const canDeleteComment = memberMeta.grade === 3 || (memberMeta.id && memberMeta.id === comment?.id);
                    if (canDeleteComment && comment?.commentNum) {
                        const deleteButtonEl = document.createElement('button');
                        deleteButtonEl.type = 'button';
                        deleteButtonEl.className = 'pull btn btn-right cancel-btn';
                        deleteButtonEl.textContent = '댓글삭제(-)';
                        deleteButtonEl.addEventListener('click', () => {
                            void (async () => {
                                try {
                                    await deleteComment(boardTitle, comment.commentNum);
                                    await updateCommentCount(boardTitle, postNum);
                                    await loadComments(pageDto.recentPage || 1);
                                } catch (e) {
                                    appendSystemMessage('댓글 삭제 중 오류가 발생했습니다.');
                                }
                            })();
                        });
                        headerEl.appendChild(deleteButtonEl);
                    }

                    const contentTextEl = document.createElement('div');
                    contentTextEl.className = 'sc-feed__comment-content';
                    contentTextEl.textContent = comment?.content ?? '';

                    commentEl.appendChild(headerEl);
                    commentEl.appendChild(contentTextEl);
                    commentsListEl.appendChild(commentEl);
                });

                commentsPageEl.innerHTML = '';
                const pageInfo = pageDto || {};
                const pageBegin = pageInfo.pageBeginPoint ?? 1;
                const pageEnd = pageInfo.pageEndPoint ?? 1;
                const totalPage = pageInfo.totalPage ?? 1;
                const currentPage = pageInfo.recentPage ?? 1;

                function addPagerButton(label, targetPage, isCurrent = false) {
                    const buttonEl = document.createElement('button');
                    buttonEl.type = 'button';
                    buttonEl.className = `btn btn-theme${isCurrent ? ' is-current' : ''}`;
                    buttonEl.textContent = label;
                    buttonEl.addEventListener('click', () => {
                        void loadComments(targetPage);
                    });
                    commentsPageEl.appendChild(buttonEl);
                }

                if ((pageInfo.prevPageSetPoint ?? 0) >= 1) {
                    addPagerButton('◁', pageInfo.prevPageSetPoint);
                }

                if (totalPage > 1) {
                    for (let i = pageBegin; i <= pageEnd; i += 1) {
                        addPagerButton(String(i), i, i === currentPage);
                    }
                }

                if ((pageInfo.nextPageSetPoint ?? 0) <= totalPage) {
                    if ((pageInfo.nextPageSetPoint ?? 0) >= 1) {
                        addPagerButton('▷', pageInfo.nextPageSetPoint);
                    }
                }
            } catch (e) {
                commentsListEl.innerHTML = '';
                commentsPageEl.innerHTML = '';
            }
        }

        async function refreshCommentCount() {
            try {
                const pageDto = await fetchCommentPageSetting(boardTitle, postNum, 1);
                const updatedCount = Number.parseInt(String(pageDto?.totalPostCount ?? '0'), 10);
                commentCount = Number.isNaN(updatedCount) ? 0 : updatedCount;
                renderCommentViewLink();
            } catch (e) {
                // ignore
            }
        }

        commentSubmitButtonEl.addEventListener('click', () => {
            const content = (commentTextareaEl.value || '').trim();
            if (!content) {
                alert('댓글 내용을 작성해주세요~');
                return;
            }

            if (!memberMeta.isLoggedIn) {
                const nick = (nicknameInputEl?.value || '').trim();
                const pw = (passwordInputEl?.value || '').trim();
                if (!nick) {
                    alert('닉네임을 입력해주세요.');
                    return;
                }
                if (!pw) {
                    alert('비밀번호를 입력해주세요.');
                    return;
                }

                void (async () => {
                    try {
                        await addComment(boardTitle, { postNum, id: '', content, nickname: nick, password: pw });
                        await updateCommentCount(boardTitle, postNum);
                        if (!commentsViewEl.hidden) {
                            await loadComments(1);
                        } else {
                            await refreshCommentCount();
                        }
                        commentTextareaEl.value = '';
                        nicknameInputEl.value = '';
                        passwordInputEl.value = '';
                    } catch (e) {
                        appendSystemMessage('댓글 작성 중 오류가 발생했습니다.');
                    }
                })();
                return;
            }

            void (async () => {
                try {
                    await addComment(boardTitle, { postNum, id: memberMeta.id, content });
                    await updateCommentCount(boardTitle, postNum);
                    if (!commentsViewEl.hidden) {
                        await loadComments(1);
                    } else {
                        await refreshCommentCount();
                    }
                    commentTextareaEl.value = '';
                } catch (e) {
                    appendSystemMessage('댓글 작성 중 오류가 발생했습니다.');
                }
            })();
        });

        const actionsEl = document.createElement('div');
        actionsEl.className = 'sc-feed__actions sc-feed__post-actions';

        function createInlineAction(label, accessKey) {
            const linkEl = createActionLink(label, '#', {
                className: 'pull btn btn-right cancel-btn',
                accessKey,
            });
            linkEl.setAttribute('role', 'button');
            linkEl.setAttribute('data-sc-terminal-bypass', '1');
            return linkEl;
        }

        const useCompactPostMenu = isNarrowViewport();

        const listLinkEl = createActionLink(useCompactPostMenu ? '목록' : '목 록(L)', `/boards/${encodeURIComponent(boardTitle)}`, {
            className: 'pull btn btn-right cancel-btn',
            accessKey: 'l',
        });
        actionsEl.appendChild(listLinkEl);

        let extraActionsEl = actionsEl;
        if (useCompactPostMenu) {
            const compactMenuToggleEl = createInlineAction('게시글 메뉴');
            compactMenuToggleEl.classList.add('sc-feed__post-menu-toggle');
            compactMenuToggleEl.setAttribute('aria-expanded', 'false');

            const safeBoardId = String(boardTitle ?? '').replace(/[^a-zA-Z0-9_-]/g, '_') || 'board';
            const menuItemsId = `scFeedPostMenuItems_${safeBoardId}_${postNum}`;

            const menuItemsEl = document.createElement('div');
            menuItemsEl.className = 'sc-feed__post-menu-items';
            menuItemsEl.id = menuItemsId;
            menuItemsEl.hidden = true;

            compactMenuToggleEl.setAttribute('aria-controls', menuItemsId);

            function setCompactMenuOpen(open) {
                menuItemsEl.hidden = !open;
                compactMenuToggleEl.setAttribute('aria-expanded', open ? 'true' : 'false');
            }

            compactMenuToggleEl.addEventListener('click', (event) => {
                event.preventDefault();
                setCompactMenuOpen(menuItemsEl.hidden);
            });

            menuItemsEl.addEventListener('click', (event) => {
                const targetEl = event.target;
                if (!targetEl || !(targetEl.closest && targetEl.closest('a, button'))) {
                    return;
                }
                setCompactMenuOpen(false);
            });

            actionsEl.appendChild(compactMenuToggleEl);
            actionsEl.appendChild(menuItemsEl);
            extraActionsEl = menuItemsEl;
        }

        const recommendLinkEl = createInlineAction('', 'm');

        function renderRecommendLink() {
            if (useCompactPostMenu) {
                recommendLinkEl.textContent = isRecommended
                    ? `취소(${recommendCount})`
                    : `추천(${recommendCount})`;
                return;
            }
            recommendLinkEl.textContent = isRecommended
                ? `추천취소(M): ${recommendCount}`
                : `추천(M): ${recommendCount}`;
        }

        async function refreshRecommend() {
            if (!memberMeta.isLoggedIn) {
                isRecommended = false;
                renderRecommendLink();
                return;
            }

            try {
                const state = await fetchRecommendState(boardTitle, postNum);
                isRecommended = state.isRecommended;
            } catch (e) {
                isRecommended = false;
            }

            try {
                recommendCount = await fetchRecommendCount(boardTitle, postNum);
            } catch (e) {
                recommendCount = Number.parseInt(String(post.recommendCount ?? '0'), 10);
                recommendCount = Number.isNaN(recommendCount) ? 0 : recommendCount;
            }

            renderRecommendLink();
        }

        recommendLinkEl.addEventListener('click', (event) => {
            event.preventDefault();
            if (!memberMeta.isLoggedIn) {
                alert('추천 기능을 사용하려면 로그인이 필요합니다.');
                return;
            }
            void (async () => {
                try {
                    await setRecommendation(boardTitle, postNum, isRecommended);
                    await refreshRecommend();
                } catch (e) {
                    appendSystemMessage('추천 처리 중 오류가 발생했습니다.');
                }
            })();
        });

        renderRecommendLink();
        void refreshRecommend();
        extraActionsEl.appendChild(recommendLinkEl);

        const commentViewLinkEl = createInlineAction('', 'o');

        function renderCommentViewLink() {
            if (useCompactPostMenu) {
                commentViewLinkEl.textContent = `댓글(${commentCount})`;
                return;
            }
            commentViewLinkEl.textContent = `댓글보기(O): ${commentCount}`;
        }

        renderCommentViewLink();
        commentViewLinkEl.addEventListener('click', (event) => {
            event.preventDefault();
            commentsViewEl.hidden = !commentsViewEl.hidden;
            syncCommentAreaDivider();
            renderCommentViewLink();
            if (!commentsViewEl.hidden) {
                scrollIntoViewIfNeeded(commentsViewEl);
                void loadComments(1);
            }
        });
        extraActionsEl.appendChild(commentViewLinkEl);

        const commentWriteLinkEl = createInlineAction(useCompactPostMenu ? '댓글작성' : '댓글작성(C)', 'c');
        commentWriteLinkEl.addEventListener('click', (event) => {
            event.preventDefault();
            commentFormEl.hidden = !commentFormEl.hidden;
            syncCommentAreaDivider();
            if (!commentFormEl.hidden) {
                scrollIntoViewIfNeeded(commentFormEl);
                focusIfAllowed(commentTextareaEl);
            }
        });
        extraActionsEl.appendChild(commentWriteLinkEl);

        if (canEdit) {
            extraActionsEl.appendChild(
                createActionLink('수정', `/boards/${encodeURIComponent(boardTitle)}/modifyPost?postNum=${encodeURIComponent(postNum)}`, {
                    className: 'pull btn btn-right cancel-btn',
                }),
            );

            const formEl = document.createElement('form');
            formEl.action = `/boards/${encodeURIComponent(boardTitle)}/deletePost`;
            formEl.method = 'post';
            formEl.onsubmit = () => window.confirm('정말 이 글을 삭제할까요?');

            const postNumInputEl = document.createElement('input');
            postNumInputEl.type = 'hidden';
            postNumInputEl.name = 'postNum';
            postNumInputEl.value = String(postNum);
            formEl.appendChild(postNumInputEl);

            const writerInputEl = document.createElement('input');
            writerInputEl.type = 'hidden';
            writerInputEl.name = 'writer';
            writerInputEl.value = String(post.writer ?? '');
            formEl.appendChild(writerInputEl);

            const deleteButtonEl = document.createElement('button');
            deleteButtonEl.type = 'submit';
            deleteButtonEl.className = 'pull btn btn-right cancel-btn';
            deleteButtonEl.style.height = 'auto';
            deleteButtonEl.textContent = '삭제';
            formEl.appendChild(deleteButtonEl);

            extraActionsEl.appendChild(formEl);
        }

        extraActionsEl.appendChild(
            createActionLink(useCompactPostMenu ? '홈' : '초기화면(N)', '/', {
                className: 'pull btn btn-right cancel-btn',
                accessKey: 'n',
            }),
        );

        const commentsDividerEl = createDivider();
        commentsDividerEl.hidden = true;

        function syncCommentAreaDivider() {
            commentsDividerEl.hidden = commentsViewEl.hidden && commentFormEl.hidden;
        }

        syncCommentAreaDivider();

        appendFeedAdAndLatestPosts(itemEl);

        itemEl.appendChild(titleEl);
        itemEl.appendChild(infoEl);
        itemEl.appendChild(createDivider());
        itemEl.appendChild(contentEl);
        itemEl.appendChild(createDivider());
        itemEl.appendChild(actionsEl);
        itemEl.appendChild(commentsDividerEl);
        itemEl.appendChild(commentsEl);
        itemEl.appendChild(createDivider());

        feedListEl.appendChild(itemEl);
        activateTerminalAccessKeys(itemEl);
        terminalEl.scrollIntoView({ block: 'end' });
    }

    function appendEntry(metaLines, contentHtml) {
        const entryEl = document.createElement('div');
        entryEl.className = 'sc-terminal__entry';

        (metaLines || []).forEach((line) => {
            const metaEl = document.createElement('div');
            metaEl.className = 'sc-terminal__meta';
            metaEl.textContent = line;
            entryEl.appendChild(metaEl);
        });

        if (contentHtml) {
            const contentEl = document.createElement('div');
            contentEl.className = 'sc-terminal__content';
            contentEl.innerHTML = contentHtml;
            entryEl.appendChild(contentEl);
        }

        outputEl.appendChild(entryEl);
        openTerminal();
        scrollOutputToBottom();
        return entryEl;
    }

    function appendSystemMessage(message) {
        const feedListEl = ensureFeedListEl();
        if (feedListEl) {
            enableFeedMode();

            const itemEl = document.createElement('article');
            itemEl.className = 'sc-feed__item sc-feed__system-item';

            const titleEl = document.createElement('div');
            titleEl.className = 'sc-feed__title';
            titleEl.textContent = '[SYSTEM]';

            const contentEl = document.createElement('div');
            contentEl.className = 'sc-feed__content sc-feed__system-message';
            contentEl.innerHTML = sanitizeHtml(message);

            itemEl.appendChild(titleEl);
            itemEl.appendChild(contentEl);

            feedListEl.appendChild(itemEl);
            activateTerminalAccessKeys(itemEl);
            terminalEl.scrollIntoView({ block: 'end' });
            return;
        }

        appendEntry(['[SYSTEM]'], sanitizeHtml(message));
    }

    function ensureTerminalNoticeInFeed(feedListEl) {
        if (!feedListEl) {
            return;
        }

        if (document.getElementById('scTerminalNoticeFeed')) {
            return;
        }

        const noticeEl = document.querySelector('.sc-terminal__notice');
        if (!noticeEl) {
            return;
        }

        const itemEl = document.createElement('article');
        itemEl.id = 'scTerminalNoticeFeed';
        itemEl.className = 'sc-feed__item sc-feed__system-item sc-feed__terminal-notice';

        const titleEl = document.createElement('div');
        titleEl.className = 'sc-feed__title';
        titleEl.textContent = '[SYSTEM]';

        const contentEl = document.createElement('div');
        contentEl.className = 'sc-feed__content sc-feed__terminal-notice-text';
        contentEl.textContent = noticeEl.textContent || '';

        itemEl.appendChild(titleEl);
        itemEl.appendChild(contentEl);

        feedListEl.insertBefore(itemEl, feedListEl.firstChild);
    }

    function isBoardUrl(urlOrPath) {
        try {
            const url = new URL(urlOrPath, window.location.origin);
            if (!url.pathname.startsWith('/boards/')) {
                return false;
            }
            const parts = url.pathname.split('/').filter(Boolean);
            if (parts.length !== 2) {
                return false;
            }
            if (parts[0] !== 'boards') {
                return false;
            }
            const boardTitle = parts[1];
            if (boardTitle === 'boardList' || boardTitle === 'showLatestPosts') {
                return false;
            }
            return Boolean(boardTitle);
        } catch (e) {
            return false;
        }
    }

    function parseBoardUrl(urlOrPath) {
        try {
            const url = new URL(urlOrPath, window.location.origin);
            const parts = url.pathname.split('/').filter(Boolean);
            if (parts.length !== 2 || parts[0] !== 'boards') {
                return null;
            }
            const boardTitle = parts[1];
            if (!boardTitle) {
                return null;
            }
            if (boardTitle === 'boardList' || boardTitle === 'showLatestPosts') {
                return null;
            }
            const recentPage = Number.parseInt(url.searchParams.get('recentPage') || '', 10);
            return { boardTitle, recentPage: Number.isNaN(recentPage) ? null : recentPage };
        } catch (e) {
            return null;
        }
    }

    function isPostUrl(urlOrPath) {
        try {
            const url = new URL(urlOrPath, window.location.origin);
            if (!url.pathname.startsWith('/boards/')) {
                return false;
            }
            const parts = url.pathname.split('/').filter(Boolean);
            if (parts.length < 3) {
                return false;
            }
            return parts[0] === 'boards' && parts[2] === 'readPost' && url.searchParams.has('postNum');
        } catch (e) {
            return false;
        }
    }

    function parsePostUrl(urlOrPath) {
        try {
            const url = new URL(urlOrPath, window.location.origin);
            const parts = url.pathname.split('/').filter(Boolean);
            if (parts.length < 3 || parts[0] !== 'boards' || parts[2] !== 'readPost') {
                return null;
            }
            const boardTitle = parts[1];
            const postNum = Number.parseInt(url.searchParams.get('postNum') || '', 10);
            if (!boardTitle || Number.isNaN(postNum)) {
                return null;
            }
            return { boardTitle, postNum };
        } catch (e) {
            return null;
        }
    }

    async function fetchBoardListData(boardTitle, recentPage) {
        const params = new URLSearchParams();
        if (recentPage) {
            params.set('recentPage', String(recentPage));
        }
        const url = `/boards/${encodeURIComponent(boardTitle)}/listData${params.toString() ? `?${params}` : ''}`;
        const response = await fetch(url, {
            method: 'GET',
            headers: { Accept: 'application/json' },
        });
        if (!response.ok) {
            throw new Error(`Failed to load board list: ${response.status}`);
        }
        return response.json();
    }

    async function fetchPostData(boardTitle, postNum) {
        const url = `/boards/${encodeURIComponent(boardTitle)}/postData?postNum=${encodeURIComponent(postNum)}`;
        const response = await fetch(url, {
            method: 'GET',
            headers: { Accept: 'application/json' },
        });
        if (!response.ok) {
            throw new Error(`Failed to load post: ${response.status}`);
        }
        return response.json();
    }

    function createFeedSidebar() {
        const sidebarEl = document.createElement('div');
        sidebarEl.className = 'sc-feed__sidebar';

        function createFieldset(titleText, items) {
            const fieldsetEl = document.createElement('fieldset');
            const legendEl = document.createElement('legend');
            legendEl.textContent = titleText;
            fieldsetEl.appendChild(legendEl);

            const listEl = document.createElement('ul');
            (items || []).forEach((item) => {
                const liEl = document.createElement('li');
                const anchorEl = document.createElement('a');
                anchorEl.href = item.href;
                anchorEl.textContent = item.label;
                if (item.accessKey) {
                    anchorEl.setAttribute('data-sc-accesskey', item.accessKey);
                }
                if (item.style) {
                    anchorEl.setAttribute('style', item.style);
                }
                liEl.appendChild(anchorEl);
                listEl.appendChild(liEl);
            });
            fieldsetEl.appendChild(listEl);
            return fieldsetEl;
        }

        sidebarEl.appendChild(
            createFieldset('[ 지휘관회의실 ]', [
                { href: '/', label: '0. 초기화면(N)', accessKey: 'n' },
                { href: '/boards/noticeBoard', label: '1. 공지사항' },
                { href: '/boards/videoLinkBoard', label: '2. 영상자료실' },
                { href: '/boards/promotionBoard', label: '3. 추천 및 홍보' },
                { href: '/boards/funBoard', label: '4. 꿀잼놀이터' },
            ]),
        );

        sidebarEl.appendChild(
            createFieldset('[ 공략게시판 ]', [
                { href: '/boards/tVsZBoard', label: '10. 테저전 게시판', style: 'color:#00FFFF;' },
                { href: '/boards/tVsPBoard', label: '11. 테프전 게시판', style: 'color:#00FFFF;' },
                { href: '/boards/tVsTBoard', label: '12. 테테전 게시판', style: 'color:#00FFFF;' },
                { href: '/boards/zVsTBoard', label: '13. 저테전 게시판', style: 'color:#ea40f7;' },
                { href: '/boards/zVsPBoard', label: '14. 저프전 게시판', style: 'color:#ea40f7;' },
                { href: '/boards/zVsZBoard', label: '15. 저저전 게시판', style: 'color:#ea40f7;' },
                { href: '/boards/pVsTBoard', label: '16. 프테전 게시판', style: 'color:#FFFF00' },
                { href: '/boards/pVsZBoard', label: '17. 프저전 게시판', style: 'color:#FFFF00' },
                { href: '/boards/pVsPBoard', label: '18. 프프전 게시판', style: 'color:#FFFF00' },
                { href: '/boards/teamPlayGuideBoard', label: '19. 팀플 게시판' },
                { href: '/boards/tipBoard', label: '20. 꿀팁보급고(T)', accessKey: 't' },
            ]),
        );

        return sidebarEl;
    }

    function appendBoardListToFeed(boardTitle, koreanTitle, page, selfNoticeList, postList, canWrite) {
        const feedListEl = ensureFeedListEl();
        if (!feedListEl) {
            appendSystemMessage('피드 영역을 찾을 수 없습니다.');
            return;
        }

        const itemEl = document.createElement('article');
        itemEl.className = 'sc-feed__item';

        const titleEl = document.createElement('div');
        titleEl.className = 'sc-feed__title';
        titleEl.textContent = `[${koreanTitle ?? boardTitle}]`;

        const metaEl = document.createElement('div');
        metaEl.className = 'sc-feed__meta';
        const recentPage = page?.recentPage ?? 1;
        const totalPage = page?.totalPage ?? 1;
        const totalPostCount = page?.totalPostCount ?? 0;
        metaEl.textContent = `페이지: ${recentPage}/${totalPage} | 총 ${totalPostCount}개`;

        const tableEl = document.createElement('table');
        tableEl.className = 'sc-feed__table';

        const theadEl = document.createElement('thead');
        const headerRowEl = document.createElement('tr');
        const headerCells = [
            { text: '번 호', className: 'post-num hide-on-mobile', width: '10%' },
            { text: '제 목', className: 'title', width: '56%' },
            { text: '작성자', className: 'writer hide-on-mobile', width: '10%' },
            { text: '날 짜', className: 'date hide-on-mobile', width: '10%' },
            { text: '조회', className: 'views hide-on-mobile', width: '7%' },
            { text: '추천', className: 'recommend hide-on-mobile', width: '7%' },
        ];
        headerCells.forEach((cell) => {
            const thEl = document.createElement('th');
            thEl.textContent = cell.text;
            thEl.className = cell.className;
            thEl.style.width = cell.width;
            headerRowEl.appendChild(thEl);
        });
        theadEl.appendChild(headerRowEl);
        tableEl.appendChild(theadEl);

        const tbodyEl = document.createElement('tbody');

        function appendRow(post, isNotice) {
            const trEl = document.createElement('tr');
            if (isNotice) {
                trEl.className = 'notice-tr';
            }

            const numTdEl = document.createElement('td');
            numTdEl.className = 'post-num hide-on-mobile';
            numTdEl.textContent = `${post.postNum ?? ''}`;

            const titleTdEl = document.createElement('td');
            titleTdEl.className = 'title';
            if (isNotice) {
                const prefixEl = document.createElement('span');
                prefixEl.textContent = '[공지] ';
                titleTdEl.appendChild(prefixEl);
            }

            const linkEl = document.createElement('a');
            linkEl.href = `/boards/${encodeURIComponent(boardTitle)}/readPost?postNum=${encodeURIComponent(post.postNum)}`;
            linkEl.textContent = post.title ?? '';
            titleTdEl.appendChild(linkEl);

            if ((post.commentCount ?? 0) > 0) {
                titleTdEl.appendChild(document.createTextNode(` ( ${post.commentCount} )`));
            }

            const writerTdEl = document.createElement('td');
            writerTdEl.className = 'writer hide-on-mobile';
            writerTdEl.textContent = post.writer ?? '';

            const dateTdEl = document.createElement('td');
            dateTdEl.className = 'date hide-on-mobile';
            dateTdEl.textContent = formatMmDd(post.regDate);

            const viewsTdEl = document.createElement('td');
            viewsTdEl.className = 'views hide-on-mobile';
            viewsTdEl.textContent = String(post.views ?? '');

            const recommendTdEl = document.createElement('td');
            recommendTdEl.className = 'recommend hide-on-mobile';
            recommendTdEl.textContent = String(post.recommendCount ?? '');

            trEl.appendChild(numTdEl);
            trEl.appendChild(titleTdEl);
            trEl.appendChild(writerTdEl);
            trEl.appendChild(dateTdEl);
            trEl.appendChild(viewsTdEl);
            trEl.appendChild(recommendTdEl);
            tbodyEl.appendChild(trEl);
        }

        (selfNoticeList || []).forEach((notice) => appendRow(notice, true));
        (postList || []).forEach((post) => appendRow(post, false));

        tableEl.appendChild(tbodyEl);

        const actionsEl = document.createElement('div');
        actionsEl.className = 'sc-feed__actions';

        const baseBoardUrl = `/boards/${encodeURIComponent(boardTitle)}/`;
        if (page?.prevPageSetPoint >= 1) {
            actionsEl.appendChild(
                createActionLink('[이전]', `${baseBoardUrl}?recentPage=${encodeURIComponent(page.prevPageSetPoint)}`, {
                    className: 'btn btn-theme',
                }),
            );
        }

        if ((page?.totalPage ?? 0) > 1) {
            for (let countPage = page.pageBeginPoint; countPage <= page.pageEndPoint; countPage += 1) {
                actionsEl.appendChild(
                    createActionLink(`[${countPage}]`, `${baseBoardUrl}?recentPage=${encodeURIComponent(countPage)}`, {
                        className: `btn btn-theme${countPage === page.recentPage ? ' is-current' : ''}`,
                    }),
                );
            }
        }

        if (page?.nextPageSetPoint && page.nextPageSetPoint <= page.totalPage) {
            actionsEl.appendChild(
                createActionLink('[다음]', `${baseBoardUrl}?recentPage=${encodeURIComponent(page.nextPageSetPoint)}`, {
                    className: 'btn btn-theme',
                }),
            );
        }

        if (canWrite) {
            actionsEl.appendChild(
                createActionLink('글쓰기(I)', `/boards/${encodeURIComponent(boardTitle)}/writePost`, {
                    className: 'pull-right btn btn-theme',
                    accessKey: 'i',
                }),
            );
        }

        const layoutEl = document.createElement('div');
        layoutEl.className = 'sc-row sc-feed__board-layout';

        const sidebarColEl = document.createElement('div');
        sidebarColEl.className = 'sc-col-3';
        sidebarColEl.appendChild(createFeedSidebar());

        const contentColEl = document.createElement('div');
        contentColEl.className = 'sc-col-9';
        contentColEl.appendChild(tableEl);
        contentColEl.appendChild(createDivider());
        contentColEl.appendChild(actionsEl);

        layoutEl.appendChild(sidebarColEl);
        layoutEl.appendChild(contentColEl);

        appendFeedAdAndLatestPosts(itemEl);
        itemEl.appendChild(titleEl);
        itemEl.appendChild(metaEl);
        itemEl.appendChild(createDivider());
        itemEl.appendChild(layoutEl);
        itemEl.appendChild(createDivider());

        feedListEl.appendChild(itemEl);
        activateTerminalAccessKeys(itemEl);
        terminalEl.scrollIntoView({ block: 'end' });
    }

    async function openPost(boardTitle, postNum) {
        if (!feedEnabled) {
            const url = `/boards/${encodeURIComponent(boardTitle)}/readPost?postNum=${encodeURIComponent(postNum)}`;
            window.location.href = url;
            return;
        }

        enableFeedMode();
        try {
            const boardDisplayName = await getBoardDisplayName(boardTitle);
            const post = await fetchPostData(boardTitle, postNum);
            appendPostToFeed(boardTitle, boardDisplayName, postNum, post);
            focusIfAllowed(inputEl);
        } catch (e) {
            appendSystemMessage(`게시물을 불러오지 못했습니다. (${boardTitle} / ${postNum})`);
        }
    }

    async function openBoardList(boardTitle, recentPage) {
        if (!feedEnabled) {
            const query = recentPage ? `?recentPage=${encodeURIComponent(recentPage)}` : '';
            const url = `/boards/${encodeURIComponent(boardTitle)}${query}`;
            window.location.href = url;
            return;
        }

        enableFeedMode();
        try {
            const data = await fetchBoardListData(boardTitle, recentPage);
            appendBoardListToFeed(
                data.boardTitle ?? boardTitle,
                data.koreanTitle,
                data.page,
                data.selfNoticeList,
                data.postList,
                data.canWrite,
            );
            focusIfAllowed(inputEl);
        } catch (e) {
            appendSystemMessage(`게시판 목록을 불러오지 못했습니다. (${boardTitle})`);
        }
    }

    function openPostFromUrl(urlOrPath) {
        const parsed = parsePostUrl(urlOrPath);
        if (!parsed) {
            return false;
        }
        if (!feedEnabled) {
            const url = `/boards/${encodeURIComponent(parsed.boardTitle)}/readPost?postNum=${encodeURIComponent(parsed.postNum)}`;
            window.location.href = url;
            return true;
        }
        void openPost(parsed.boardTitle, parsed.postNum);
        return true;
    }

    function openBoardFromUrl(urlOrPath) {
        const parsed = parseBoardUrl(urlOrPath);
        if (!parsed) {
            return false;
        }
        if (!feedEnabled) {
            const query = parsed.recentPage ? `?recentPage=${encodeURIComponent(parsed.recentPage)}` : '';
            const url = `/boards/${encodeURIComponent(parsed.boardTitle)}${query}`;
            window.location.href = url;
            return true;
        }
        void openBoardList(parsed.boardTitle, parsed.recentPage);
        return true;
    }

    function runCommand(raw) {
        const value = (raw || '').trim();
        if (!value) {
            return false;
        }

        const tokens = value.split(/\s+/);
        const command = tokens[0].toLowerCase();

        if (command === 'help') {
            appendSystemMessage('명령어: help, clear, close, open <url>, read <boardTitle> <postNum>, ask <question>');
            return true;
        }

        if (command === 'clear') {
            outputEl.innerHTML = '';
            openTerminal();
            return true;
        }

        if (command === 'close') {
            closeTerminal();
            return true;
        }

        if (command === 'open' && tokens.length >= 2) {
            if (openPostFromUrl(tokens[1])) {
                return true;
            }
            if (openBoardFromUrl(tokens[1])) {
                return true;
            }
            return false;
        }

        if (command === 'read' && tokens.length >= 3) {
            const boardTitle = tokens[1];
            const postNum = Number.parseInt(tokens[2], 10);
            if (!boardTitle || Number.isNaN(postNum)) {
                appendSystemMessage('사용법: read <boardTitle> <postNum>');
                return true;
            }
            void openPost(boardTitle, postNum);
            return true;
        }

        if (command === 'ask' || command === 'ai') {
            const question = value.slice(tokens[0].length).trim();
            if (!question) {
                appendSystemMessage('사용법: ask <question>');
                return true;
            }
            void askAssistant(question);
            return true;
        }

        return false;
    }

    async function askAssistant(question) {
        const trimmed = String(question ?? '').trim();
        if (!trimmed) {
            appendSystemMessage('질문을 입력해주세요.');
            return;
        }
        if (trimmed.length > 800) {
            appendSystemMessage('질문이 너무 깁니다. 조금만 짧게 입력해주세요.');
            return;
        }

        const feedListEl = ensureFeedListEl();

        let metaEl = null;
        let answerEl = null;
        let relatedEl = null;
        let pendingContentEl = null;

        if (feedListEl) {
            enableFeedMode();
            const itemEl = document.createElement('article');
            itemEl.className = 'sc-feed__item sc-feed__chat-item';

            const titleEl = document.createElement('div');
            titleEl.className = 'sc-feed__title';
            titleEl.textContent = '[AI]';

            metaEl = document.createElement('div');
            metaEl.className = 'sc-feed__meta';
            metaEl.textContent = '';

            const questionEl = document.createElement('div');
            questionEl.className = 'sc-feed__content sc-feed__chat-question';
            questionEl.innerHTML = `<div><strong>Q.</strong> ${escapeHtml(trimmed)}</div>`;

            answerEl = document.createElement('div');
            answerEl.className = 'sc-feed__content sc-feed__chat-answer';
            answerEl.innerHTML = `<div><strong>A.</strong> ${escapeHtml('답변 생성 중...')}</div>`;

            relatedEl = document.createElement('div');
            relatedEl.className = 'sc-feed__content sc-feed__chat-related';
            relatedEl.hidden = true;

            appendFeedAdAndLatestPosts(itemEl);
            itemEl.appendChild(titleEl);
            itemEl.appendChild(metaEl);
            itemEl.appendChild(questionEl);
            itemEl.appendChild(answerEl);
            itemEl.appendChild(relatedEl);

            feedListEl.appendChild(itemEl);
            activateTerminalAccessKeys(itemEl);
            terminalEl.scrollIntoView({ block: 'end' });
        } else {
            appendEntry(['[YOU]'], escapeHtml(trimmed));
            const pendingEntryEl = appendEntry(['[AI]'], escapeHtml('답변 생성 중...'));
            pendingContentEl = pendingEntryEl.querySelector('.sc-terminal__content');
        }

        try {
            const response = await fetch('/api/assistant/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
                body: JSON.stringify({ message: trimmed }),
            });

            const data = await response.json().catch(() => ({}));
            if (!response.ok) {
                const errorMessage = data?.error ? String(data.error) : `AI 요청에 실패했습니다. (${response.status})`;
                const usageText = data?.usageText ? String(data.usageText) : '';
                if (metaEl && data?.usageText) {
                    metaEl.textContent = String(data.usageText);
                }
                if (answerEl) {
                    answerEl.innerHTML = `<div><strong>A.</strong> ${escapeHtml(errorMessage)}</div>`;
                } else if (pendingContentEl) {
                    const usageHtml = usageText ? `<div>${escapeHtml(usageText)}</div>` : '';
                    pendingContentEl.innerHTML = usageHtml + escapeHtml(errorMessage);
                } else {
                    appendSystemMessage(errorMessage);
                }
                return;
            }

            const answer = data?.answer ? String(data.answer) : '';
            const related = Array.isArray(data?.relatedPosts) ? data.relatedPosts : [];
            const usageText = data?.usageText ? String(data.usageText) : '';

            if (metaEl && usageText) {
                metaEl.textContent = usageText;
            }

            if (answerEl) {
                const answerText = answer || '답변을 생성하지 못했습니다.';
                answerEl.innerHTML = `<div><strong>A.</strong> ${escapeHtml(answerText).replace(/\\n/g, '<br>')}</div>`;
            } else if (pendingContentEl) {
                const usageHtml = usageText ? `<div>${escapeHtml(usageText)}</div>` : '';
                const answerHtml = escapeHtml(answer || '답변을 생성하지 못했습니다.').replace(/\\n/g, '<br>');
                pendingContentEl.innerHTML = usageHtml + answerHtml;
            }

            if (relatedEl) {
                if (!related.length) {
                    relatedEl.hidden = true;
                } else {
                    const boardNameCache = new Map();
                    const itemsHtml = await Promise.all(
                        related.map(async (post) => {
                            const boardTitle = String(post?.boardTitle ?? '');
                            const postNum = Number.parseInt(String(post?.postNum ?? ''), 10);
                            const title = String(post?.title ?? '');
                            const url = String(post?.url ?? '');

                            if (!boardTitle || Number.isNaN(postNum)) {
                                return '';
                            }

                            let boardDisplayName = boardTitle;
                            if (boardNameCache.has(boardTitle)) {
                                boardDisplayName = boardNameCache.get(boardTitle);
                            } else {
                                boardDisplayName = await getBoardDisplayName(boardTitle);
                                boardNameCache.set(boardTitle, boardDisplayName);
                            }

                            const href = url || `/boards/${encodeURIComponent(boardTitle)}/readPost?postNum=${encodeURIComponent(
                                postNum,
                            )}`;
                            const label = `[${boardDisplayName}] ${postNum}번 | ${title || '제목 없음'}`;
                            return `<li><a href="${escapeHtml(href)}">${escapeHtml(label)}</a></li>`;
                        }),
                    );

                    const filteredItems = itemsHtml.filter(Boolean);
                    if (!filteredItems.length) {
                        relatedEl.hidden = true;
                    } else {
                        relatedEl.hidden = false;
                        relatedEl.innerHTML = `<div>${escapeHtml('[관련 게시물]')}</div><ol>${filteredItems.join('')}</ol>`;
                    }
                }
            }
        } catch (e) {
            const errorMessage = 'AI 응답을 불러오지 못했습니다.';
            if (answerEl) {
                answerEl.innerHTML = `<div><strong>A.</strong> ${escapeHtml(errorMessage)}</div>`;
                return;
            }
            if (pendingContentEl) {
                pendingContentEl.innerHTML = escapeHtml(errorMessage);
                return;
            }
            appendSystemMessage(errorMessage);
        }
    }

    function shouldBypassClick(event) {
        return (
            event.defaultPrevented ||
            !event.isTrusted ||
            event.button !== 0 ||
            event.metaKey ||
            event.ctrlKey ||
            event.shiftKey ||
            event.altKey
        );
    }

    if (feedEnabled) {
        document.addEventListener('click', (event) => {
            if (shouldBypassClick(event)) {
                return;
            }

            const anchor = event.target.closest('a[href]');
            if (!anchor) {
                return;
            }

            if (anchor.hasAttribute('data-sc-terminal-bypass') || anchor.getAttribute('target') === '_blank') {
                return;
            }

            const rawHref = anchor.getAttribute('href') || '';
            if (!rawHref || rawHref.startsWith('javascript:') || rawHref.startsWith('#')) {
                return;
            }

            if (!isPostUrl(anchor.href)) {
                if (!isBoardUrl(anchor.href)) {
                    return;
                }
                event.preventDefault();
                openBoardFromUrl(anchor.href);
                return;
            }

            event.preventDefault();
            openPostFromUrl(anchor.href);
        });
    }

    terminalEl.addEventListener('mousedown', (event) => {
        const interactiveEl = event.target?.closest?.('input, textarea, select, button, a');
        if (interactiveEl) {
            return;
        }
        focusIfAllowed(inputEl);
    });

    window.scTerminal = {
        isPostUrl,
        isBoardUrl,
        openPostFromUrl,
        openBoardFromUrl,
        runCommand,
        ask: (question) => askAssistant(question),
    };
})();
