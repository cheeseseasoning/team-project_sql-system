<%@ page pageEncoding="UTF-8" %>
	<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

	<div class="page-heading home-heading">
	    <h1 class="home-title">
	        <span class="home-title-icon" aria-hidden="true">
	            <img src="${pageContext.request.contextPath}/icons/House.svg" alt="">
	        </span>
	        Home
	    </h1>
	    <a class="button primary" href="${pageContext.request.contextPath}/requests/new">
	        + 데이터 조회 요청
	    </a>
	</div>

	<div class="home-dashboard-grid">
		<section class="panel" aria-labelledby="dashboard-work-title">
	        <div class="panel-heading">
	            <div>
	                <h2 id="dashboard-work-title">내 업무 현황</h2>
	                <span class="caption">현재 상태 기준</span>
	            </div>
	        </div>
	        <div id="dashboard-counts">업무 현황을 불러오는 중입니다.</div>
		</section>

		<section class="panel" aria-labelledby="dashboard-calendar-title">
		    <div class="panel-heading">
		        <div class="dashboard-calendar-heading">
		            <h2 id="dashboard-calendar-title">부서 일정</h2>
		            <c:if test="${not empty deptName}"><span class="dashboard-dept-badge"><c:out value="${deptName}"/></span></c:if>
		        </div>
		        <button id="dashboard-schedule-open" type="button" class="button">+ 일정 등록</button>
		    </div>

		    <div class="dashboard-calendar-nav dashboard-calendar-month-nav">
		        <button id="dashboard-calendar-prev" type="button" class="button" aria-label="이전 달">‹</button>
		        <span id="dashboard-calendar-month" class="caption" aria-live="polite"></span>
		        <button id="dashboard-calendar-next" type="button" class="button" aria-label="다음 달">›</button>
		    </div>
		    <div id="dashboard-calendar" class="dashboard-calendar"></div>
		    <div id="dashboard-calendar-detail" class="dashboard-calendar-detail" aria-live="polite">
		        <p class="caption">일정을 불러오는 중입니다.</p>
		    </div>

		    <dialog id="dashboard-schedule-dialog" class="dashboard-schedule-dialog" aria-labelledby="dashboard-schedule-title">
		        <form id="dashboard-schedule-form">
		            <h2 id="dashboard-schedule-title">부서 일정 등록</h2>
		            <label>날짜 <input name="scheduleDate" type="date" required></label>
		            <label>제목 <input name="title" type="text" maxlength="100" required></label>
		            <label>메모 <textarea name="memo" rows="3" maxlength="500"></textarea></label>
		            <p id="dashboard-schedule-error" role="alert"></p>
		            <div class="dashboard-actions">
		                <button id="dashboard-schedule-cancel" type="button" class="button">취소</button>
		                <button id="dashboard-schedule-submit" type="submit" class="button primary">등록</button>
		            </div>
		        </form>
		    </dialog>
		</section>

		<section class="panel" aria-labelledby="dashboard-recent-title">
	        <div class="panel-heading">
	            <h2 id="dashboard-recent-title">최근 내 요청</h2>
	            <a href="${pageContext.request.contextPath}/requests">전체 보기</a>
	        </div>
	        <div class="table-scroll">
	            <table>
	                <thead>
	                    <tr>
	                        <th scope="col">요청명</th>
	                        <th scope="col">상태</th>
	                        <th scope="col">희망일</th>
	                    </tr>
	                </thead>
	                <tbody id="dashboard-recent">
	                    <tr><td colspan="3" class="empty">불러오는 중입니다.</td></tr>
	                </tbody>
	            </table>
	        </div>
		</section>

		<section class="panel" aria-labelledby="dashboard-notice-title">
		    <div class="panel-heading">
		        <h2 id="dashboard-notice-title">공지사항</h2>
		        <a href="${pageContext.request.contextPath}/notices">전체 보기</a>
		    </div>
		    <div id="dashboard-notices" aria-live="polite">
		        <p class="caption">공지를 불러오는 중입니다.</p>
		    </div>
		</section>
	</div>

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

			function renderDashboardCounts(summary, myApprovalCount) {
			    // 내 요청 집계 3개와 내가 결재할 목록의 건수를 각 화면으로 연결한다.
			    const cards = [
			        {label: '내 요청 결재 중', count: summary.pendingApprovalCount, path: '/requests?status=PENDING_APPROVAL'},
			        {label: '내가 결재할 요청', count: myApprovalCount, path: '/approvals/pending'},
			        {label: '실행 대기', count: summary.readyCount, path: '/requests?status=READY'},
			        {label: '실행 완료', count: summary.completedCount, path: '/requests?status=COMPLETED'}
			    ];

			    return cards.map(function (card) {
			        const countText = card.count == null ? '조회 실패' : card.count + '건';

			        return '<a class="request-count-card" href="' + BASE + card.path + '">'
			            + '<span>' + esc(card.label) + '</span>'
			            + '<strong>' + esc(countText) + '</strong>'
			            + '</a>';
			    }).join('');
			}

			function renderDashboardRecent(requests) {
			    if (requests.length === 0) {
			        return '<tr><td colspan="3" class="empty">아직 작성한 요청이 없습니다.</td></tr>';
			    }

			    // 대시보드에는 최신 3건만 표시하고 나머지는 내 조회 요청에서 확인한다.
			    return requests.slice(0, 3).map(function (request) {
			        const href = BASE + '/requests/' + encodeURIComponent(request.requestId);
			        const desiredDate = request.desiredDate ? request.desiredDate.substring(0, 10) : '미지정';
			        const urgentBadge = request.isUrgent === 'Y' ? '<span class="badge urgent-badge">긴급</span> ' : '';
			        const statusName = dashboardStatusName(request.requestStatus);

			        return '<tr>'
			            + '<td>' + urgentBadge + '<a class="table-title" href="' + href + '">' + esc(request.title) + '</a></td>'
			            + '<td>' + badge(statusName) + '</td>'
			            + '<td>' + esc(desiredDate) + '</td>'
			            + '</tr>';
			    }).join('');
			}

			async function loadDashboard() {
			    const sequence = ++dashboardSequence;

			    try {
			        // 결재 목록 조회가 실패해도 내 요청 현황과 최근 요청은 표시한다.
			        const [data, pendingApprovals] = await Promise.all([
			            api('/requests/dashboard'),
			            api('/approvals/pending').catch(() => null)
			        ]);

			        if (sequence !== dashboardSequence) {
			            return;
			        }

			        const myApprovalCount = pendingApprovals === null ? null : pendingApprovals.length;

			        $('#dashboard-counts').innerHTML = renderDashboardCounts(data.summary, myApprovalCount);
			        $('#dashboard-recent').innerHTML = renderDashboardRecent(data.recentRequests);
			    } catch (error) {
			        if (sequence !== dashboardSequence) {
			            return;
			        }

			        $('#dashboard-counts').textContent = '업무 현황을 불러오지 못했습니다.';
			        $('#dashboard-recent').innerHTML = '<tr><td colspan="3" class="empty">'
			            + '최근 요청을 불러오지 못했습니다.'
			            + '</td></tr>';

			        notify(error.message, true);
			    }
			}

			let dashboardNoticeSequence = 0;

			function renderDashboardNotices(notices) {
			    if (notices.length === 0) {
			        return '<p class="caption">등록된 공지가 없습니다.</p>';
			    }

			    return '<ul class="dashboard-notice-list">'
			        + notices.map(function (notice) {
			            const href = BASE + '/notices?page=1&id=' + encodeURIComponent(notice.noticeId);
			            const registeredDate = notice.createdAt ? notice.createdAt.substring(5, 10).replace('-', '.') : '-';

			            return '<li>'
			                + '<a class="dashboard-notice-title" href="' + esc(href) + '">' + esc(notice.title) + '</a>'
			                + '<span class="caption">' + esc(registeredDate) + '</span>'
			                + '</li>';
			        }).join('')
			        + '</ul>';
			}

			async function loadDashboardNotices() {
			    const sequence = ++dashboardNoticeSequence;
			    const container = $('#dashboard-notices');

			    try {
			        // 공지 목록 API의 첫 페이지에서 최신 공지 3건을 가져온다.
			        const data = await api('/notices?page=1&size=3');

			        // 새로고침이 겹치면 마지막 요청의 응답만 화면에 반영한다.
			        if (sequence === dashboardNoticeSequence) {
			            container.innerHTML = renderDashboardNotices(data.notices);
			        }
			    } catch (error) {
			        if (sequence === dashboardNoticeSequence) {
			            container.textContent = '공지를 불러오지 못했습니다.';
			        }
			    }
			}
			
			// 오늘은 서울 날짜로 잡고, API의 날짜 값과 같은 YYYY-MM-DD 키를 사용한다.
			const calendarToday = new Intl.DateTimeFormat('sv-SE', {
			    timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit'
			}).format(new Date());

			let calendarMonth = new Date(
			    Number(calendarToday.slice(0, 4)), Number(calendarToday.slice(5, 7)) - 1, 1
			);
			let selectedCalendarDate = calendarToday;
			let calendarSequence = 0;
			let schedulesByDate = new Map();
			let calendarMessage = '일정을 불러오는 중입니다.';

			function getCalendarMonthKey() {
			    return calendarMonth.getFullYear() + '-'
			        + String(calendarMonth.getMonth() + 1).padStart(2, '0');
			}

			function renderCalendarDetail() {
			    const container = $('#dashboard-calendar-detail');

			    if (calendarMessage) {
			        container.innerHTML = '<p class="caption">' + esc(calendarMessage) + '</p>';
			        return;
			    }

			    const schedules = schedulesByDate.get(selectedCalendarDate) || [];
			    const [year, month, day] = selectedCalendarDate.split('-').map(Number);
			    const dateLabel = new Intl.DateTimeFormat('ko-KR', {
			        month: 'long', day: 'numeric', weekday: 'long'
			    }).format(new Date(year, month - 1, day));

			    const heading = '<strong>' + esc(dateLabel) + ' · 일정 ' + schedules.length + '건</strong>';

			    if (schedules.length === 0) {
			        container.innerHTML = heading + '<p class="caption">등록된 일정이 없습니다.</p>';
			        return;
			    }

			    container.innerHTML = heading + '<ul>' + schedules.map(function (schedule) {
			        const title = esc(schedule.title);
			        return '<li>' + (schedule.memo
			            ? '<details><summary>' + title + '</summary><p class="caption">' + esc(schedule.memo) + '</p></details>'
			            : title) + '</li>';
			    }).join('') + '</ul>';
			}

			function renderDashboardCalendar() {
			    const year = calendarMonth.getFullYear();
			    const month = calendarMonth.getMonth();
			    const prefix = getCalendarMonthKey() + '-';
			    const lastDay = new Date(year, month + 1, 0).getDate();

			    $('#dashboard-calendar-month').textContent = year + '년 ' + (month + 1) + '월';

			    const weekdays = ['일', '월', '화', '수', '목', '금', '토'].map(function (day) {
			        return '<span class="caption">' + day + '</span>';
			    }).join('');
			    const blanks = '<span aria-hidden="true"></span>'.repeat(calendarMonth.getDay());
			    const days = [];

			    for (let day = 1; day <= lastDay; day++) {
			        const key = prefix + String(day).padStart(2, '0');
			        const count = (schedulesByDate.get(key) || []).length;

			        days.push('<button type="button" data-date="' + key + '"'
			            + ' aria-pressed="' + (key === selectedCalendarDate) + '"'
			            + ' aria-label="' + key + ' 일정 ' + count + '건">'
			            + day + (count > 0 ? '<span class="calendar-dot" aria-hidden="true"></span>' : '')
			            + '</button>');
			    }

			    $('#dashboard-calendar').innerHTML = weekdays + blanks + days.join('');
			    renderCalendarDetail();
			}

			async function loadDashboardCalendar() {
			    const sequence = ++calendarSequence;
			    const month = getCalendarMonthKey();

			    schedulesByDate = new Map();
			    calendarMessage = '일정을 불러오는 중입니다.';
			    renderDashboardCalendar();

			    try {
			        const schedules = await api('/department-schedules?month=' + encodeURIComponent(month));

			        // 월을 빠르게 이동해도 마지막에 선택한 월의 응답만 반영한다.
			        if (sequence !== calendarSequence) {
			            return;
			        }

			        const grouped = new Map();
			        for (const schedule of schedules) {
			            const day = schedule.scheduleDate;
			            if (!grouped.has(day)) {
			                grouped.set(day, []);
			            }
			            grouped.get(day).push(schedule);
			        }

			        schedulesByDate = grouped;
			        calendarMessage = '';
			    } catch (error) {
			        if (sequence !== calendarSequence) {
			            return;
			        }

			        calendarMessage = error.message || '일정을 불러오지 못했습니다.';
			    }

			    renderDashboardCalendar();
			}

			function moveDashboardCalendarMonth(offset) {
			    calendarMonth = new Date(calendarMonth.getFullYear(), calendarMonth.getMonth() + offset, 1);
			    const prefix = getCalendarMonthKey() + '-';

			    selectedCalendarDate = calendarToday.startsWith(prefix) ? calendarToday : prefix + '01';
			    loadDashboardCalendar();
			}

			$('#dashboard-calendar-prev').addEventListener('click', function () {
			    moveDashboardCalendarMonth(-1);
			});

			$('#dashboard-calendar-next').addEventListener('click', function () {
			    moveDashboardCalendarMonth(1);
			});

			$('#dashboard-calendar').addEventListener('click', function (event) {
			    const button = event.target.closest('button[data-date]');
			    if (!button) {
			        return;
			    }

			    this.querySelector('[aria-pressed="true"]')?.setAttribute('aria-pressed', 'false');
			    selectedCalendarDate = button.dataset.date;
			    button.setAttribute('aria-pressed', 'true');
			    renderCalendarDetail();
			});

			const scheduleDialog = $('#dashboard-schedule-dialog');
			const scheduleForm = $('#dashboard-schedule-form');

			$('#dashboard-schedule-open').addEventListener('click', function () {
			    scheduleForm.reset();
			    scheduleForm.elements.scheduleDate.value = selectedCalendarDate;
			    $('#dashboard-schedule-error').textContent = '';
			    scheduleDialog.showModal();
			});

			$('#dashboard-schedule-cancel').addEventListener('click', function () {
			    scheduleDialog.close();
			});

			scheduleForm.addEventListener('submit', function (event) {
			    event.preventDefault();
			    const submitButton = $('#dashboard-schedule-submit');
			    const errorMessage = $('#dashboard-schedule-error');
			    const values = new FormData(this);
			    const scheduleDate = values.get('scheduleDate');
			    const title = values.get('title').trim();

			    if (!title) {
			        errorMessage.textContent = '일정 제목을 입력하세요.';
			        this.elements.title.focus();
			        return;
			    }

			    errorMessage.textContent = '';
			    busy(submitButton, async function () {
			        try {
			            await api('/department-schedules', {
			                method: 'POST',
			                body: JSON.stringify({scheduleDate, title, memo: values.get('memo')})
			            });
			        } catch (error) {
			            errorMessage.textContent = error.message || '일정을 등록하지 못했습니다.';
			            return;
			        }

			        // 등록한 날짜가 보이도록 해당 월로 이동한 뒤 일정을 다시 조회한다.
			        const [year, month] = scheduleDate.split('-').map(Number);
			        calendarMonth = new Date(year, month - 1, 1);
			        selectedCalendarDate = scheduleDate;
			        scheduleDialog.close();
			        await loadDashboardCalendar();
			    });
			});

			function refreshDashboard() {
			    // 각 영역이 오류를 따로 처리하므로 달력 조회에 실패해도 공지와 업무 현황은 갱신한다.
			    return Promise.all([
			        loadDashboard(),
			        loadDashboardNotices(),
			        loadDashboardCalendar()
			    ]);
			}

			refreshDashboard();
		</script>

		<%@ include file="/WEB-INF/views/fragments/bottom.jspf" %>
