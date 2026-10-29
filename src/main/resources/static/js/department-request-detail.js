(() => {
    'use strict';

    const requestId =
        $('#department-detail-config').dataset.requestId;

    $('#department-back').href =
        BASE + '/department-requests' + location.search;

    async function loadHistory() {
        const panel = $('#approval-history-panel');
        const list = $('#approval-history-list');

        try {
            const history = await api(
                '/requests/department/'
                + encodeURIComponent(requestId)
                + '/approval-history',
                { cache: 'no-store' }
            );

            list.innerHTML =
                RequestDetailRenderer.renderHistory(history);

        } catch (error) {
            list.innerHTML =
                '<li class="empty">'
                + esc(error.message || '결재 이력을 불러오지 못했습니다.')
                + '</li>';
        }

        panel.hidden = false;
    }

    async function loadDetail() {
        try {
            const detail = await api(
                '/requests/department/' + encodeURIComponent(requestId),
                { cache: 'no-store' }
            );

            RequestDetailRenderer.renderRequestInfo(detail);
            RequestDetailRenderer.renderViewCreateResult(detail);
            RequestDetailRenderer.renderSavedApprovalLine(detail);

            const downloadButton = $('#view-sql-download');
            if (downloadButton && detail.requestType === 'CREATE_VIEW'
                    && detail.requestStatus === 'READY'
                    && detail.approvalStatus === 'APPROVED') {
                downloadButton.hidden = false;
            }

            $('#detail-items').innerHTML =
                RequestDetailRenderer.renderItems(
                    detail.items,
                    new Map(),
                    { readOnly: true }
                );

            $('#request-info').hidden = false;
            $('#department-detail-content').hidden = false;

        } catch (error) {
            $('#detail-title').textContent = '기안을 열 수 없습니다.';
            $('#department-detail-content').hidden = true;
            notify(error.message, true);
            return;
        }

        await loadHistory();
    }

    const resultForm = $('#view-create-result-form');
    if (resultForm) {
        resultForm.addEventListener('submit', async event => {
            event.preventDefault();
            const button = $('#view-create-result-submit');
            const message = $('#view-create-result-message');
            button.disabled = true;
            message.textContent = '저장 중…';
            try {
                await api('/requests/department/' + encodeURIComponent(requestId)
                    + '/view-create-result', {
                    method: 'POST',
                    body: JSON.stringify({
                        resultStatus: $('#view-create-result-select').value,
                        resultContent: $('#view-create-result-input').value
                    })
                });
                notify('외부 처리 결과를 등록했습니다.');
                message.textContent = '처리 결과를 저장했습니다.';
                await loadDetail();
            } catch (error) {
                message.textContent = error.message || '결과 등록에 실패했습니다.';
            } finally {
                button.disabled = false;
            }
        });
    }

    const downloadButton = $('#view-sql-download');
    if (downloadButton) {
        downloadButton.addEventListener('click', async () => {
            const message = $('#view-sql-download-message');
            downloadButton.disabled = true;
            message.textContent = '다운로드 준비 중…';
            try {
                const response = await fetch(BASE + '/api/requests/'
                        + encodeURIComponent(requestId) + '/view-sql/download', {
                    headers: {
                        'Accept': 'application/sql',
                        [document.querySelector('meta[name="csrf-header"]')?.content || 'X-CSRF-TOKEN']:
                            document.querySelector('meta[name="csrf-token"]')?.content || ''
                    }
                });
                if (!response.ok) {
                    let error = {};
                    try { error = await response.json(); } catch (ignored) { }
                    throw new Error(error.message || 'SQL 다운로드에 실패했습니다.');
                }
                const url = URL.createObjectURL(await response.blob());
                const link = document.createElement('a');
                link.href = url;
                link.download = 'view-create-request-' + requestId + '.sql';
                document.body.append(link);
                link.click();
                link.remove();
                URL.revokeObjectURL(url);
                message.textContent = 'SQL 파일을 다운로드했습니다.';
            } catch (error) {
                message.textContent = error.message || 'SQL 다운로드에 실패했습니다.';
            } finally {
                downloadButton.disabled = false;
            }
        });
    }

    loadDetail();
})();
