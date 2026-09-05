// Indicate the current location in the shared navigation and board sidebar.
document.addEventListener('DOMContentLoaded', () => {
    const path = window.location.pathname.replace(/\/$/, '') || '/';
    const boardPath = path.startsWith('/boards/') ? path.split('/').slice(0, 3).join('/') : path;
    const communityBoards = ['/boards/noticeboard', '/boards/videolinkboard', '/boards/promotionboard', '/boards/funboard'];
    const section = path === '/' ? 'home' : path.startsWith('/strategy-tips') ? 'tips'
        : communityBoards.includes(boardPath) ? 'community' : path.startsWith('/boards/') ? 'strategy' : null;
    document.querySelectorAll('[data-nav-section]').forEach((link) => {
        if (link.dataset.navSection === section) link.setAttribute('aria-current', 'page');
    });
    document.querySelectorAll('#sidebar a[href]').forEach((link) => {
        if (link.getAttribute('href') === boardPath) link.setAttribute('aria-current', 'page');
    });
});

(() => {
    const header = document.getElementById('scSiteHeader');
    if (!header || header.dataset.loggedIn !== 'true') {
        return;
    }

    const CHECK_INTERVAL_MS = 60 * 1000;
    const KEEP_ALIVE_INTERVAL_MS = 10 * 60 * 1000;
    const RECENT_ACTIVITY_WINDOW_MS = 2 * 60 * 1000;
    let lastActivityAt = Date.now();
    let lastKeepAliveAt = Date.now();
    let requestInFlight = false;
    let stopped = false;

    const recordActivity = () => {
        lastActivityAt = Date.now();
    };

    const extendLoginIfActive = async () => {
        const now = Date.now();
        if (stopped || requestInFlight || document.visibilityState === 'hidden') {
            return;
        }
        if (now - lastActivityAt > RECENT_ACTIVITY_WINDOW_MS
            || now - lastKeepAliveAt < KEEP_ALIVE_INTERVAL_MS) {
            return;
        }

        requestInFlight = true;
        try {
            const response = await fetch('/extendLogin', {
                method: 'PUT',
                credentials: 'same-origin',
                headers: { 'X-Requested-With': 'XMLHttpRequest' }
            });
            if (response.status === 401) {
                stopped = true;
                window.alert('로그인 세션이 만료되었습니다. 작성 중인 내용이 있다면 복사한 뒤 다시 로그인해 주세요.');
                return;
            }
            if (!response.ok) {
                throw new Error(`Failed to extend login: ${response.status}`);
            }
            lastKeepAliveAt = Date.now();
        } catch (error) {
            console.error('로그인 연장 실패', error);
        } finally {
            requestInFlight = false;
        }
    };

    ['keydown', 'pointerdown', 'scroll', 'touchstart'].forEach((eventName) => {
        window.addEventListener(eventName, recordActivity, { passive: true });
    });
    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') {
            recordActivity();
            void extendLoginIfActive();
        }
    });

    window.setInterval(() => void extendLoginIfActive(), CHECK_INTERVAL_MS);
})();
