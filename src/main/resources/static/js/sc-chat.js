(() => {
    const terminalEl = document.getElementById('scTerminal');
    const outputEl = document.getElementById('scTerminalOutput');
    const inputEl = document.getElementById('scTerminalInput');
    if (!terminalEl || !outputEl) {
        return;
    }

    const COLLAPSED_CLASS = 'is-collapsed';
    const MAX_QUESTION_LENGTH = 800;

    let lastSeq = 0;
    const renderedIds = new Set();
    let pollTimerId = null;
    let started = false;
    let pollIntervalMillis = 2500;
    let hiddenPollIntervalMillis = 10000;
    let errorBackoffMillis = 0;
    let self = null;

    function getMemberMeta() {
        const metaEl = document.getElementById('scMemberMeta');
        const grade = metaEl ? Number.parseInt(metaEl.dataset.grade || '0', 10) : 0;
        const isLoggedIn = metaEl ? metaEl.dataset.loggedIn === 'true' : false;
        return { grade, isLoggedIn, isAdmin: grade === 3 };
    }

    function ensureChatLog() {
        let logEl = document.getElementById('scChatLog');
        if (!logEl) {
            // The terminal `clear` command wipes the whole output — rebuild and
            // let the next poll re-render history from the server buffer.
            logEl = document.createElement('div');
            logEl.id = 'scChatLog';
            logEl.className = 'sc-chat__log';
            outputEl.insertBefore(logEl, outputEl.firstChild);
            renderedIds.clear();
            lastSeq = 0;
        }
        return logEl;
    }

    function openTerminal() {
        terminalEl.classList.remove(COLLAPSED_CLASS);
    }

    function isNearBottom() {
        return outputEl.scrollHeight - outputEl.scrollTop - outputEl.clientHeight < 40;
    }

    function scrollToBottom() {
        outputEl.scrollTop = outputEl.scrollHeight;
    }

    function nickClass(role) {
        switch (role) {
            case 'ADMIN': return 'sc-chat__nick sc-chat__nick--admin';
            case 'AI': return 'sc-chat__nick sc-chat__nick--ai';
            case 'GUEST': return 'sc-chat__nick sc-chat__nick--guest';
            default: return 'sc-chat__nick sc-chat__nick--member';
        }
    }

    function renderMessage(message) {
        if (!message || typeof message.id !== 'number' || renderedIds.has(message.id)) {
            return;
        }
        const logEl = ensureChatLog();
        renderedIds.add(message.id);

        const shouldScroll = isNearBottom();

        const lineEl = document.createElement('div');
        lineEl.className = 'sc-chat__line';
        lineEl.dataset.msgId = String(message.id);
        lineEl.dataset.nick = message.nickname || '';

        const timeEl = document.createElement('span');
        timeEl.className = 'sc-chat__time';
        timeEl.textContent = message.regDate || '';

        const nickEl = document.createElement('span');
        nickEl.className = nickClass(message.role);
        nickEl.textContent = '<' + (message.nickname || '?') + '>';

        const contentEl = document.createElement('span');
        contentEl.className = 'sc-chat__content';
        contentEl.textContent = ' ' + (message.content || '');

        lineEl.appendChild(timeEl);
        lineEl.appendChild(nickEl);
        lineEl.appendChild(contentEl);

        if (getMemberMeta().isAdmin && message.role !== 'AI') {
            const delEl = document.createElement('span');
            delEl.className = 'sc-chat__admin-del';
            delEl.textContent = '[X]';
            delEl.title = '메시지 삭제';
            delEl.addEventListener('click', () => { void deleteMessage(message.id); });
            lineEl.appendChild(delEl);
        }

        logEl.appendChild(lineEl);
        if (shouldScroll) {
            scrollToBottom();
        }
    }

    function markDeleted(messageId) {
        renderedIds.add(messageId);
        const lineEl = outputEl.querySelector('.sc-chat__line[data-msg-id="' + messageId + '"]');
        if (!lineEl) {
            return;
        }
        lineEl.className = 'sc-chat__line sc-chat__line--deleted';
        lineEl.textContent = '삭제된 메시지입니다.';
    }

    function systemLine(message) {
        const logEl = ensureChatLog();
        const shouldScroll = isNearBottom();
        const lineEl = document.createElement('div');
        lineEl.className = 'sc-chat__line sc-chat__line--system';
        lineEl.textContent = '* ' + message;
        logEl.appendChild(lineEl);
        openTerminal();
        if (shouldScroll) {
            scrollToBottom();
        }
        return lineEl;
    }

    async function fetchJson(url, options) {
        const response = await fetch(url, Object.assign({
            headers: { Accept: 'application/json' },
            credentials: 'include',
        }, options || {}));
        const contentType = response.headers.get('content-type') || '';
        if (!contentType.includes('application/json')) {
            // AdminInterceptor answers with an HTML alert page when the session expired.
            return { ok: response.ok, status: response.status, data: null, nonJson: true };
        }
        const data = await response.json().catch(() => null);
        return { ok: response.ok, status: response.status, data, nonJson: false };
    }

    function applyPollResponse(data) {
        if (!data) {
            return;
        }
        if (data.self) {
            self = data.self;
            if (self.pollIntervalMillis > 0) {
                pollIntervalMillis = self.pollIntervalMillis;
            }
            if (self.hiddenPollIntervalMillis > 0) {
                hiddenPollIntervalMillis = self.hiddenPollIntervalMillis;
            }
            if (self.muted && self.mutedText) {
                systemLine(self.mutedText);
            }
        }
        (data.messages || []).forEach(renderMessage);
        (data.deletedIds || []).forEach(markDeleted);
        if (typeof data.lastSeq === 'number' && data.lastSeq > lastSeq) {
            lastSeq = data.lastSeq;
        }
    }

    async function pollOnce() {
        try {
            const result = await fetchJson('/api/chat/messages?afterSeq=' + lastSeq);
            if (result.ok && result.data) {
                applyPollResponse(result.data);
                errorBackoffMillis = 0;
            } else if (result.status === 503) {
                errorBackoffMillis = 30000;
            } else {
                bumpBackoff();
            }
        } catch (e) {
            bumpBackoff();
        }
        scheduleNextPoll();
    }

    function bumpBackoff() {
        errorBackoffMillis = Math.min(errorBackoffMillis > 0 ? errorBackoffMillis * 2 : 5000, 30000);
    }

    function scheduleNextPoll() {
        if (pollTimerId) {
            window.clearTimeout(pollTimerId);
        }
        let delay = document.hidden ? hiddenPollIntervalMillis : pollIntervalMillis;
        if (errorBackoffMillis > 0) {
            delay = Math.max(delay, errorBackoffMillis);
        }
        pollTimerId = window.setTimeout(() => { void pollOnce(); }, delay);
    }

    function start() {
        if (started) {
            return;
        }
        started = true;
        void pollOnce();
    }

    async function send(text) {
        const trimmed = String(text ?? '').trim();
        if (!trimmed) {
            return;
        }
        start();
        openTerminal();
        try {
            const result = await fetchJson('/api/chat/messages', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
                body: JSON.stringify({ content: trimmed }),
            });
            if (result.ok && result.data && result.data.message) {
                renderMessage(result.data.message);
                if (result.data.lastSeq > lastSeq) {
                    lastSeq = result.data.lastSeq;
                }
                scrollToBottom();
                return;
            }
            const error = result.data && result.data.error
                ? result.data.error
                : '메시지 전송에 실패했습니다. (' + result.status + ')';
            systemLine(error);
        } catch (e) {
            systemLine('메시지 전송에 실패했습니다. 네트워크 상태를 확인해주세요.');
        }
    }

    async function ask(question) {
        const trimmed = String(question ?? '').trim();
        if (!trimmed) {
            systemLine('사용법: /ai <질문>');
            return;
        }
        if (trimmed.length > MAX_QUESTION_LENGTH) {
            systemLine('질문이 너무 깁니다. 조금만 짧게 입력해주세요.');
            return;
        }
        start();
        openTerminal();

        const pendingEl = systemLine('AI 답변 생성 중');
        const dotsEl = document.createElement('span');
        dotsEl.className = 'sc-loading-dots';
        dotsEl.textContent = '.';
        pendingEl.appendChild(dotsEl);
        let dots = 1;
        const dotsTimerId = window.setInterval(() => {
            dots = (dots % 3) + 1;
            dotsEl.textContent = '.'.repeat(dots);
        }, 500);

        try {
            const result = await fetchJson('/api/chat/ai', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
                body: JSON.stringify({ question: trimmed }),
            });
            const data = result.data || {};
            if (data.questionMessage) {
                renderMessage(data.questionMessage);
            }
            if (data.answerMessage) {
                renderMessage(data.answerMessage);
            }
            if (typeof data.lastSeq === 'number' && data.lastSeq > lastSeq) {
                lastSeq = data.lastSeq;
            }
            if (!result.ok && data.error) {
                systemLine(data.error);
            } else if (!result.ok) {
                systemLine('AI 요청에 실패했습니다. (' + result.status + ')');
            }
            if (data.usageText) {
                systemLine(data.usageText);
            }
            scrollToBottom();
        } catch (e) {
            systemLine('AI 응답을 불러오지 못했습니다.');
        } finally {
            window.clearInterval(dotsTimerId);
            pendingEl.remove();
        }
    }

    async function deleteMessage(messageId) {
        try {
            const result = await fetchJson('/api/admin/chat/messages/' + messageId, { method: 'DELETE' });
            if (result.nonJson) {
                systemLine('관리자 세션이 만료되었습니다. 다시 로그인해주세요.');
                return;
            }
            if (result.ok) {
                markDeleted(messageId);
            } else {
                systemLine(result.data && result.data.error ? result.data.error : '삭제에 실패했습니다.');
            }
        } catch (e) {
            systemLine('삭제에 실패했습니다.');
        }
    }

    function runAdminCommand(body) {
        const meta = getMemberMeta();
        if (!meta.isAdmin) {
            return false;
        }
        const tokens = String(body || '').trim().split(/\s+/);
        const command = (tokens[0] || '').toLowerCase();
        const nickname = tokens[1] || '';
        const minutes = tokens[2] ? Number.parseInt(tokens[2], 10) : null;

        if (command === 'del') {
            if (!nickname) {
                systemLine('사용법: /del <닉네임>');
                return true;
            }
            const lines = outputEl.querySelectorAll('.sc-chat__line[data-nick="' + CSS.escape(nickname) + '"]');
            if (!lines.length) {
                systemLine('해당 닉네임의 메시지를 찾을 수 없습니다.');
                return true;
            }
            const target = lines[lines.length - 1];
            void deleteMessage(Number.parseInt(target.dataset.msgId, 10));
            return true;
        }

        if (command === 'mute' || command === 'blockip') {
            if (!nickname) {
                systemLine('사용법: /' + command + ' <닉네임> [분]');
                return true;
            }
            const type = command === 'mute' ? 'MUTE' : 'BLOCK_IP';
            void (async () => {
                const result = await fetchJson('/api/admin/chat/sanctions', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
                    body: JSON.stringify({ type, nickname, minutes, reason: tokens.slice(3).join(' ') || null }),
                });
                if (result.nonJson) {
                    systemLine('관리자 세션이 만료되었습니다. 다시 로그인해주세요.');
                    return;
                }
                if (result.ok) {
                    const sanction = result.data && result.data.sanction;
                    systemLine('제재 등록 완료: ' + nickname + ' (' + type + ', 해제: '
                        + (sanction && sanction.expiresAtText ? sanction.expiresAtText : '영구') + ')');
                } else {
                    systemLine(result.data && result.data.error ? result.data.error : '제재 등록에 실패했습니다.');
                }
            })();
            return true;
        }

        if (command === 'unmute' || command === 'unblockip') {
            if (!nickname) {
                systemLine('사용법: /' + command + ' <닉네임>');
                return true;
            }
            const type = command === 'unmute' ? 'MUTE' : 'BLOCK_IP';
            void (async () => {
                const listResult = await fetchJson('/api/admin/chat/sanctions');
                if (listResult.nonJson) {
                    systemLine('관리자 세션이 만료되었습니다. 다시 로그인해주세요.');
                    return;
                }
                const sanctions = Array.isArray(listResult.data) ? listResult.data : [];
                const match = sanctions.find((s) => s.sanctionType === type && s.nickname === nickname);
                if (!match) {
                    systemLine('해당 닉네임의 활성 제재를 찾을 수 없습니다.');
                    return;
                }
                const result = await fetchJson('/api/admin/chat/sanctions/' + match.id, { method: 'DELETE' });
                systemLine(result.ok ? '제재 해제 완료: ' + nickname : '제재 해제에 실패했습니다.');
            })();
            return true;
        }

        if (command === 'sanctions') {
            void (async () => {
                const result = await fetchJson('/api/admin/chat/sanctions');
                if (result.nonJson) {
                    systemLine('관리자 세션이 만료되었습니다. 다시 로그인해주세요.');
                    return;
                }
                const sanctions = Array.isArray(result.data) ? result.data : [];
                if (!sanctions.length) {
                    systemLine('활성 제재가 없습니다.');
                    return;
                }
                sanctions.forEach((s) => {
                    systemLine('#' + s.id + ' ' + s.sanctionType + ' ' + (s.nickname || '-')
                        + ' (해제: ' + (s.expiresAtText || '영구') + ')'
                        + (s.reason ? ' - ' + s.reason : ''));
                });
            })();
            return true;
        }

        return false;
    }

    function init() {
        const isHomePage = document.body.classList.contains('sc-home-page');
        if (isHomePage) {
            openTerminal();
            start();
            return;
        }
        // 다른 페이지에서는 터미널을 처음 사용할 때만 폴링을 시작해 부하를 줄인다.
        if (inputEl) {
            inputEl.addEventListener('focus', () => start(), { once: true });
        }
    }

    document.addEventListener('visibilitychange', () => {
        if (started && !document.hidden) {
            scheduleNextPoll();
        }
    });

    window.scChat = { send, ask, system: systemLine, runAdminCommand };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
