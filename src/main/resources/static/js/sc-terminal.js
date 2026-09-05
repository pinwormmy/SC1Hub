(() => {
    const terminalEl = document.getElementById('scTerminal');
    if (!terminalEl) {
        return;
    }

    let titleSlideRafId = 0;

    const outputEl = document.getElementById('scTerminalOutput');
    const inputEl = document.getElementById('scTerminalInput');
    const mirrorEl = document.getElementById('scTerminalMirror');

    if (!outputEl || !inputEl) {
        return;
    }

    function prepareTitleSlide(slideEl) {
        if (!slideEl || slideEl.dataset.scTitlePrepared === 'true') {
            return;
        }

        const text = slideEl.textContent ?? '';
        slideEl.textContent = '';

        const innerEl = document.createElement('span');
        innerEl.className = 'sc-title-slide__inner';

        const itemEl = document.createElement('span');
        itemEl.className = 'sc-title-slide__item';
        itemEl.textContent = text;

        const cloneEl = document.createElement('span');
        cloneEl.className = 'sc-title-slide__item';
        cloneEl.textContent = text;
        cloneEl.setAttribute('aria-hidden', 'true');

        innerEl.append(itemEl, cloneEl);
        slideEl.appendChild(innerEl);
        slideEl.dataset.scTitlePrepared = 'true';
    }

    function updateTitleSlideOverflow(rootEl = document) {
        const slideEls = Array.from(rootEl.querySelectorAll('.sc-title-slide'));
        if (slideEls.length === 0) {
            return;
        }
        // 쓰기(마크업 준비) → 읽기(폭 측정) → 쓰기(클래스 토글)를 단계별로 모아,
        // 제목 수만큼 강제 리플로우가 반복되던 것을 한 번으로 줄인다(초기화면 30여 개).
        slideEls.forEach(prepareTitleSlide);
        const measurements = slideEls.map((slideEl) => ({
            slideEl,
            containerEl: slideEl.closest('.sc-title-cell') || slideEl.closest('.title'),
            isOverflowing: slideEl.scrollWidth > slideEl.clientWidth + 1,
        }));
        measurements.forEach(({ slideEl, containerEl, isOverflowing }) => {
            if (!containerEl) {
                return;
            }
            containerEl.classList.toggle('is-overflowing', isOverflowing);
            slideEl.classList.toggle('is-marquee', isOverflowing);
        });
    }

    window.scUpdateTitleSlides = (rootEl) => {
        updateTitleSlideOverflow(rootEl && typeof rootEl.querySelectorAll === 'function' ? rootEl : document);
    };

    function runScheduledTitleSlideUpdate() {
        titleSlideRafId = 0;
        updateTitleSlideOverflow();
    }

    function scheduleTitleSlideUpdate() {
        if (titleSlideRafId) {
            cancelAnimationFrame(titleSlideRafId);
        }
        titleSlideRafId = requestAnimationFrame(runScheduledTitleSlideUpdate);
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

    function openTerminal() {
        terminalEl.classList.remove(COLLAPSED_CLASS);
    }

    function closeTerminal() {
        terminalEl.classList.add(COLLAPSED_CLASS);
    }

    const FULLSCREEN_CLASS = 'sc-chat-fullscreen';
    const expandButtonEl = document.getElementById('scTerminalExpandBtn');
    function setTerminalExpanded(expanded) {
        openTerminal();
        document.body.classList.toggle(FULLSCREEN_CLASS, expanded);
        if (expandButtonEl) {
            expandButtonEl.textContent = expanded ? '채팅창 축소' : '채팅창 확장';
            expandButtonEl.setAttribute('aria-expanded', expanded ? 'true' : 'false');
        }
        window.dispatchEvent(new CustomEvent('sc:chat-expanded', {
            detail: { expanded },
        }));
        outputEl.scrollTop = outputEl.scrollHeight;
    }

    if (expandButtonEl) {
        expandButtonEl.addEventListener('click', () => {
            setTerminalExpanded(!document.body.classList.contains(FULLSCREEN_CLASS));
        });
    }
    document.querySelectorAll('[data-chat-open]').forEach((link) => {
        link.addEventListener('click', (event) => {
            event.preventDefault();
            setTerminalExpanded(true);
        });
    });

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

    async function buildRelatedPostsHtml(related, noticeText) {
        const notice = String(noticeText ?? '').trim();
        if (!Array.isArray(related) || related.length === 0) {
            if (!notice) {
                return '';
            }
            return `<div>${escapeHtml('[관련 게시물]')}</div><div>${escapeHtml(notice)}</div>`;
        }

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

                const href = url || `/boards/${encodeURIComponent(boardTitle)}/readPost?postNum=${encodeURIComponent(postNum)}`;
                const label = `[${boardDisplayName}] ${postNum}번 | ${title || '제목 없음'}`;
                return `<li><a href="${escapeHtml(href)}" data-google-vignette="false">${escapeHtml(label)}</a></li>`;
            }),
        );

        const filteredItems = itemsHtml.filter(Boolean);
        if (!filteredItems.length) {
            return '';
        }

        const noticeHtml = notice ? `<div>${escapeHtml(notice)}</div>` : '';
        return `<div>${escapeHtml('[관련 게시물]')}</div><ol>${filteredItems.join('')}</ol>${noticeHtml}`;
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
        appendEntry(['[SYSTEM]'], sanitizeHtml(message));
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

    function openPost(boardTitle, postNum) {
        const url = `/boards/${encodeURIComponent(boardTitle)}/readPost?postNum=${encodeURIComponent(postNum)}`;
        window.location.href = url;
    }

    function openPostFromUrl(urlOrPath) {
        const parsed = parsePostUrl(urlOrPath);
        if (!parsed) {
            return false;
        }
        const url = `/boards/${encodeURIComponent(parsed.boardTitle)}/readPost?postNum=${encodeURIComponent(parsed.postNum)}`;
        window.location.href = url;
        return true;
    }

    function openBoardFromUrl(urlOrPath) {
        const parsed = parseBoardUrl(urlOrPath);
        if (!parsed) {
            return false;
        }
        const query = parsed.recentPage ? `?recentPage=${encodeURIComponent(parsed.recentPage)}` : '';
        const url = `/boards/${encodeURIComponent(parsed.boardTitle)}${query}`;
        window.location.href = url;
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
            const memberMeta = getMemberMeta();
            const isAdmin = memberMeta.grade === 3;
            const base = '명령어: help, clear, close, open <url>, read <boardTitle> <postNum>, ask <question>';
            const adminExtra = ', index status, index reindex, index update';
            appendSystemMessage(isAdmin ? base + adminExtra : base);
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

        if (command === 'index' && tokens.length >= 2) {
            const memberMeta = getMemberMeta();
            if (memberMeta.grade !== 3) {
                appendSystemMessage('관리자만 실행할 수 있습니다.');
                return true;
            }
            const action = String(tokens[1] || '').toLowerCase();
            if (action === 'status') {
                void runIndexStatus();
                return true;
            }
            if (action === 'reindex') {
                void runIndexReindex();
                return true;
            }
            if (action === 'update') {
                void runIndexUpdate();
                return true;
            }
            appendSystemMessage('사용법: index status | index reindex | index update');
            return true;
        }

        if (command === 'rag' && tokens.length >= 2) {
            const memberMeta = getMemberMeta();
            if (memberMeta.grade !== 3) {
                appendSystemMessage('관리자만 실행할 수 있습니다.');
                return true;
            }
            const action = String(tokens[1] || '').toLowerCase();
            if (action === 'reindex') {
                const sync = String(tokens[2] || '').toLowerCase() === 'sync';
                appendSystemMessage('안내: rag reindex는 index reindex로 통합되었습니다.');
                void runIndexReindex();
                return true;
            }
            if (action === 'update') {
                appendSystemMessage('안내: rag update는 index update로 통합되었습니다.');
                void runIndexUpdate();
                return true;
            }
            appendSystemMessage('사용법: index status | index reindex | index update');
            return true;
        }

        if ((command === 'searchterms' || command === 'search-terms') && tokens.length >= 2) {
            const memberMeta = getMemberMeta();
            if (memberMeta.grade !== 3) {
                appendSystemMessage('관리자만 실행할 수 있습니다.');
                return true;
            }
            const action = String(tokens[1] || '').toLowerCase();
            if (action === 'reindex') {
                appendSystemMessage('안내: searchterms reindex는 index reindex로 통합되었습니다.');
                void runIndexReindex();
                return true;
            }
            appendSystemMessage('사용법: index status | index reindex | index update');
            return true;
        }

        return false;
    }

    async function postJson(url) {
        const response = await fetch(url, {
            method: 'POST',
            headers: { Accept: 'application/json' },
            credentials: 'include',
        });
        const contentType = response.headers.get('content-type') || '';
        const bodyText = await response.text().catch(() => '');
        if (!contentType.includes('application/json')) {
            return { ok: response.ok, status: response.status, data: null, text: bodyText };
        }
        const data = bodyText ? JSON.parse(bodyText) : null;
        return { ok: response.ok, status: response.status, data, text: bodyText };
    }

    async function getJson(url) {
        const response = await fetch(url, {
            method: 'GET',
            headers: { Accept: 'application/json' },
            credentials: 'include',
        });
        const contentType = response.headers.get('content-type') || '';
        const bodyText = await response.text().catch(() => '');
        if (!contentType.includes('application/json')) {
            return { ok: response.ok, status: response.status, data: null, text: bodyText };
        }
        const data = bodyText ? JSON.parse(bodyText) : null;
        return { ok: response.ok, status: response.status, data, text: bodyText };
    }

    async function runIndexStatus() {
        try {
            appendSystemMessage('[INDEX] status 조회...');
            const result = await getJson('/api/assistant/index/status');
            if (!result.ok) {
                appendSystemMessage(`[INDEX] status 실패 (${result.status}): ${result.text.slice(0, 500)}`);
                return;
            }
            appendSystemMessage(`[INDEX] status 응답 (${result.status}): ${result.text.slice(0, 500)}`);
        } catch (e) {
            appendSystemMessage('[INDEX] status 실패');
        }
    }

    async function runIndexReindex() {
        try {
            appendSystemMessage('[INDEX] reindex 시작...');
            const result = await postJson('/api/assistant/index/reindex');
            if (!result.ok) {
                appendSystemMessage(`[INDEX] reindex 실패 (${result.status}): ${result.text.slice(0, 500)}`);
                return;
            }
            appendSystemMessage(`[INDEX] reindex 응답 (${result.status}): ${result.text.slice(0, 500)}`);
        } catch (e) {
            appendSystemMessage('[INDEX] reindex 실패');
        }
    }

    async function runIndexUpdate() {
        try {
            appendSystemMessage('[INDEX] update 시작...');
            const result = await postJson('/api/assistant/index/update');
            if (!result.ok) {
                appendSystemMessage(`[INDEX] update 실패 (${result.status}): ${result.text.slice(0, 500)}`);
                return;
            }
            appendSystemMessage(`[INDEX] update 응답 (${result.status}): ${result.text.slice(0, 500)}`);
        } catch (e) {
            appendSystemMessage('[INDEX] update 실패');
        }
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

        let pendingContentEl = null;
        let stopLoadingDots = null;

        function startLoadingDots(targetEl) {
            if (!targetEl) {
                return null;
            }

            const lineEl = document.createElement('div');
            const textEl = document.createElement('span');
            textEl.textContent = '답변 생성 중';

            const dotsEl = document.createElement('span');
            dotsEl.className = 'sc-loading-dots';

            lineEl.append(textEl, dotsEl);
            targetEl.innerHTML = '';
            targetEl.appendChild(lineEl);

            const maxDots = 3;
            let dots = 1;
            dotsEl.textContent = '.';

            const timerId = window.setInterval(() => {
                dots = (dots % maxDots) + 1;
                dotsEl.textContent = '.'.repeat(dots);
            }, 500);

            return () => window.clearInterval(timerId);
        }

        appendEntry(['[YOU]'], escapeHtml(trimmed));
        const pendingEntryEl = appendEntry(['[AI]'], escapeHtml('답변 생성 중'));
        pendingContentEl = pendingEntryEl.querySelector('.sc-terminal__content');
        stopLoadingDots = startLoadingDots(pendingContentEl);

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
                if (pendingContentEl) {
                    const usageHtml = usageText ? `<div>${escapeHtml(usageText)}</div>` : '';
                    pendingContentEl.innerHTML = usageHtml + escapeHtml(errorMessage);
                } else {
                    appendSystemMessage(errorMessage);
                }
                return;
            }

            const answer = data?.answer ? String(data.answer) : '';
            const related = Array.isArray(data?.relatedPosts) ? data.relatedPosts : [];
            const relatedNotice = data?.relatedPostsNotice ? String(data.relatedPostsNotice) : '';
            const usageText = data?.usageText ? String(data.usageText) : '';


            if (pendingContentEl) {
                const usageHtml = usageText ? `<div>${escapeHtml(usageText)}</div>` : '';
                const answerHtml = escapeHtml(answer || '답변을 생성하지 못했습니다.').replace(/\r?\n/g, '<br>');
                pendingContentEl.innerHTML = usageHtml + answerHtml;
            }

            const relatedHtml = await buildRelatedPostsHtml(related, relatedNotice);

            if (pendingContentEl && relatedHtml) {
                pendingContentEl.innerHTML += `<div class="sc-terminal__related">${relatedHtml}</div>`;
            }
        } catch (e) {
            const errorMessage = 'AI 응답을 불러오지 못했습니다.';
            if (pendingContentEl) {
                pendingContentEl.innerHTML = escapeHtml(errorMessage);
                return;
            }
            appendSystemMessage(errorMessage);
        } finally {
            if (stopLoadingDots) {
                stopLoadingDots();
            }
        }
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
