// Run in <head> before styles paint so saved dark mode survives page navigation.
(() => {
    const storageKey = 'sc1hub.theme';
    const root = document.documentElement;
    const systemTheme = window.matchMedia('(prefers-color-scheme: dark)');
    let preference = readPreference();

    function readPreference() {
        try {
            const value = window.localStorage.getItem(storageKey);
            return value === 'dark' || value === 'light' ? value : null;
        } catch (_) {
            return null;
        }
    }

    function render() {
        const theme = preference || (systemTheme.matches ? 'dark' : 'light');
        root.dataset.theme = theme;
        const meta = document.querySelector('meta[name="theme-color"]');
        if (meta) meta.content = theme === 'dark' ? '#1c2521' : '#f3f4f0';
        const toggle = document.getElementById('scThemeToggle');
        if (toggle) {
            toggle.setAttribute('aria-checked', String(theme === 'dark'));
            toggle.title = theme === 'dark' ? '라이트 모드로 전환' : '다크 모드로 전환';
        }
    }

    render();
    document.addEventListener('DOMContentLoaded', () => {
        render();
        const toggle = document.getElementById('scThemeToggle');
        if (!toggle) return;
        toggle.addEventListener('click', () => {
            preference = root.dataset.theme === 'dark' ? 'light' : 'dark';
            try {
                window.localStorage.setItem(storageKey, preference);
            } catch (_) {
                // The toggle still works when browser storage is unavailable.
            }
            render();
        });
    }, { once: true });
    systemTheme.addEventListener('change', () => {
        if (!preference) render();
    });
    window.addEventListener('storage', (event) => {
        if (event.key === storageKey || event.key === null) {
            preference = readPreference();
            render();
        }
    });
    window.addEventListener('pageshow', (event) => {
        if (event.persisted) {
            preference = readPreference();
            render();
        }
    });
})();
