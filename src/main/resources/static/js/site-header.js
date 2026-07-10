(() => {
    const header = document.getElementById('scSiteHeader');
    if (!header || header.dataset.loggedIn !== 'true') {
        return;
    }

    const extendLogin = async () => {
        const shouldExtend = window.confirm('장시간 한 페이지에 머무르고 있습니다. 로그인을 연장할까요?');
        if (!shouldExtend) {
            window.alert('로그아웃 후 초기화면으로 이동합니다.');
            window.location.href = '/logout';
            return;
        }

        try {
            const response = await fetch('/extendLogin', { method: 'PUT' });
            if (!response.ok) {
                throw new Error(`Failed to extend login: ${response.status}`);
            }
            window.alert('로그인 시간을 연장했습니다.');
        } catch (error) {
            console.error('로그인 연장 실패', error);
            window.alert('로그인 연장 중 문제가 발생했습니다.');
        }
    };

    window.setInterval(() => void extendLogin(), 25 * 60 * 1000);
})();
