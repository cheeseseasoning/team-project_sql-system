<%@ page pageEncoding="UTF-8" %>
	<%@ include file="fragments/top.jspf" %>
		<div class="page-heading">
			<h1>내 조회 요청</h1><a class="button primary" href="${pageContext.request.contextPath}/requests/new">새 조회 요청</a>
		</div>
		<section class="panel">
			<form id="filter-form" class="filter-bar"><label>요청 검색<input name="keyword" id="keyword"
						placeholder="요청번호 또는 제목" maxlength="120"></label><label>처리 상태<select id="status">
						<option value="">전체</option>
						<option value="APPROVAL_PENDING">결재 대기</option>
						<option value="COMPLETED">실행 종료</option>
						<option value="FAILED">실패 SQL 포함</option>
					</select></label><button class="button primary" type="submit">조회</button></form>
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
						</tr>
					</thead>
					<tbody id="request-rows"></tbody>
				</table>
			</div>
		</section>
		<script>
			let listSequence = 0;
	
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
			        WITHDRAWN: '회수'
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
	
			        return '<tr>'
			            + '<td>'
			            +     '<strong class="table-title">'
			            +         esc(request.title)
			            +     '</strong>'
			            +     '<span class="secondary">'
			            +         esc(request.requestNo)
			            +     '</span>'
			            + '</td>'
			            + '<td>' + esc(classLabel) + '</td>'
			            + '<td>'
			            +     badge(statusLabel)
			            +     '<span class="secondary">'
			            +         'SQL ' + esc(request.sqlCount) + '개'
			            +     '</span>'
			            + '</td>'
			            + '<td>' + esc(createdDate) + '</td>'
			            + '</tr>';
			    }).join('');
			}
	
			async function loadRequests() {
			    const sequence = ++listSequence;
	
			    $('#request-rows').innerHTML =
			        '<tr><td colspan="4" class="empty">'
			        + '불러오는 중입니다.'
			        + '</td></tr>';
	
			    $('#result-count').textContent = '';
	
			    try {
			        // 실제 Oracle 기반 목록 API
			        const rows = await api('/requests/mine');
	
			        if (sequence !== listSequence) {
			            return;
			        }
	
			        $('#request-rows').innerHTML = rows.length
			            ? renderDbRequestRows(rows)
			            : '<tr><td colspan="4" class="empty">'
			                + '저장된 요청이 없습니다.'
			                + '</td></tr>';
	
			        $('#result-count').textContent = '총 ' + rows.length + '건';
	
			    } catch (error) {
			        if (sequence !== listSequence) {
			            return;
			        }
	
			        $('#request-rows').innerHTML =
			            '<tr><td colspan="4" class="empty">'
			            + '목록을 불러오지 못했습니다.'
			            + '</td></tr>';
	
			        notify(error.message, true);
			    }
			}
	
			// 검색 API를 구현하기 전까지 입력 비활성화
			$('#keyword').disabled = true;
			$('#status').disabled = true;
	
			// 현재 조회 버튼은 목록 새로고침 용도
			$('#filter-form button[type="submit"]').textContent = '새로고침';
	
			$('#filter-form').addEventListener('submit', function (event) {
			    event.preventDefault();
			    busy(event.submitter, loadRequests);
			});
	
			loadRequests();
		</script>
		<%@ include file="fragments/bottom.jspf" %>