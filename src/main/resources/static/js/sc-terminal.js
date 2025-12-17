(() => {
    const terminalEl = document.getElementById('scTerminal');
    if (!terminalEl) {
        return;
    }

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

    function relocateTerminalIntoBodyFrame() {
        const sectionInnerEl = document.querySelector('.section-inner');
        if (!sectionInnerEl) {
            return;
        }

        const targetRow = findBodyRow(sectionInnerEl);
        if (!targetRow) {
            return;
        }

        let slotEl = document.getElementById('scTerminalSlot');
        if (!slotEl) {
            slotEl = document.createElement('div');
            slotEl.id = 'scTerminalSlot';
            slotEl.className = targetRow.classList.contains('sc-row')
                ? 'sc-col-12 sc-terminal-slot'
                : 'col-sm-12 sc-terminal-slot';
            targetRow.appendChild(slotEl);
        }

        slotEl.appendChild(terminalEl);
    }

    relocateTerminalIntoBodyFrame();

    const outputEl = document.getElementById('scTerminalOutput');
    const inputEl = document.getElementById('scTerminalInput');

    if (!outputEl || !inputEl) {
        return;
    }

    const COLLAPSED_CLASS = 'is-collapsed';

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

    function sanitizeHtml(html) {
        const parser = new DOMParser();
        const doc = parser.parseFromString(`<div>${html ?? ''}</div>`, 'text/html');
        doc.querySelectorAll('script, style').forEach((el) => el.remove());
        return doc.body.firstElementChild ? doc.body.firstElementChild.innerHTML : '';
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
    }

    function appendSystemMessage(message) {
        appendEntry(['[SYSTEM]'], sanitizeHtml(message));
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

    async function openPost(boardTitle, postNum) {
        openTerminal();
        try {
            const post = await fetchPostData(boardTitle, postNum);
            const metaLines = [
                `[${boardTitle}] ${post.postNum}번 | ${post.title}`,
                `작성자: ${post.writer} | 날짜: ${post.regDate} | 조회: ${post.views}`,
            ];
            const rawHtml = `
                <div>
                    <a href="/boards/${boardTitle}/readPost?postNum=${postNum}" data-sc-terminal-bypass="1" target="_blank" rel="noopener">[원문 페이지 열기]</a>
                </div>
                ${post.content ?? ''}
            `;
            appendEntry(metaLines, sanitizeHtml(rawHtml));
            inputEl.focus();
        } catch (e) {
            appendSystemMessage(`게시물을 불러오지 못했습니다. (${boardTitle} / ${postNum})`);
        }
    }

    function openPostFromUrl(urlOrPath) {
        const parsed = parsePostUrl(urlOrPath);
        if (!parsed) {
            return false;
        }
        void openPost(parsed.boardTitle, parsed.postNum);
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
            appendSystemMessage('명령어: help, clear, close, open <url>, read <boardTitle> <postNum>');
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
            return openPostFromUrl(tokens[1]);
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

        return false;
    }

    function shouldBypassClick(event) {
        return (
            event.defaultPrevented ||
            event.button !== 0 ||
            event.metaKey ||
            event.ctrlKey ||
            event.shiftKey ||
            event.altKey
        );
    }

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
            return;
        }

        event.preventDefault();
        openPostFromUrl(anchor.href);
    });

    terminalEl.addEventListener('mousedown', () => {
        inputEl.focus();
    });

    window.scTerminal = {
        isPostUrl,
        openPostFromUrl,
        runCommand,
    };
})();
