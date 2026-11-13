(() => {
    'use strict';

    const PAGE_SIZE = 3;
    const PAGE_BLOCK_SIZE = 10;

    const statusConfig = {
        PENDING_APPROVAL: {
            label: '결재 대기',
            tone: 'warning',
            className: 'is-pending'
        },
        READY: {
            label: '실행 대기',
            tone: 'neutral',
            className: 'is-ready'
        },
        RUNNING: {
            label: '실행 중',
            tone: 'info',
            className: 'is-running'
        },
        RETRY_REVIEW: {
            label: '재실행 검토',
            tone: 'warning',
            className: 'is-pending'
        },
        COMPLETED: {
            label: '실행 완료',
            tone: 'success',
            className: 'is-completed'
        },
        PARTIAL_FAILED: {
            label: '일부 실패',
            tone: 'warning',
            className: 'is-partial-failed'
        },
        FAILED: {
            label: '실패',
            tone: 'error',
            className: 'is-failed'
        },
        REJECTED: {
            label: '반려',
            tone: 'neutral',
            className: 'is-rejected'
        },
        WITHDRAWN: {
            label: '회수',
            tone: 'neutral',
            className: 'is-withdrawn'
        }
    };

    const approvalStageConfig = {
        APPROVER: {
            label: '부서 결재 중',
            tone: 'warning',
            className: 'is-pending'
        },
        INTERMEDIATE: {
            label: '부서 결재 중',
            tone: 'warning',
            className: 'is-pending'
        },
        DIRECTOR: {
            label: '부서장 결재 중',
            tone: 'warning',
            className: 'is-pending'
        },
        DB_EXECUTION: {
            label: 'DB팀 결재 중',
            tone: 'warning',
            className: 'is-pending'
        }
    };

    const resultStatusConfig = {
        PENDING: {
            label: '외부 처리 대기',
            tone: 'warning',
            className: 'is-pending'
        },
        COMPLETED: {
            label: '결과 완료',
            tone: 'success',
            className: 'is-completed'
        },
        FAILED: {
            label: '결과 실패',
            tone: 'error',
            className: 'is-failed'
        }
    };

    const typeNames = {
        SELECT: '조회',
        CREATE_VIEW: '뷰 생성',
        INSERT: '데이터 등록',
        UPDATE: '데이터 수정',
        DELETE: '데이터 삭제'
    };

    function formatDate(value) {
        if (!value) {
            return '-';
        }

        const date = new Date(value);

        if (Number.isNaN(date.getTime())) {
            return String(value).substring(0, 10);
        }

        return new Intl.DateTimeFormat('ko-KR', {
            timeZone: 'Asia/Seoul',
            month: '2-digit',
            day: '2-digit'
        }).format(date).replace(/\s/g, '').replace(/\.$/, '');
    }

    function emptyRow(message) {
        return '<tr><td colspan="7" class="empty">'
            + esc(message)
            + '</td></tr>';
    }

    function getStatus(item) {
        if (item.currentApprovalType
                && approvalStageConfig[item.currentApprovalType]) {
            return approvalStageConfig[item.currentApprovalType];
        }

        if (item.viewResultStatus
                && resultStatusConfig[item.viewResultStatus]) {
            return resultStatusConfig[item.viewResultStatus];
        }

        return statusConfig[item.requestStatus] || {
            label: item.requestStatus || '-',
            tone: 'neutral'
        };
    }

    const form = $('#department-filter-form');

    if (!form) {
        return;
    }

    const keywordInput = $('#department-keyword');
    const typeSelect = $('#department-type');
    const statusSelect = $('#department-status');
    const clearButton = $('#department-clear');
    const rows = $('#department-rows');
    const count = $('#department-count');
    const pagination = $('#department-pagination');

    Object.entries(statusConfig).forEach(([value, config]) => {
        statusSelect.add(new Option(config.label, value));
    });

    const initialParams = new URLSearchParams(location.search);

    keywordInput.value = initialParams.get('keyword') || '';

    const initialType = initialParams.get('type') || '';
    typeSelect.value = Object.hasOwn(typeNames, initialType)
        ? initialType
        : '';

    const initialStatus = initialParams.get('status') || '';
    statusSelect.value = Object.hasOwn(statusConfig, initialStatus)
        ? initialStatus
        : '';

    let filters = {
        keyword: keywordInput.value.trim(),
        type: typeSelect.value,
        status: statusSelect.value
    };

    let currentPage = 1;
    let requestSequence = 0;

    function makeParams(page) {
        const params = new URLSearchParams({
            page: String(page),
            size: String(PAGE_SIZE)
        });

        if (filters.keyword) {
            params.set('keyword', filters.keyword);
        }

        if (filters.type) {
            params.set('type', filters.type);
        }

        if (filters.status) {
            params.set('status', filters.status);
        }

        return params;
    }

    function renderRows(items, params) {
        if (!items || items.length === 0) {
            return emptyRow('검색 조건에 맞는 요청이 없습니다.');
        }

        return items.map(item => {
            const detailUrl = BASE
                + '/department-requests/'
                + encodeURIComponent(item.requestId)
                + '?'
                + params.toString();

            const typeLabel = typeNames[item.requestType]
                || item.requestType
                || '-';

            const status = getStatus(item);

            return '<tr>'
                + '<td class="request-number-cell" title="'
                + esc(item.requestNo || '') + '">'
                + esc(item.requestNo || '-') + '</td>'
                + '<td>'
                + '<div class="request-title-content">'
                + (item.isUrgent === 'Y'
                    ? '<span class="badge urgent-badge">긴급</span>'
                    : '')
                + '<a class="request-title-link" href="'
                + esc(detailUrl) + '" title="' + esc(item.title || '') + '">'
                + esc(item.title || '제목 없음')
                + '</a></div>'
                + '</td>'
                + '<td>' + esc(typeLabel) + '</td>'
                + '<td>' + esc(item.drafterName || '-') + '</td>'
                + '<td class="request-department-cell" title="'
                + esc(item.deptName || '') + '">'
                + esc(item.deptName || '-') + '</td>'
                + '<td>'
                + '<span class="request-status-text '
                + esc(status.className || 'is-draft')
                + '">'
                + esc(status.label)
                + '</span>'
                + '</td>'
                + '<td class="request-date-cell">'
                + esc(formatDate(item.submittedAt)) + '</td>'
                + '</tr>';
        }).join('');
    }

    function renderPagination(totalPages) {
        if (totalPages <= 0) {
            pagination.innerHTML = '';
            return;
        }

        const blockStart =
            Math.floor((currentPage - 1) / PAGE_BLOCK_SIZE)
            * PAGE_BLOCK_SIZE
            + 1;

        const blockEnd = Math.min(
            blockStart + PAGE_BLOCK_SIZE - 1,
            totalPages
        );

        const buttons = [];

        buttons.push(
            '<button type="button"'
            + ' class="department-page-button request-page-direction"'
            + ' data-page="' + (currentPage - 1) + '"'
            + (currentPage <= 1 ? ' disabled' : '')
            + ' aria-label="이전 페이지">'
            + '‹ 이전</button>'
        );

        // 번호 묶음의 간격도 내 기안함 공통 스타일을 사용한다.
        buttons.push('<div class="request-page-numbers">');

        for (let page = blockStart; page <= blockEnd; page += 1) {
            const isCurrent = page === currentPage;

            buttons.push(
                '<button type="button"'
                + ' class="department-page-button request-page-number'
                + (isCurrent ? ' is-current' : '')
                + '" data-page="' + page + '"'
                + (isCurrent ? ' aria-current="page"' : '')
                + ' aria-label="' + page + '페이지">'
                + page
                + '</button>'
            );
        }

        buttons.push('</div>');

        buttons.push(
            '<button type="button"'
            + ' class="department-page-button request-page-direction"'
            + ' data-page="' + (currentPage + 1) + '"'
            + (currentPage >= totalPages ? ' disabled' : '')
            + ' aria-label="다음 페이지">'
            + '다음 ›</button>'
        );

        pagination.innerHTML = buttons.join('');
    }

    async function load(page) {
        const sequence = ++requestSequence;
        const params = makeParams(page);

        form.querySelectorAll('button').forEach(button => {
            button.disabled = true;
        });

        count.textContent = '';
        rows.innerHTML = emptyRow('불러오는 중입니다.');
        pagination.innerHTML = '';

        try {
            const data = await api(
                '/requests/department/page?' + params.toString(),
                { cache: 'no-store' }
            );

            if (sequence !== requestSequence) {
                return;
            }

            currentPage = data.page || 1;

            const actualParams = makeParams(currentPage);

            history.replaceState(
                null,
                '',
                BASE + '/department-requests?'
                    + actualParams.toString()
            );

            rows.innerHTML = renderRows(
                data.items || [],
                actualParams
            );

            const totalCount = Number(data.totalCount) || 0;
            const totalPages = Number(data.totalPages) || 0;
            const firstItem = totalCount === 0
                ? 0
                : (currentPage - 1) * PAGE_SIZE + 1;
            const lastItem = Math.min(
                currentPage * PAGE_SIZE,
                totalCount
            );

            count.textContent =
                '총 ' + totalCount + '건 중 '
                + firstItem + '–' + lastItem + '건';

            renderPagination(totalPages);
        } catch (error) {
            if (sequence !== requestSequence) {
                return;
            }

            rows.innerHTML =
                emptyRow('목록을 불러오지 못했습니다.');
            count.textContent = '';
            pagination.innerHTML = '';
            notify(error.message, true);
        } finally {
            if (sequence === requestSequence) {
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
            type: typeSelect.value,
            status: statusSelect.value
        };

        load(1);
    });

    clearButton.addEventListener('click', () => {
        keywordInput.value = '';
        typeSelect.value = '';
        statusSelect.value = '';

        filters = {
            keyword: '',
            type: '',
            status: ''
        };

        load(1);
    });

    pagination.addEventListener('click', event => {
        const button = event.target.closest('button[data-page]');

        if (!button || button.disabled) {
            return;
        }

        const page = Number(button.dataset.page);

        if (!Number.isInteger(page) || page < 1) {
            return;
        }

        load(page);
    });

    const requestedPage = Number(
        initialParams.get('page') || '1'
    );

    load(
        Number.isSafeInteger(requestedPage) && requestedPage > 0
            ? requestedPage
            : 1
    );
})();