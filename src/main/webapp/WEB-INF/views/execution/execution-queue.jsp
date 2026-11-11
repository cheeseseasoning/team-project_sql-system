<%@ page pageEncoding="UTF-8" %>
	<%@ include file="/WEB-INF/views/fragments/top.jspf" %>
		<div class="page-heading">
			<div>
				<p class="caption">DB팀 실행 권한 계정</p>
				<h1>쿼리 실행 대기 목록</h1>
			</div><a class="button" href="${pageContext.request.contextPath}/execution-results">쿼리 실행 결과</a>
		</div>
		<section class="panel execution-queue-panel">
			<form id="queue-search" class="queue-search">
			    <label for="queue-department" class="queue-department-field">
			        부서
			        <select id="queue-department" name="deptId" disabled>
			            <option value="">전체 부서</option>
			        </select>
			    </label>

			    <label for="queue-keyword" class="queue-keyword-field">
			        요청 검색
			        <input id="queue-keyword"
			               name="keyword"
			               type="search"
			               placeholder="요청번호, 제목, 작성자"
			               maxlength="120">
			    </label>

			    <div class="queue-search-actions">
			        <button id="queue-search-button"
			                class="button primary"
			                type="submit"
			                disabled>
			            검색
			        </button>

			        <button id="queue-refresh"
			                class="button refresh-button"
			                type="button"
			                title="검색 조건 초기화 및 새로고침"
			                aria-label="검색 조건 초기화 및 새로고침"
			                disabled>
			            <img src="${pageContext.request.contextPath}/icons/RefreshCw.svg"
			                 alt="">
			        </button>
			    </div>
			</form>
			
			<div class="table-scroll">
				<table>
					<thead>
						<tr>
							<th>요청 번호 / 제목</th>
							<th>작성자</th>
							<th>SQL 수</th>
							<th>상태</th>
							<th>제출 시각</th>
							<th></th>
						</tr>
					</thead>
					<tbody id="queue-rows"></tbody>
				</table>
			</div>
			<nav class="form-footer" aria-label="실행 목록 페이지 이동"><button id="queue-prev" class="button" type="button"
					disabled>이전</button><span id="queue-page" class="caption"></span><button id="queue-next"
					class="button" type="button" disabled>다음</button></nav>
		</section>
		<script>
			(function () {
				const size = 10;

				let page = 1;
				let keyword = '';
				let deptId = '';
				let totalPages = 0;
				let departmentsReady = false;
				let loading = false;
				let requestVersion = 0;
				
				const $id = id => document.getElementById(id);
				const fmt = v => v ? new Date(v).toLocaleString('ko-KR', {timeZone: 'Asia/Seoul'}) : '—';
				
				function empty(text) {
					$id('queue-rows').innerHTML =
							'<tr><td colspan="6" class="empty">' + esc(text) + '</td></tr>';
				}
				
				function updateControls() {
				    const disabled = loading || !departmentsReady;

				    $id('queue-department').disabled = disabled;
				    $id('queue-keyword').disabled = disabled;
				    $id('queue-search-button').disabled = disabled;
				    $id('queue-refresh').disabled = disabled;

				    $id('queue-prev').disabled =
				        disabled || page <= 1;

				    $id('queue-next').disabled =
				        disabled || totalPages === 0 || page >= totalPages;
				}

				async function loadDepartments() {
				    const departments = await api('/views/departments');

				    if (!Array.isArray(departments)) {
				        throw new Error('부서 목록 응답을 확인할 수 없습니다.');
				    }

				    const select = $id('queue-department');

				    select.replaceChildren(new Option('전체 부서', ''));

				    for (const department of departments) {
				        select.add(new Option(
				            department.deptName,
				            String(department.deptId)
				        ));
				    }

				    departmentsReady = true;
				}
				
				async function load(next = page) {
				    const version = ++requestVersion;

				    page = next;
				    loading = true;
				    updateControls();

				    $id('queue-rows').setAttribute('aria-busy', 'true');
				    $id('queue-page').textContent = '조회 중입니다.';
				    empty('실행 목록을 불러오는 중입니다.');

				    try {
				        const params = new URLSearchParams({
				            page: String(page),
				            size: String(size),
				            keyword
				        });

				        if (deptId) {
				            params.set('deptId', deptId);
				        }

				        const data = await api(
				            '/executions/ready?' + params.toString()
				        );

				        if (version !== requestVersion) {
				            return;
				        }

				        if (!Array.isArray(data.items)) {
				            throw new Error('실행 목록 응답을 확인할 수 없습니다.');
				        }

				        totalPages = data.totalPages || 0;

				        // 조회 중 데이터가 줄어 현재 페이지가 없어졌다면 보정
				        if (totalPages > 0 && page > totalPages) {
				            await load(totalPages);
				            return;
				        }

				        if (totalPages === 0) {
				            page = 1;
				        }

				        if (data.items.length > 0) {
				            $id('queue-rows').innerHTML = data.items.map(function (row) {
				                const detailUrl =
				                    BASE + '/execution-queue/'
				                    + encodeURIComponent(row.requestId);

				                const statusLabel =
				                    row.batchStatus === 'RUNNING' ? '전체 실행 중'
				                    : row.batchStatus === 'SUCCESS' ? '완료'
				                    : row.batchStatus === 'FAILED' ? '실패'
				                    : row.requestStatus === 'RUNNING' ? '개별 SQL 실행 중'
				                    : row.batchId ? '개별 실행 가능'
				                    : '실행 대기';

				                const statusKind =
				                    row.batchStatus === 'RUNNING'
				                    || row.requestStatus === 'RUNNING'
				                        ? 'waiting'
				                        : row.batchStatus === 'SUCCESS'
				                            ? 'success'
				                            : 'neutral';

				                return '<tr>'
				                    + '<td>'
				                    + '<a class="table-title" href="' + esc(detailUrl) + '">'
				                    + esc(row.requestNo)
				                    + '</a>'
				                    + '<span class="secondary">'
				                    + esc(row.title)
				                    + '</span>'
				                    + '</td>'
				                    + '<td>'
				                    + esc(row.drafterName)
				                    + '<span class="secondary">'
				                    + esc(row.deptName)
				                    + '</span>'
				                    + '</td>'
				                    + '<td class="numeric">'
				                    + esc(row.queryCount)
				                    + '</td>'
				                    + '<td>'
				                    + badge(statusLabel, statusKind)
				                    + '</td>'
				                    + '<td>'
				                    + esc(fmt(row.submittedAt))
				                    + '</td>'
				                    + '<td>'
				                    + '<a class="button" href="' + esc(detailUrl) + '">'
				                    + '상세'
				                    + '</a>'
				                    + '</td>'
				                    + '</tr>';
				            }).join('');

				        } else {
				            empty(
				                deptId || keyword
				                    ? '검색 조건에 맞는 요청이 없습니다.'
				                    : '실행 대기 중인 승인 요청이 없습니다.'
				            );
				        }

				        $id('queue-page').textContent =
				            totalPages > 0
				                ? page + ' / ' + totalPages
				                    + ' 페이지 · 총 ' + data.totalCount + '건'
				                : '검색 결과 없음 · 총 0건';

				    } catch (error) {
				        if (version !== requestVersion) {
				            return;
				        }

				        totalPages = 0;
				        empty(error.message);
				        $id('queue-page').textContent = '';
				        notify(error.message, true);

				    } finally {
				        if (version === requestVersion) {
				            loading = false;
				            $id('queue-rows').setAttribute('aria-busy', 'false');
				            updateControls();
				        }
				    }
				}
				
				$id('queue-search').addEventListener('submit', function (event) {
				    event.preventDefault();

				    if (loading || !departmentsReady) {
				        return;
				    }

				    keyword = $id('queue-keyword').value.trim();
				    deptId = $id('queue-department').value;

				    load(1);
				});

				$id('queue-refresh').addEventListener('click', function () {
				    if (loading || !departmentsReady) {
				        return;
				    }

				    $id('queue-search').reset();

				    keyword = '';
				    deptId = '';

				    load(1);
				});

				$id('queue-prev').addEventListener('click', function () {
				    if (!loading && page > 1) {
				        load(page - 1);
				    }
				});

				$id('queue-next').addEventListener('click', function () {
				    if (!loading && page < totalPages) {
				        load(page + 1);
				    }
				});

				async function initialize() {
				    updateControls();

				    try {
				        await loadDepartments();
				        await load(1);
				    } catch (error) {
				        empty('부서 목록을 불러오지 못했습니다. 화면을 새로고침해 주세요.');
				        notify(error.message, true);
				    }
				}

				initialize();
			})();
		</script>
		<%@ include file="/WEB-INF/views/fragments/bottom.jspf" %>
