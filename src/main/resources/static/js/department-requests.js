(() => {
    'use strict';

    const statusNames = {
        PENDING_APPROVAL: '결재 대기',
        READY: '실행 대기',
        RUNNING: '실행 중',
        RETRY_REVIEW: '재실행 검토',
        COMPLETED: '실행 완료',
        PARTIAL_FAILED: '일부 실패',
        FAILED: '실패',
        REJECTED: '반려',
        WITHDRAWN: '회수'
    };

    function dateTime(value) {
        return value
            ? String(value).replace('T', ' ').substring(0, 19)
            : '-';
    }

    function dateOnly(value) {
        return value ? String(value).substring(0, 10) : '미지정';
    }

    if ($('#department-filter-form')) {
        initializeList();
    }

    function initializeList() {
        const form = $('#department-filter-form');
        const keywordInput = $('#department-keyword');
        const statusSelect = $('#department-status');
        const previous = $('#department-previous');
        const next = $('#department-next');

        Object.entries(statusNames).forEach(([value, label]) => {
            statusSelect.add(new Option(label, value));
        });

        const initial = new URLSearchParams(location.search);

        keywordInput.value = initial.get('keyword') || '';

        const initialStatus = initial.get('status') || '';
        statusSelect.value = Object.hasOwn(statusNames, initialStatus)
            ? initialStatus
            : '';

        let filters = {
            keyword: keywordInput.value.trim(),
            status: statusSelect.value
        };

        let currentPage = 1;
        let sequence = 0;

        function queryParams(page) {
            const params = new URLSearchParams({
                page: String(page),
                size: '10'
            });

            if (filters.keyword) {
                params.set('keyword', filters.keyword);
            }

            if (filters.status) {
                params.set('status', filters.status);
            }

            return params;
        }

        function emptyRow(message) {
            return '<tr><td colspan="6" class="empty">'
                + esc(message)
                + '</td></tr>';
        }

        async function load(page) {
            const requestSequence = ++sequence;

            previous.disabled = true;
            next.disabled = true;

            form.querySelectorAll('button').forEach(button => {
                button.disabled = true;
            });

            $('#department-count').textContent = '';
            $('#department-page-info').textContent = '조회 중입니다.';
            $('#department-rows').innerHTML =
                emptyRow('불러오는 중입니다.');

            try {
                const data = await api(
                    '/requests/department/page?' + queryParams(page),
                    { cache: 'no-store' }
                );

                if (requestSequence !== sequence) {
                    return;
                }

                currentPage = data.page;

                const params = queryParams(currentPage);

                history.replaceState(
                    null,
                    '',
                    BASE + '/department-requests?' + params
                );

                $('#department-rows').innerHTML = data.items.length
                    ? data.items.map(item => {
                        const href = BASE
                            + '/department-requests/'
                            + encodeURIComponent(item.requestId)
                            + '?'
                            + params;

                        return '<tr>'
                            + '<td>'
                            + (item.isUrgent === 'Y'
                                ? '<span class="badge urgent-badge">긴급</span> '
                                : '')
                            + '<a class="table-title" href="'
                            + esc(href) + '">'
                            + esc(item.title)
                            + '</a>'
                            + '<span class="secondary">'
                            + esc(item.requestNo)
                            + '</span>'
                            + (item.requestType === 'CREATE_VIEW'
                                ? '<span class="secondary">뷰 생성 요청</span>' : '')
                            + '</td>'
                            + '<td>' + esc(item.drafterName) + '</td>'
                            + '<td>' + esc(item.deptName) + '</td>'
                            + '<td>'
                            + badge(
                                statusNames[item.requestStatus]
                                || item.requestStatus
                            )
                            + '<span class="secondary">SQL '
                            + esc(item.sqlCount)
                            + '개</span>'
                            + (item.viewResultStatus
                                ? '<span class="secondary">외부 처리: '
                                    + esc({ PENDING: '처리 대기', COMPLETED: '처리 완료', FAILED: '처리 실패' }[item.viewResultStatus] || item.viewResultStatus)
                                    + '</span>'
                                : '')
                            + '</td>'
                            + '<td>' + esc(dateTime(item.submittedAt)) + '</td>'
                            + '<td>' + esc(dateOnly(item.desiredDate)) + '</td>'
                            + '</tr>';
                    }).join('')
                    : emptyRow('검색 조건에 맞는 기안이 없습니다.');

                $('#department-count').textContent =
                    '총 ' + data.totalCount + '건';

                $('#department-page-info').textContent =
                    data.totalPages === 0
                        ? '페이지 없음'
                        : data.page + ' / ' + data.totalPages + ' 페이지';

                previous.disabled =
                    data.totalPages === 0 || data.page <= 1;

                next.disabled =
                    data.totalPages === 0 || data.page >= data.totalPages;

            } catch (error) {
                if (requestSequence !== sequence) {
                    return;
                }

                $('#department-rows').innerHTML =
                    emptyRow('목록을 불러오지 못했습니다.');

                $('#department-page-info').textContent = '';
                notify(error.message, true);

            } finally {
                if (requestSequence === sequence) {
                    form.querySelectorAll('button').forEach(button => {
                        button.disabled = false;
                    });
                }
            }
        }

        form.addEventListener('submit', event => {
            event.preventDefault();

            filters = {
                keyword: keywordInput.value.trim(),
                status: statusSelect.value
            };

            load(1);
        });

        $('#department-clear').addEventListener('click', () => {
            keywordInput.value = '';
            statusSelect.value = '';
            filters = { keyword: '', status: '' };
            load(1);
        });

        previous.addEventListener('click', () => {
            if (currentPage > 1) {
                load(currentPage - 1);
            }
        });

        next.addEventListener('click', () => {
            load(currentPage + 1);
        });

        const requestedPage = Number(initial.get('page') || '1');

        load(
            Number.isSafeInteger(requestedPage)
                && requestedPage >= 1
                && requestedPage <= 2147483647
                ? requestedPage
                : 1
        );
    }
	
})();
