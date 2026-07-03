(() => {
    const terminalEl = document.getElementById('scTerminal');
    const outputEl = document.getElementById('scTerminalOutput');
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
    // 광고 위치는 메시지 id/역할만으로 결정적으로 계산한다.
    // 페이지를 이동해도 서버 버퍼가 같은 순서로 재렌더링되므로 광고가 같은 자리에 유지된다.
    let recentRoles = [];
    let sinceLastAd = Number.POSITIVE_INFINITY;
    let adObserver = null;

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
            recentRoles = [];
            sinceLastAd = Number.POSITIVE_INFINITY;
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

    function getChatAdConfig() {
        const metaEl = document.getElementById('scChatAdMeta');
        if (!metaEl || !metaEl.dataset.trackingCode || !metaEl.dataset.pcId) {
            return null;
        }
        const useMobile = metaEl.dataset.mobileId && window.matchMedia('(max-width: 768px)').matches;
        return {
            trackingCode: metaEl.dataset.trackingCode,
            id: useMobile ? metaEl.dataset.mobileId : metaEl.dataset.pcId,
            width: Number.parseInt((useMobile ? metaEl.dataset.mobileWidth : metaEl.dataset.pcWidth) || '680', 10),
            height: Number.parseInt((useMobile ? metaEl.dataset.mobileHeight : metaEl.dataset.pcHeight) || '140', 10),
            messageInterval: Number.parseInt(metaEl.dataset.messageInterval || '20', 10),
            aiRecentWindow: Number.parseInt(metaEl.dataset.aiRecentWindow || '5', 10),
        };
    }

    // 히스토리 재렌더링으로 광고가 여러 개 생겨도 화면에 보일 때만 iframe을 로드한다.
    function getAdObserver() {
        if (adObserver || typeof IntersectionObserver === 'undefined') {
            return adObserver;
        }
        adObserver = new IntersectionObserver((entries) => {
            entries.forEach((entry) => {
                if (!entry.isIntersecting) {
                    return;
                }
                const iframeEl = entry.target;
                if (iframeEl.dataset.src) {
                    iframeEl.src = iframeEl.dataset.src;
                    delete iframeEl.dataset.src;
                }
                adObserver.unobserve(iframeEl);
            });
        }, { root: outputEl, rootMargin: '200px 0px' });
        return adObserver;
    }

    // 쿠팡 g.js는 document.write 방식이라 동적 삽입이 불가능해,
    // g.js가 최종 생성하는 위젯 iframe을 직접 만들어 채팅 로그에 붙인다.
    function insertAdLine(config) {
        const logEl = ensureChatLog();
        const shouldScroll = isNearBottom();

        const lineEl = document.createElement('div');
        lineEl.className = 'sc-chat__line sc-chat__ad';

        const iframeEl = document.createElement('iframe');
        const adSrc = 'https://ads-partners.coupang.com/widgets.html'
            + '?id=' + encodeURIComponent(config.id)
            + '&template=carousel'
            + '&trackingCode=' + encodeURIComponent(config.trackingCode)
            + '&subId=&width=' + config.width + '&height=' + config.height + '&tsource=';
        iframeEl.width = String(config.width);
        iframeEl.height = String(config.height);
        iframeEl.setAttribute('frameborder', '0');
        iframeEl.setAttribute('scrolling', 'no');
        iframeEl.setAttribute('referrerpolicy', 'unsafe-url');
        iframeEl.title = '쿠팡 파트너스 광고';

        const noticeEl = document.createElement('span');
        noticeEl.className = 'sc-chat__ad-notice';
        noticeEl.textContent = '* 쿠팡 파트너스 활동의 일환으로, 이에 따른 일정액의 수수료를 제공받습니다.';

        lineEl.appendChild(iframeEl);
        lineEl.appendChild(noticeEl);
        logEl.appendChild(lineEl);

        const observer = getAdObserver();
        if (observer) {
            iframeEl.dataset.src = adSrc;
            observer.observe(iframeEl);
        } else {
            iframeEl.src = adSrc;
        }

        if (shouldScroll) {
            scrollToBottom();
            // iframe 로드로 높이가 늘어난 뒤에도 하단 고정을 유지한다.
            iframeEl.addEventListener('load', () => {
                if (isNearBottom()) {
                    scrollToBottom();
                }
            });
        }
    }

    function maybeInsertAd(message) {
        const config = getChatAdConfig();
        if (!config) {
            return;
        }
        // AI 답변: 직전 N개(기본 5) 채팅에 AI 답변이 없을 때만 광고를 붙인다.
        const hadRecentAi = recentRoles.includes('AI');
        recentRoles.push(message.role);
        if (recentRoles.length > config.aiRecentWindow) {
            recentRoles.shift();
        }
        sinceLastAd += 1;

        const aiAdDue = message.role === 'AI' && !hadRecentAi;
        // 대화 N회(기본 20)마다: 전역 메시지 id 기준이라 재렌더링해도 같은 자리에 붙는다.
        const intervalAdDue = config.messageInterval > 0
            && message.id % config.messageInterval === 0;
        // 광고끼리 최소 N개 메시지 간격을 두어 연달아 붙는 것을 막는다.
        if ((aiAdDue || intervalAdDue) && sinceLastAd >= config.aiRecentWindow) {
            insertAdLine(config);
            sinceLastAd = 0;
        }
    }

    // AI 답변 끝의 "관련: <제목> <URL>" 줄은 URL을 숨기고 제목에 링크를 건다.
    const RELATED_LINE_PATTERN = /\n관련: ([\s\S]+) ((?:https?:\/\/|\/)\S+)\s*$/;

    function fillMessageContent(contentEl, message) {
        const text = ' ' + (message.content || '');
        const match = message.role === 'AI' ? RELATED_LINE_PATTERN.exec(text) : null;
        if (!match) {
            contentEl.textContent = text;
            return;
        }
        contentEl.textContent = text.slice(0, match.index) + '\n관련: ';
        const linkEl = document.createElement('a');
        linkEl.href = match[2];
        linkEl.textContent = match[1];
        contentEl.appendChild(linkEl);
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
        fillMessageContent(contentEl, message);

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
        maybeInsertAd(message);
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
        // 모든 페이지에서 채팅창을 기본 표시하고 폴링을 시작한다.
        openTerminal();
        start();
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
