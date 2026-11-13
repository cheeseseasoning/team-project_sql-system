/* 비동기로 갱신되는 목록도 전체 제목을 마우스·보조기기로 확인할 수 있게 한다. */
(() => {
    const selector = '.request-list-table .request-title-link, #execution-result-list .result-request-title, '
        + '.execution-queue-panel .queue-title-link, .notice-table tbody td:first-child a, '
        + '.views-page .views-object-name, .views-page .views-resource-name, '
        + '.resource-list-table .resource-view-link, .resource-list-table .resource-view-name, '
        + '.resource-list-table tbody td:nth-child(2), '
        + '.home-dashboard-grid .dashboard-recent-link, .home-dashboard-grid .dashboard-notice-title';
    const apply = () => document.querySelectorAll(selector).forEach(element => {
        element.title = element.textContent.trim();
    });
    let queued = false;
    apply();
    new MutationObserver(() => {
        if (queued) return;
        queued = true;
        queueMicrotask(() => { queued = false; apply(); });
    }).observe(document.body, { childList: true, subtree: true, characterData: true });
})();
