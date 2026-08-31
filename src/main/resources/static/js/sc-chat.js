(() => {
    const terminalEl = document.getElementById('scTerminal');
    const outputEl = document.getElementById('scTerminalOutput');
    if (!terminalEl || !outputEl) {
        return;
    }

    const COLLAPSED_CLASS = 'is-collapsed';
    const MAX_QUESTION_LENGTH = 800;
    const DEFAULT_MAX_RENDERED_MESSAGES = 50;

    let lastSeq = 0;
    const renderedIds = new Set();
    let pollTimerId = null;
    let started = false;
    let pollIntervalMillis = 2500;
    let hiddenPollIntervalMillis = 10000;
    let errorBackoffMillis = 0;
    let self = null;
    // 광고 위치는 현재 유지하는 메시지 id/역할만으로 결정적으로 다시 계산한다.
    let adObserver = null;
    let currentAdLineEl = null;
    let currentAdIframeEl = null;
    let currentAdSrc = null;
    let currentAdAfterMessageId = null;
    let batchRendering = false;
    let batchFragment = null;
    let maxRenderedMessages = DEFAULT_MAX_RENDERED_MESSAGES;

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
            removePreviousChatAd();
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

    function isChatExpanded() {
        return document.body.classList.contains('sc-chat-fullscreen');
    }

    function createCurrentChatAdIframe() {
        if (!isChatExpanded() || !currentAdLineEl || !currentAdLineEl.isConnected
                || currentAdIframeEl || !currentAdSrc) {
            return;
        }
        const iframeEl = document.createElement('iframe');
        iframeEl.width = currentAdLineEl.dataset.adWidth;
        iframeEl.height = currentAdLineEl.dataset.adHeight;
        iframeEl.setAttribute('frameborder', '0');
        iframeEl.setAttribute('scrolling', 'no');
        iframeEl.setAttribute('referrerpolicy', 'unsafe-url');
        iframeEl.title = '쿠팡 파트너스 광고';
        iframeEl.src = currentAdSrc;
        currentAdLineEl.insertBefore(iframeEl, currentAdLineEl.firstChild);
        currentAdIframeEl = iframeEl;
    }

    // 확장된 채팅에서 최신 광고가 실제 화면 근처에 보일 때 iframe을 만든다.
    function getAdObserver() {
        if (adObserver || typeof IntersectionObserver === 'undefined') {
            return adObserver;
        }
        adObserver = new IntersectionObserver((entries) => {
            entries.forEach((entry) => {
                if (!entry.isIntersecting) {
                    return;
                }
                const lineEl = entry.target;
                if (lineEl !== currentAdLineEl || !lineEl.isConnected) {
                    adObserver.unobserve(lineEl);
                    return;
                }
                createCurrentChatAdIframe();
                adObserver.unobserve(lineEl);
            });
        }, { root: outputEl, rootMargin: '200px 0px' });
        return adObserver;
    }

    function removePreviousChatAd() {
        if (currentAdLineEl && adObserver) {
            adObserver.unobserve(currentAdLineEl);
        }
        if (currentAdLineEl) {
            currentAdLineEl.remove();
        }
        currentAdLineEl = null;
        currentAdIframeEl = null;
        currentAdSrc = null;
        currentAdAfterMessageId = null;
    }

    function observePendingChatAd() {
        if (!isChatExpanded() || !currentAdLineEl || currentAdIframeEl || !currentAdSrc) {
            return;
        }
        const observer = getAdObserver();
        if (observer) {
            observer.observe(currentAdLineEl);
            return;
        }
        createCurrentChatAdIframe();
    }

    function unloadCurrentChatAd() {
        if (!currentAdIframeEl) {
            return;
        }
        currentAdIframeEl.remove();
        currentAdIframeEl = null;
    }

    function setChatExpanded(expanded) {
        const actuallyExpanded = expanded && isChatExpanded();
        if (currentAdLineEl) {
            currentAdLineEl.setAttribute('aria-hidden', actuallyExpanded ? 'false' : 'true');
        }
        if (actuallyExpanded) {
            observePendingChatAd();
        } else {
            if (adObserver && currentAdLineEl) {
                adObserver.unobserve(currentAdLineEl);
            }
            unloadCurrentChatAd();
        }
    }

    // 쿠팡 g.js는 document.write 방식이라 동적 삽입이 불가능해,
    // g.js가 최종 생성하는 위젯 iframe을 직접 만들어 채팅 로그에 붙인다.
    function insertAdLine(config, afterMessageEl, afterMessageId) {
        const logEl = ensureChatLog();
        const shouldScroll = !batchRendering && isNearBottom();

        // 배치에서 결정된 최신 후보 하나만 DOM에 만들고 이전 광고는 제거한다.
        removePreviousChatAd();

        const lineEl = document.createElement('div');
        lineEl.className = 'sc-chat__line sc-chat__ad';
        lineEl.setAttribute('aria-hidden', isChatExpanded() ? 'false' : 'true');

        const adSrc = 'https://ads-partners.coupang.com/widgets.html'
            + '?id=' + encodeURIComponent(config.id)
            + '&template=carousel'
            + '&trackingCode=' + encodeURIComponent(config.trackingCode)
            + '&subId=&width=' + config.width + '&height=' + config.height + '&tsource=';
        lineEl.dataset.adWidth = String(config.width);
        lineEl.dataset.adHeight = String(config.height);

        const noticeEl = document.createElement('span');
        noticeEl.className = 'sc-chat__ad-notice';
        noticeEl.textContent = '* 쿠팡 파트너스 활동의 일환으로, 이에 따른 일정액의 수수료를 제공받습니다.';

        lineEl.appendChild(noticeEl);
        if (afterMessageEl && afterMessageEl.parentElement === logEl) {
            afterMessageEl.insertAdjacentElement('afterend', lineEl);
        } else {
            logEl.appendChild(lineEl);
        }
        currentAdLineEl = lineEl;
        currentAdIframeEl = null;
        currentAdSrc = adSrc;
        currentAdAfterMessageId = afterMessageId;

        // 축소 상태에서는 iframe 자체를 만들지 않는다. 확장 후 근접하면 즉시 로드한다.
        observePendingChatAd();

        if (shouldScroll) {
            scrollToBottom();
        }
    }

    function refreshLatestChatAd() {
        const config = getChatAdConfig();
        if (!config) {
            removePreviousChatAd();
            return;
        }
        const logEl = ensureChatLog();
        const messageEls = Array.from(logEl.querySelectorAll('.sc-chat__line[data-msg-id]'))
            .sort((leftEl, rightEl) => Number.parseInt(leftEl.dataset.msgId || '', 10)
                - Number.parseInt(rightEl.dataset.msgId || '', 10));
        const recentRoles = [];
        let sinceLastAd = Number.POSITIVE_INFINITY;
        let candidate = null;

        messageEls.forEach((messageEl) => {
            const messageId = Number.parseInt(messageEl.dataset.msgId || '', 10);
            const role = messageEl.dataset.role || '';
            const hadRecentAi = recentRoles.includes('AI');
            recentRoles.push(role);
            if (recentRoles.length > config.aiRecentWindow) {
                recentRoles.shift();
            }
            sinceLastAd += 1;

            const aiAdDue = role === 'AI' && !hadRecentAi;
            const intervalAdDue = config.messageInterval > 0
                && messageId % config.messageInterval === 0;
            if ((aiAdDue || intervalAdDue) && sinceLastAd >= config.aiRecentWindow) {
                candidate = { messageEl, messageId };
                sinceLastAd = 0;
            }
        });

        if (!candidate) {
            removePreviousChatAd();
            return;
        }
        if (currentAdLineEl && currentAdLineEl.isConnected
                && currentAdAfterMessageId === candidate.messageId) {
            if (candidate.messageEl.nextElementSibling !== currentAdLineEl) {
                candidate.messageEl.insertAdjacentElement('afterend', currentAdLineEl);
            }
            return;
        }
        insertAdLine(config, candidate.messageEl, candidate.messageId);
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
        linkEl.setAttribute('data-google-vignette', 'false');
        linkEl.textContent = match[1];
        contentEl.appendChild(linkEl);
    }

    function insertMessageLine(logEl, lineEl) {
        const messageId = Number.parseInt(lineEl.dataset.msgId || '', 10);
        const nextMessageEl = Array.from(logEl.querySelectorAll('.sc-chat__line[data-msg-id]'))
            .find((candidateEl) => Number.parseInt(candidateEl.dataset.msgId || '', 10) > messageId);
        if (nextMessageEl) {
            logEl.insertBefore(lineEl, nextMessageEl);
        } else {
            logEl.appendChild(lineEl);
        }
    }

    function renderMessage(message) {
        if (!message || typeof message.id !== 'number' || renderedIds.has(message.id)) {
            return;
        }
        const logEl = ensureChatLog();
        renderedIds.add(message.id);

        const shouldScroll = !batchRendering && isNearBottom();

        const lineEl = document.createElement('div');
        lineEl.className = 'sc-chat__line';
        lineEl.dataset.msgId = String(message.id);
        lineEl.dataset.nick = message.nickname || '';
        lineEl.dataset.role = message.role || '';

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

        if (batchFragment) {
            batchFragment.appendChild(lineEl);
        } else {
            insertMessageLine(logEl, lineEl);
        }
        if (shouldScroll) {
            scrollToBottom();
        }
        if (!batchRendering) {
            pruneOldMessages();
            refreshLatestChatAd();
        }
        return lineEl;
    }

    function pruneOldMessages() {
        const logEl = ensureChatLog();
        const messageEls = Array.from(logEl.querySelectorAll('.sc-chat__line[data-msg-id]'))
            .sort((leftEl, rightEl) => Number.parseInt(leftEl.dataset.msgId || '', 10)
                - Number.parseInt(rightEl.dataset.msgId || '', 10));
        const removeCount = Math.max(0, messageEls.length - maxRenderedMessages);
        for (let index = 0; index < removeCount; index += 1) {
            const messageEl = messageEls[index];
            const messageId = Number.parseInt(messageEl.dataset.msgId || '', 10);
            if (!Number.isNaN(messageId)) {
                renderedIds.delete(messageId);
            }
            messageEl.remove();
        }
    }

    function renderMessages(messages) {
        if (!Array.isArray(messages) || messages.length === 0) {
            return;
        }
        const shouldScroll = isNearBottom();
        const logEl = ensureChatLog();
        const orderedMessages = messages.slice().sort((left, right) => {
            const leftId = left && typeof left.id === 'number' ? left.id : Number.POSITIVE_INFINITY;
            const rightId = right && typeof right.id === 'number' ? right.id : Number.POSITIVE_INFINITY;
            return leftId - rightId;
        });
        const firstNewMessage = orderedMessages.find((message) => message
            && typeof message.id === 'number' && !renderedIds.has(message.id));
        const existingMessageIds = Array.from(logEl.querySelectorAll('.sc-chat__line[data-msg-id]'))
            .map((messageEl) => Number.parseInt(messageEl.dataset.msgId || '', 10))
            .filter((messageId) => !Number.isNaN(messageId));
        const largestExistingId = existingMessageIds.length > 0
            ? Math.max(...existingMessageIds)
            : Number.NEGATIVE_INFINITY;
        const canAppendBatch = !firstNewMessage || firstNewMessage.id > largestExistingId;
        batchRendering = true;
        batchFragment = canAppendBatch ? document.createDocumentFragment() : null;
        try {
            orderedMessages.forEach(renderMessage);
        } finally {
            if (batchFragment) {
                logEl.appendChild(batchFragment);
            }
            batchFragment = null;
            batchRendering = false;
        }
        pruneOldMessages();
        refreshLatestChatAd();
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
            if (self.historySize > 0) {
                maxRenderedMessages = self.historySize;
            }
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
        renderMessages(data.messages);
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
        // 프리렌더된 문서는 실제 화면 전환 후에만 폴링을 시작한다.
        openTerminal();
        if (document.prerendering) {
            document.addEventListener('prerenderingchange', start, { once: true });
        } else {
            start();
        }
    }

    document.addEventListener('visibilitychange', () => {
        if (started && !document.hidden) {
            scheduleNextPoll();
        }
    });

    window.addEventListener('sc:chat-expanded', (event) => {
        setChatExpanded(Boolean(event.detail && event.detail.expanded));
    });

    window.scChat = { send, ask, system: systemLine, runAdminCommand };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
