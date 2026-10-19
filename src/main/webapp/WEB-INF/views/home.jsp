<%@ page pageEncoding="UTF-8" %>
	<%@ include file="fragments/top.jspf" %>

		<style>
			#dashboard-counts {
				display: grid;
				grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
				gap: 14px;
			}

			#dashboard-counts .request-count-card {
				display: block;
				padding: 18px;
				border: 1px solid var(--line);
				border-radius: 6px;
				background: var(--bg);
			}

			#dashboard-counts .request-count-card:hover {
				background: var(--tint);
				text-decoration: none;
			}

			#dashboard-counts strong {
				display: block;
				margin-top: 8px;
				font-size: 28px;
				color: var(--accent);
			}
		</style>

		<div class="page-heading">
			<div>
				<h1>업무 대시보드</h1>
				<span id="dashboard-date" class="caption"></span>
			</div>

			<button id="dashboard-refresh" type="button" class="button">
				새로고침
			</button>
		</div>

		<section class="panel">
			<div class="panel-heading">
				<h2>내 요청 현황</h2>
				<span class="caption">상태를 누르면 해당 목록으로 이동합니다.</span>
			</div>

			<div id="dashboard-counts">
				요청 현황을 불러오는 중입니다.
			</div>
		</section>

		<section class="panel section-gap">
			<div class="panel-heading">
				<h2>업무 바로가기</h2>
			</div>

			<div class="shortcuts">
				<a href="${pageContext.request.contextPath}/requests/new">
					<img src="${pageContext.request.contextPath}/icons/SquareTerminal.svg" alt="">
					<strong>새 조회 요청</strong>
					<span>요청 내용과 SQL 작성</span>
				</a>

				<a href="${pageContext.request.contextPath}/requests">
					<img src="${pageContext.request.contextPath}/icons/Files.svg" alt="">
					<strong>내 조회 요청</strong>
					<span>검색·수정 및 진행 상태 확인</span>
				</a>
			</div>
		</section>

		<section class="panel section-gap">
			<div class="panel-heading">
				<h2>최근 조회 요청</h2>
				<a href="${pageContext.request.contextPath}/requests">
					전체 보기
				</a>
			</div>

			<div class="table-scroll">
				<table>
					<thead>
						<tr>
							<th>요청명 / 요청번호</th>
							<th>처리 상태</th>
							<th>SQL 수</th>
							<th>작성일</th>
							<th>요청일</th>
						</tr>
					</thead>

					<tbody id="dashboard-recent">
						<tr>
							<td colspan="5" class="empty">
								불러오는 중입니다.
							</td>
						</tr>
					</tbody>
				</table>
			</div>
		</section>

		<section class="panel section-gap">
			<h2>요청 진행 안내</h2>
			<p class="caption section-gap">
				초안 작성·저장 → 제출 → 부서 결재 → DB팀 승인
				→ DB팀 실행 → 결과 확인
			</p>
		</section>

		<script>
			let dashboardSequence = 0;

			const dashboardStatuses = [
				{key: 'totalCount', label: '전체 요청', status: ''},
				{key: 'draftCount', label: '초안', status: 'DRAFT'},
				{
					key: 'pendingApprovalCount',
					label: '결재 대기',
					status: 'PENDING_APPROVAL'
				},
				{key: 'readyCount', label: '실행 대기', status: 'READY'},
				{key: 'runningCount', label: '실행 중', status: 'RUNNING'},
				{
					key: 'retryReviewCount',
					label: '재실행 검토',
					status: 'RETRY_REVIEW'
				},
				{
					key: 'completedCount',
					label: '실행 완료',
					status: 'COMPLETED'
				},
				{
					key: 'partialFailedCount',
					label: '일부 실패',
					status: 'PARTIAL_FAILED'
				},
				{key: 'failedCount', label: '실패', status: 'FAILED'},
				{key: 'rejectedCount', label: '반려', status: 'REJECTED'},
				{key: 'withdrawnCount', label: '회수', status: 'WITHDRAWN'}
			];

			function dashboardStatusName(status) {
				const matched = dashboardStatuses.find(
					item => item.status === status && item.status !== ''
				);

				return matched ? matched.label : status;
			}

			function renderDashboardCounts(summary) {
				return dashboardStatuses.map(function (item) {
					const href = BASE + '/requests'
						+ (item.status
							? '?status=' + encodeURIComponent(item.status)
							: '');

					return '<a class="request-count-card" href="'
						+ href + '">'
						+ '<span>' + esc(item.label) + '</span>'
						+ '<strong>' + esc(summary[item.key]) + '</strong>'
						+ '</a>';
				}).join('');
			}

			function renderDashboardRecent(requests) {
				if (requests.length === 0) {
					return '<tr><td colspan="5" class="empty">'
						+ '아직 작성한 요청이 없습니다.'
						+ '</td></tr>';
				}

				return requests.map(function (request) {
					const href = BASE + '/requests/'
						+ encodeURIComponent(request.requestId);

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
						+ '<a class="table-title" href="' + href + '">'
						+ esc(request.title)
						+ '</a>'
						+ '<span class="secondary">'
						+ esc(request.requestNo)
						+ '</span>'
						+ '</td>'
						+ '<td>'
						+ badge(dashboardStatusName(request.requestStatus))
						+ '</td>'
						+ '<td>' + esc(request.sqlCount) + '개</td>'
						+ '<td>' + esc(desiredDate) + '</td>'
						+ '<td>' + esc(createdDate) + '</td>'
						+ '</tr>';
				}).join('');
			}

			async function loadDashboard() {
				const sequence = ++dashboardSequence;

				try {
					const data = await api('/requests/dashboard');

					if (sequence !== dashboardSequence) {
						return;
					}

					$('#dashboard-counts').innerHTML =
						renderDashboardCounts(data.summary);

					$('#dashboard-recent').innerHTML =
						renderDashboardRecent(data.recentRequests);

					$('#dashboard-date').textContent =
						date(new Date().toISOString()) + ' · 본인 요청 기준';
				} catch (error) {
					if (sequence !== dashboardSequence) {
						return;
					}

					$('#dashboard-counts').textContent =
						'요청 현황을 불러오지 못했습니다.';

					$('#dashboard-recent').innerHTML =
						'<tr><td colspan="5" class="empty">'
						+ '최근 요청을 불러오지 못했습니다.'
						+ '</td></tr>';

					notify(error.message, true);
				}
			}

			$('#dashboard-refresh').addEventListener('click', function () {
				busy(this, loadDashboard);
			});

			loadDashboard();
		</script>

		<%@ include file="fragments/bottom.jspf" %>
