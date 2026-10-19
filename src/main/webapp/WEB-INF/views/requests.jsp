<%@ page pageEncoding="UTF-8" %>
	<%@ include file="fragments/top.jspf" %>
		<div class="page-heading">
			<h1>내 조회 요청</h1><a class="button primary" href="${pageContext.request.contextPath}/requests/new">새 조회 요청</a>
		</div>
		<section class="panel">
			<form id="filter-form" class="filter-bar">
				<label>
					요청 검색
					<input name="keyword" id="keyword" placeholder="요청번호 또는 제목" maxlength="120">
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
				<button id="clear-filter" class="button" type="button">초기화</button>
			</form>

			<div class="panel-heading">
				<h2>조회 요청 목록</h2><span id="result-count" class="caption"></span>
			</div>

			<div class="table-scroll">
				<table>
					<thead>
						<tr>
							<th>요청명 / 요청번호</th>
							<th>조회 유형</th>
							<th>처리 현황</th>
							<th>요청일</th>
							<th>희망일</th>
						</tr>
					</thead>
					<tbody id="request-rows"></tbody>
				</table>
			</div>

			<nav class="form-footer" aria-label="조회 요청 페이지 이동">
				<button id="previous-page" type="button" class="button" disabled>
					이전
				</button>

				<span id="page-info" class="caption" aria-live="polite"></span>

				<button id="next-page" type="button" class="button" disabled>
					다음
				</button>
			</nav>
		</section>
		<script>
			let listSequence = 0;

			let currentPage = 1;

			const pageSize = 10;

			let activeFilters = {
				keyword: '',
				status: ''
			};

			function renderDbRequestRows(rows) {
				const classNames = {
					GENERAL: '일반 조회',
					SENSITIVE: '민감 조회'
				};

				const statusNames = {
					DRAFT: '초안',
					PENDING_APPROVAL: '결재 대기',
					READY: '실행 대기',
					RUNNING: '실행 중',
					COMPLETED: '실행 완료',
					PARTIAL_FAILED: '일부 실패',
					FAILED: '실패',
					REJECTED: '반려',
					WITHDRAWN: '회수',
					RETRY_REVIEW: '재실행 검토'
				};

				return rows.map(function (request) {
					const classLabel =
						classNames[request.requestClass]
						|| request.requestClass
						|| '미분류';

					const statusLabel =
						statusNames[request.requestStatus]
						|| request.requestStatus;

					// 시간대 정보 없는 DB 날짜는 날짜 부분만 표시
					const createdDate = request.createdAt
						? request.createdAt.substring(0, 10)
						: '-';
						
					const desiredDate = request.desiredDate
					? request.desiredDate.substring(0, 10)
					: '미지정';

					return '<tr>'
						+ '<td>'
						+ (request.isUrgent === 'Y'
							? '<span class="badge urgent-badge">긴급</span> '
							: '')
						+ '<a class="table-title" href="'
						+ BASE + '/requests/'
						+ encodeURIComponent(request.requestId)
						+ '">'
						+ esc(request.title)
						+ '</a>'
						+ '<span class="secondary">'
						+ esc(request.requestNo)
						+ '</span>'
						+ '</td>'
						+ '<td>' + esc(classLabel) + '</td>'
						+ '<td>'
						+ badge(statusLabel)
						+ '<span class="secondary">'
						+ 'SQL ' + esc(request.sqlCount) + '개'
						+ '</span>'
						+ '</td>'
						+ '<td>' + esc(createdDate) + '</td>'
						+ '<td>' + esc(desiredDate) + '</td>'
						+ '</tr>';
				}).join('');
			}

			async function loadRequests(page = 1) {
				const sequence = ++listSequence;

				$('#previous-page').disabled = true;
				$('#next-page').disabled = true;
				$('#page-info').textContent = '조회 중입니다.';
				$('#result-count').textContent = '';

				$('#request-rows').innerHTML =
					'<tr><td colspan="5" class="empty">'
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

					if (activeFilters.status) {
						params.set('status', activeFilters.status);
					}

					const data = await api(
						'/requests/mine/page?' + params.toString()
					);

					if (sequence !== listSequence) {
						return;
					}

					currentPage = data.page;

					$('#request-rows').innerHTML = data.items.length
						? renderDbRequestRows(data.items)
						: '<tr><td colspan="5" class="empty">'
						+ '검색 조건에 맞는 요청이 없습니다.'
						+ '</td></tr>';

					$('#result-count').textContent =
						'총 ' + data.totalCount + '건';

					$('#page-info').textContent = data.totalPages === 0
						? '페이지 없음'
						: data.page + ' / ' + data.totalPages + ' 페이지';

					$('#previous-page').disabled =
						data.totalPages === 0 || data.page <= 1;

					$('#next-page').disabled =
						data.totalPages === 0 || data.page >= data.totalPages;
				} catch (error) {
					if (sequence !== listSequence) {
						return;
					}

					$('#request-rows').innerHTML =
						'<tr><td colspan="5" class="empty">'
						+ '목록을 불러오지 못했습니다.'
						+ '</td></tr>';

					$('#page-info').textContent = '';
					notify(error.message, true);
				}
			}

			$('#filter-form').addEventListener('submit', function (event) {
				event.preventDefault();

				activeFilters = {
					keyword: $('#keyword').value.trim(),
					status: $('#status').value
				};

				// 검색 조건을 변경하면 첫 페이지부터 조회
				busy(
					$('#filter-form button[type="submit"]'),
					() => loadRequests(1)
				);
			});

			$('#clear-filter').addEventListener('click', function () {
				$('#keyword').value = '';
				$('#status').value = '';

				activeFilters = {
					keyword: '',
					status: ''
				};

				busy(this, () => loadRequests(1));
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

			$('#keyword').value = initialParams.get('keyword') || '';

			const initialStatus = initialParams.get('status') || '';

			if (Array.from($('#status').options)
				.some(option => option.value === initialStatus)) {
				$('#status').value = initialStatus;
			}

			activeFilters = {
				keyword: $('#keyword').value.trim(),
				status: $('#status').value
			};

			loadRequests(1);
		</script>
		<%@ include file="fragments/bottom.jspf" %>