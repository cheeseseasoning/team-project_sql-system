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
            RequestDetailRenderer.renderSavedApprovalLine(detail);

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

    loadDetail();
})();