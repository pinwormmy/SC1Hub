(() => {
    const formEl = document.getElementById('scHomeAskForm');
    const inputEl = document.getElementById('scHomeAskInput');
    const submitEl = document.getElementById('scHomeAskSubmit');
    const resultEl = document.getElementById('scHomeAskResult');
    if (!formEl || !inputEl || !submitEl || !resultEl) {
        return;
    }

    const chipButtons = Array.from(document.querySelectorAll('[data-ask-prompt]'));
    const MAX_QUESTION_LENGTH = 800;
    let inFlight = false;
    let boardTitleMapPromise = null;

    function trackEvent(eventName, params) {
        if (typeof window.gtag === 'function') {
            window.gtag('event', eventName, params || {});
        }
    }

    function getBoardTitleMap() {
        if (boardTitleMapPromise) {
            return boardTitleMapPromise;
        }
        boardTitleMapPromise = fetch('/boards/boardList', {
            method: 'GET',
            headers: { Accept: 'application/json' },
        })
            .then((response) => (response.ok ? response.json() : []))
            .then((boards) => {
                const map = new Map();
                if (Array.isArray(boards)) {
                    boards.forEach((board) => {
                        if (board && board.boardTitle && board.koreanTitle) {
                            map.set(String(board.boardTitle).toLowerCase(), String(board.koreanTitle));
                        }
                    });
                }
                return map;
            })
            .catch(() => new Map());
        return boardTitleMapPromise;
    }

    function sanitizeRelatedUrl(rawUrl, fallbackUrl) {
        const trimmed = String(rawUrl ?? '').trim();
        if (!trimmed) {
            return fallbackUrl;
        }
        if ((trimmed.startsWith('/') && !trimmed.startsWith('//')) || /^https?:\/\//i.test(trimmed)) {
            return trimmed;
        }
        return fallbackUrl;
    }

    function setBusy(busy) {
        inFlight = busy;
        submitEl.disabled = busy;
        submitEl.textContent = busy ? '답변 중...' : 'AI 질문';
        chipButtons.forEach((button) => {
            button.disabled = busy;
        });
    }

    function buildHeader(titleText, usageText) {
        const headerEl = document.createElement('div');
        headerEl.className = 'sc-home-ask__header';

        const metaEl = document.createElement('div');
        metaEl.className = 'sc-home-ask__meta';

        const titleEl = document.createElement('strong');
        titleEl.textContent = titleText;
        metaEl.appendChild(titleEl);

        if (usageText) {
            const usageEl = document.createElement('span');
            usageEl.className = 'sc-home-ask__usage';
            usageEl.textContent = usageText;
            metaEl.appendChild(usageEl);
        }

        const closeBtn = document.createElement('button');
        closeBtn.type = 'button';
        closeBtn.className = 'sc-home-ask__close';
        closeBtn.textContent = '닫기';
        closeBtn.addEventListener('click', () => {
            resultEl.hidden = true;
            resultEl.replaceChildren();
        });

        headerEl.append(metaEl, closeBtn);
        return headerEl;
    }

    function renderLoading() {
        const statusEl = document.createElement('p');
        statusEl.className = 'sc-home-ask__status';
        statusEl.textContent = '공략 데이터를 찾아 답변을 작성하는 중입니다...';
        resultEl.replaceChildren(buildHeader('AI 공략 답변', ''), statusEl);
        resultEl.hidden = false;
    }

    function renderError(message, usageText) {
        const errorEl = document.createElement('p');
        errorEl.className = 'sc-home-ask__error';
        errorEl.textContent = message;
        resultEl.replaceChildren(buildHeader('AI 공략 답변', usageText || ''), errorEl);
        resultEl.hidden = false;
    }

    async function buildRelatedSection(relatedPosts, noticeText) {
        const posts = Array.isArray(relatedPosts) ? relatedPosts : [];
        const notice = String(noticeText ?? '').trim();
        if (posts.length === 0 && !notice) {
            return null;
        }

        const boardMap = await getBoardTitleMap();
        const sectionEl = document.createElement('div');
        sectionEl.className = 'sc-home-ask__related';

        const headingEl = document.createElement('h2');
        headingEl.className = 'sc-home-ask__related-title';
        headingEl.textContent = '참고 공략';
        sectionEl.appendChild(headingEl);

        if (posts.length > 0) {
            const listEl = document.createElement('ul');
            listEl.className = 'sc-home-ask__related-list';
            posts.forEach((post) => {
                const boardTitle = String(post?.boardTitle ?? '').trim();
                const postNum = Number.parseInt(String(post?.postNum ?? ''), 10);
                if (!boardTitle || Number.isNaN(postNum)) {
                    return;
                }
                const itemEl = document.createElement('li');

                const badgeEl = document.createElement('span');
                badgeEl.className = 'sc-related-posts__badge';
                badgeEl.textContent = boardMap.get(boardTitle.toLowerCase()) || boardTitle;

                const fallbackHref = `/boards/${encodeURIComponent(boardTitle)}/readPost?postNum=${encodeURIComponent(String(postNum))}`;
                const linkEl = document.createElement('a');
                linkEl.href = sanitizeRelatedUrl(post?.url, fallbackHref);
                linkEl.setAttribute('data-google-vignette', 'false');
                linkEl.textContent = String(post?.title ?? '') || '제목 없음';

                itemEl.append(badgeEl, linkEl);
                listEl.appendChild(itemEl);
            });
            if (listEl.children.length > 0) {
                sectionEl.appendChild(listEl);
            }
        }

        if (notice) {
            const noticeEl = document.createElement('p');
            noticeEl.className = 'sc-home-ask__notice';
            noticeEl.textContent = notice;
            sectionEl.appendChild(noticeEl);
        }

        return sectionEl;
    }

    async function renderSuccess(data) {
        const fragment = document.createDocumentFragment();
        const usageText = data?.usageText ? String(data.usageText) : '';
        fragment.appendChild(buildHeader('AI 공략 답변', usageText));

        const answerEl = document.createElement('p');
        answerEl.className = 'sc-home-ask__answer';
        answerEl.textContent = data?.answer ? String(data.answer) : '답변을 생성하지 못했습니다.';
        fragment.appendChild(answerEl);

        const relatedEl = await buildRelatedSection(data?.relatedPosts, data?.relatedPostsNotice);
        if (relatedEl) {
            fragment.appendChild(relatedEl);
        }

        resultEl.replaceChildren(fragment);
        resultEl.hidden = false;
    }

    async function submitQuestion(rawQuestion, source) {
        if (inFlight) {
            return;
        }
        const question = String(rawQuestion ?? '').trim();
        if (!question) {
            inputEl.focus();
            return;
        }
        if (question.length > MAX_QUESTION_LENGTH) {
            renderError('질문은 800자 이하로 입력해주세요.', '');
            return;
        }

        trackEvent('home_ai_ask', { source: source || 'form' });
        setBusy(true);
        renderLoading();

        try {
            const response = await fetch('/api/assistant/chat', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    Accept: 'application/json',
                },
                body: JSON.stringify({ message: question }),
            });

            const data = await response.json().catch(() => ({}));
            if (!response.ok) {
                const message = data?.error ? String(data.error) : `AI 요청에 실패했습니다. (${response.status})`;
                const usageText = data?.usageText ? String(data.usageText) : '';
                renderError(message, usageText);
                return;
            }

            await renderSuccess(data);
        } catch (error) {
            renderError('AI 응답을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.', '');
        } finally {
            setBusy(false);
        }
    }

    formEl.addEventListener('submit', (event) => {
        event.preventDefault();
        void submitQuestion(inputEl.value, 'form');
    });

    chipButtons.forEach((button) => {
        button.addEventListener('click', () => {
            if (inFlight) {
                return;
            }
            const prompt = button.dataset.askPrompt || '';
            inputEl.value = prompt;
            void submitQuestion(prompt, 'chip');
        });
    });
})();
