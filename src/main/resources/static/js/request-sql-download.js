/* 내 요청·부서 요청 상세의 SQL 파일 다운로드만 공통으로 처리한다. */
window.RequestSqlDownload = Object.freeze({
    update(detail) {
        const button = document.getElementById('view-sql-download');
        if (!button) return;

        const types = ['SELECT', 'CREATE_VIEW', 'INSERT', 'UPDATE', 'DELETE'];
        const states = ['READY', 'RUNNING', 'RETRY_REVIEW', 'COMPLETED', 'PARTIAL_FAILED', 'FAILED'];
        button.hidden = !(types.includes(detail.requestType)
            && states.includes(detail.requestStatus) && detail.approvalStatus === 'APPROVED');
        button.onclick = async () => {
            if (button.disabled) return;
            const message = document.getElementById('view-sql-download-message');
            const contextPath = document.querySelector('meta[name="context-path"]')?.content || '';
            button.disabled = true;
            button.setAttribute('aria-busy', 'true');
            message.textContent = '다운로드 준비 중…';
            try {
                const response = await fetch(contextPath + '/api/requests/'
                    + encodeURIComponent(detail.requestId) + '/view-sql/download', {
                    // 실패 응답의 JSON도 수신해 서버가 보낸 원인을 그대로 안내한다.
                    headers: { Accept: 'application/sql, application/json' }
                });
                if (!response.ok) {
                    let error = {};
                    try { error = await response.json(); } catch (ignored) { }
                    throw new Error(error.message || 'SQL 다운로드에 실패했습니다.');
                }
                if (!response.headers.get('Content-Type')?.includes('application/sql')) {
                    throw new Error('로그인 상태를 확인한 뒤 다시 다운로드해 주세요.');
                }
                const url = URL.createObjectURL(await response.blob());
                const link = document.createElement('a');
                link.href = url;
                link.download = 'request-' + detail.requestId + '.sql';
                document.body.append(link);
                link.click();
                link.remove();
                setTimeout(() => URL.revokeObjectURL(url), 1000);
                message.textContent = 'SQL 파일을 다운로드했습니다.';
            } catch (error) {
                message.textContent = error.message || 'SQL 다운로드에 실패했습니다.';
            } finally {
                button.disabled = false;
                button.removeAttribute('aria-busy');
            }
        };
    }
});
