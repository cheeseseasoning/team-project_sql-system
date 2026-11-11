<%@ page pageEncoding="UTF-8" %>
	<%@ include file="/WEB-INF/views/fragments/top.jspf" %>
		<div class="page-heading">
		    <h1><c:out value="${pageTitle}"/></h1>
		    <a class="button primary"
		       href="${pageContext.request.contextPath}/requests/new">새 조회 요청</a>
		</div>
		
		<section id="my-request-list" class="panel">
			<form id="filter-form" class="filter-bar">
				<label>
					요청 검색
					<input name="keyword" id="keyword" placeholder="요청번호 또는 제목" maxlength="120">
				</label>

				<label class="request-type-filter">
				    요청 유형
				    <select id="request-type" name="requestType">
				        <option value="">전체</option>
				        <option value="SELECT">조회</option>
				        <option value="DB_CHANGE">DB 변경</option>
				    </select>
				</label>

				<label>
					처리 상태
					<select id="status" name="status">
						<option value="">전체</option>
						<option value="DRAFT">초안</option>
						<option value="PENDING_APPROVAL">결재 대기</option>
						<option value="READY">실행 대기</option>
						<option value="RUNNING">실행 중</option>
						<option value="RETRY_REVIEW">재실행 검토</option>
						<option value="COMPLETED">실행 완료</option>
						<option value="PARTIAL_FAILED">일부 실패</option>
						<option value="FAILED">실패</option>
						<option value="REJECTED">반려</option>
						<option value="WITHDRAWN">회수</option>
					</select>
				</label>

				<button class="button primary" type="submit">검색</button>

				<button id="clear-filter"
				        class="button refresh-button"
				        type="button"
				        title="검색 조건 초기화 및 새로고침"
				        aria-label="검색 조건 초기화 및 새로고침">
				    <img src="${pageContext.request.contextPath}/icons/RefreshCw.svg"
				         alt="">
				</button>
			</form>

			<div class="panel-heading request-list-heading">
			    <h2>조회 요청 목록</h2>

			    <span id="result-count"
			          class="caption request-list-count"
			          aria-live="polite"></span>
			</div>

			<div class="table-scroll">
			    <table class="request-list-table">
			        <caption class="sr-only">내 기안함 목록</caption>

			        <colgroup>
			            <col class="request-number-column">
			            <col class="request-title-column">
			            <col class="request-type-column">
			            <col class="request-drafter-column">
			            <col class="request-department-column">
			            <col class="request-status-column">
			            <col class="request-date-column">
			        </colgroup>

			        <thead>
			            <tr>
			                <th scope="col">요청번호</th>
			                <th scope="col">요청명</th>
			                <th scope="col">유형</th>
			                <th scope="col">작성자</th>
			                <th scope="col">부서</th>
			                <th scope="col">처리현황</th>
			                <th scope="col">요청일</th>
			            </tr>
			        </thead>

			        <tbody id="request-rows"></tbody>
			    </table>
			</div>

			<nav id="request-pagination"
			     class="request-pagination"
			     aria-label="조회 요청 페이지 이동">
			    <button id="previous-page"
			            type="button"
			            class="request-page-direction"
			            disabled>
			        <span aria-hidden="true">‹</span>
			        이전
			    </button>

			    <div id="page-numbers" class="request-page-numbers"></div>

			    <button id="next-page"
			            type="button"
			            class="request-page-direction"
			            disabled>
			        다음
			        <span aria-hidden="true">›</span>
			    </button>

			    <span id="page-info"
			          class="sr-only"
			          aria-live="polite"></span>
			</nav>
		</section>
		<script>
			let listSequence = 0;

			let currentPage = 1;

			const pageSize = 10;

			let activeFilters = {
				keyword: '',
				requestType: '',
				status: ''
			};

			function renderDbRequestRows(rows) {
			    const statuses = {
			        DRAFT: {
			            label: '작성 중',
			            className: 'is-draft'
			        },
			        PENDING_APPROVAL: {
			            label: '결재 진행',
			            className: 'is-pending'
			        },
			        READY: {
			            label: '실행 대기',
			            className: 'is-ready'
			        },
			        RUNNING: {
			            label: '실행 중',
			            className: 'is-running'
			        },
			        COMPLETED: {
			            label: '실행 완료',
			            className: 'is-completed'
			        },
			        PARTIAL_FAILED: {
			            label: '일부 실패',
			            className: 'is-partial-failed'
			        },
			        FAILED: {
			            label: '실패',
			            className: 'is-failed'
			        },
			        REJECTED: {
			            label: '반려',
			            className: 'is-rejected'
			        },
			        WITHDRAWN: {
			            label: '회수',
			            className: 'is-withdrawn'
			        },
			        RETRY_REVIEW: {
			            label: '재실행 검토',
			            className: 'is-pending'
			        }
			    };

			    const changeTypes = {
			        CREATE_VIEW: '뷰 생성',
			        INSERT: '데이터 추가',
			        UPDATE: '데이터 수정',
			        DELETE: '데이터 삭제'
			    };

			    return rows.map(function (request) {
			        const changeType = changeTypes[request.requestType];

			        const typeLabel = changeType
			            ? 'DB 변경'
			            : request.requestClass === 'SENSITIVE'
			                ? '민감 조회'
			                : '조회';

			        const status = statuses[request.requestStatus] || {
			            label: request.requestStatus || '상태 확인 필요',
			            className: 'is-draft'
			        };

			        // DB 날짜 문자열에서 날짜 부분만 사용한다.
			        const fullDate = request.createdAt
			            ? request.createdAt.substring(0, 10)
			            : '';

			        const shortDate = fullDate
			            ? fullDate.substring(5).replace('-', '.')
			            : '—';

			        const urgent = request.isUrgent === 'Y'
			            ? '<span class="badge urgent-badge">긴급</span>'
			            : '';

			        return '<tr>'
			            + '<td class="request-number-cell" title="'
			            + esc(request.requestNo) + '">'
			            + esc(request.requestNo)
			            + '</td>'

			            + '<td>'
			            + '<div class="request-title-content">'
			            + urgent
			            + '<a class="request-title-link" href="'
			            + BASE + '/requests/'
			            + encodeURIComponent(request.requestId)
			            + '" title="' + esc(request.title) + '">'
			            + esc(request.title)
			            + '</a>'
			            + '</div>'
			            + '</td>'

			            + '<td title="' + esc(changeType || typeLabel) + '">'
			            + esc(typeLabel)
			            + '</td>'

			            + '<td>' + esc(request.drafterName || '—') + '</td>'
			            + '<td class="request-department-cell" title="'
			            + esc(request.deptName || '') + '">'
			            + esc(request.deptName || '—')
			            + '</td>'

			            + '<td>'
			            + '<span class="request-status-text '
			            + status.className + '">'
			            + esc(status.label)
			            + '</span>'
			            + '</td>'

			            + '<td class="request-date-cell" title="'
			            + esc(fullDate) + '">'
			            + esc(shortDate)
			            + '</td>'

			            + '</tr>';
			    }).join('');
			}

			function renderRequestPagination(page, totalPages) {
			    const pagination = $('#request-pagination');
			    const numbers = $('#page-numbers');

			    numbers.replaceChildren();
			    pagination.hidden = totalPages === 0;

			    $('#previous-page').disabled =
			        totalPages === 0 || page <= 1;

			    $('#next-page').disabled =
			        totalPages === 0 || page >= totalPages;

			    $('#page-info').textContent = totalPages === 0
			        ? '검색 결과가 없습니다.'
			        : '전체 ' + totalPages + '페이지 중 ' + page + '페이지';

			    if (totalPages === 0) return;

			    // 현재 페이지 주변의 번호를 최대 5개 표시한다.
			    const startPage = Math.max(
			        1,
			        Math.min(page - 2, totalPages - 4)
			    );
			    const endPage = Math.min(totalPages, startPage + 4);

			    for (let number = startPage; number <= endPage; number++) {
			        const button = document.createElement('button');

			        button.type = 'button';
			        button.className = 'request-page-number';
			        button.textContent = String(number);
			        button.setAttribute('aria-label', number + '페이지');

			        if (number === page) {
			            button.setAttribute('aria-current', 'page');
			        }

			        button.addEventListener('click', function () {
			            if (number !== currentPage) {
			                loadRequests(number);
			            }
			        });

			        numbers.appendChild(button);
			    }
			}

			async function loadRequests(page = 1) {
			    const sequence = ++listSequence;

			    $('#request-pagination').querySelectorAll('button')
			        .forEach(button => {
			            button.disabled = true;
			        });

			    $('#page-info').textContent = '조회 중입니다.';
			    $('#result-count').textContent = '';

			    $('#request-rows').innerHTML =
			        '<tr><td colspan="7" class="empty">'
			        + '불러오는 중입니다.'
			        + '</td></tr>';

			    try {
			        const params = new URLSearchParams({
			            page: String(page),
			            size: String(pageSize)
			        });

			        if (activeFilters.keyword) {
			            params.set('keyword', activeFilters.keyword);
			        }

					if (activeFilters.requestType) {
					    params.set('requestType', activeFilters.requestType);
					}

			        if (activeFilters.status) {
			            params.set('status', activeFilters.status);
			        }

			        const data = await api(
			            '/requests/mine/page?' + params.toString()
			        );

			        if (sequence !== listSequence) return;

			        if (!data || !Array.isArray(data.items)) {
			            throw Error('조회 요청 목록 응답을 확인할 수 없습니다.');
			        }

			        currentPage = data.page;

			        $('#request-rows').innerHTML = data.items.length
			            ? renderDbRequestRows(data.items)
			            : '<tr><td colspan="7" class="empty">'
			                + '검색 조건에 맞는 요청이 없습니다.'
			                + '</td></tr>';

			        $('#result-count').textContent =
			            '총 ' + Number(data.totalCount).toLocaleString('ko-KR')
			            + '건';

			        renderRequestPagination(
			            data.page,
			            data.totalPages
			        );
			    } catch (error) {
			        if (sequence !== listSequence) return;

			        $('#request-rows').innerHTML =
			            '<tr><td colspan="7" class="empty">'
			            + '목록을 불러오지 못했습니다.'
			            + '</td></tr>';

			        $('#request-pagination').hidden = true;
			        $('#page-info').textContent = '';

			        notify(error.message, true);
			    }
			}

			$('#filter-form').addEventListener('submit', function (event) {
				event.preventDefault();

				activeFilters = {
					keyword: $('#keyword').value.trim(),
					requestType: $('#request-type').value,
					status: $('#status').value
				};

				// 검색 조건을 변경하면 첫 페이지부터 조회
				busy(
					$('#filter-form button[type="submit"]'),
					() => loadRequests(1)
				);
			});

			$('#clear-filter').addEventListener('click', async function () {
			    if (this.disabled) return;

			    this.disabled = true;
			    this.setAttribute('aria-busy', 'true');

			    try {
			        $('#keyword').value = '';
			        $('#request-type').value = '';
			        $('#status').value = '';

			        activeFilters = {
			            keyword: '',
			            requestType: '',
			            status: ''
			        };

			        await loadRequests(1);
			    } finally {
			        this.disabled = false;
			        this.removeAttribute('aria-busy');
			    }
			});

			$('#previous-page').addEventListener('click', function () {
				if (currentPage > 1) {
					loadRequests(currentPage - 1);
				}
			});

			$('#next-page').addEventListener('click', function () {
				loadRequests(currentPage + 1);
			});

			// 공통 검색창에서 넘어온 조건 반영
			const initialParams = new URLSearchParams(location.search);

			const initialRequestType =
			    initialParams.get('requestType') || '';

			if (Array.from($('#request-type').options)
			    .some(option => option.value === initialRequestType)) {
			    $('#request-type').value = initialRequestType;
			}

			$('#keyword').value = initialParams.get('keyword') || '';

			const initialStatus = initialParams.get('status') || '';

			if (Array.from($('#status').options)
				.some(option => option.value === initialStatus)) {
				$('#status').value = initialStatus;
			}

			activeFilters = {
				keyword: $('#keyword').value.trim(),
				requestType: $('#request-type').value,
				status: $('#status').value
			};

			loadRequests(1);
		</script>
		<%@ include file="/WEB-INF/views/fragments/bottom.jspf" %>
