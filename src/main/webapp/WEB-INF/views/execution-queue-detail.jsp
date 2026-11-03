<%@ page pageEncoding="UTF-8" %>
	<%@ include file="fragments/top.jspf" %>
		<div class="page-heading">
			<div><a class="back-link" href="${pageContext.request.contextPath}/execution-queue">← 실행 목록</a>
				<h1 id="detail-title">실행 요청 상세</h1>
			</div><button id="detail-refresh" class="button" type="button">상태 새로고침</button>
		</div>
		<section class="panel">
			<div class="panel-heading">
				<div>
					<h2>실행 정보</h2>
					<p class="caption">
						최종 승인된 요청의 저장 SQL을 서버에서 다시 검증한 뒤 실행합니다.
					</p>
				</div><span id="detail-status" class="badge neutral">조회 중</span>
			</div>
			<dl id="request-info" class="queue-info"></dl>
			<div id="batch-actions" class="execution-queue-actions"></div>
			<p id="detail-message" class="execution-queue-message" role="status"></p>
		</section>
		<section class="panel section-gap">
			<div class="panel-heading">
				<div>
					<h2>SQL 원문</h2>
					<p class="caption">
					    SQL을 개별 실행하거나 미실행 SQL을 요청 순서대로 한 번에 실행할 수 있습니다.
					</p>
				</div><span id="sql-count" class="caption"></span>
			</div>
			<div id="sql-items" class="queue-sql-list"></div>
		</section>
		<script>
		(function () {
		    const requestId = '${requestId}';
		    const $id = id => document.getElementById(id);

		    let detail = null;
		    let posting = false;
		    let dataFresh = false;
		    let timer = null;
		    let loadVersion = 0;
		    let disposed = false;

			// 기안서 전체의 처리 상태
			const requestStateLabel = {
			    READY: '실행 대기',
			    RUNNING: '처리 중',
			    COMPLETED: '실행 완료',
			    PARTIAL_FAILED: '일부 실패',
			    FAILED: '실패'
			};

			// SQL 한 번의 실행 상태
			const sqlStateLabel = {
			    PENDING: '실행 대기',
			    RUNNING: '실행 중',
			    SUCCESS: '성공',
			    FAILED: '실패'
			};

		    const terminalStatuses = new Set([
		        'COMPLETED', 'PARTIAL_FAILED', 'FAILED'
		    ]);

		    const dt = value => value
		        ? new Date(value).toLocaleString('ko-KR', {
		            timeZone: 'Asia/Seoul'
		        })
		        : '—';

		    function showMessage(message, error = false) {
		        const element = $id('detail-message');
		        element.textContent = message || '';
		        element.classList.toggle('error-text', error);
		    }

		    function sqlItems(data) {
		        return Array.isArray(data.sqlItems) ? data.sqlItems : [];
		    }

		    function isRequestRunnable(data) {
		        return data.approvalStatus === 'APPROVED'
		            && ['READY', 'RUNNING'].includes(data.requestStatus);
		    }

		    function hasActiveSql(data) {
		        return sqlItems(data).some(item =>
		            item.executionStatus === 'PENDING'
		            || item.executionStatus === 'RUNNING'
		        );
		    }

		    function canRunIndividual(data, item) {
		        return dataFresh
		            && !posting
		            && isRequestRunnable(data)
		            && data.batchStatus !== 'RUNNING'
		            && !hasActiveSql(data)
		            && item.executionId == null
		            && item.executionStatus == null;
		    }

			function canRunWhole(data) {
			    const items = sqlItems(data);

			    if (!dataFresh
			        || posting
			        || !isRequestRunnable(data)
			        || items.length === 0) {
			        return false;
			    }

			    // 기존에 미리 등록해둔 배치는 기존 전체 실행으로 처리한다.
			    if (data.batchId != null) {
			        return data.batchStatus === 'PENDING'
			            && data.requestStatus === 'READY'
			            && items.every(item =>
			                item.executionId != null
			                && item.executionStatus === 'PENDING'
			            );
			    }

			    // 새 전체 실행: 개별 실행이 끝난 뒤 남은 SQL도 실행할 수 있다.
			    return data.batchStatus == null
			        && !hasActiveSql(data)
			        && items.some(item =>
			            item.executionId == null
			            && item.executionStatus == null
			        );
			}
			
		    function needsPolling(data) {
		        if (!data || terminalStatuses.has(data.requestStatus)) {
		            return false;
		        }

		        const items = sqlItems(data);

		        const running = data.batchStatus === 'RUNNING'
		            || items.some(item => item.executionStatus === 'RUNNING');

		        // SQL 저장 직후, 기안서 완료 집계가 아직 반영되지 않은 구간.
		        const aggregating = data.requestStatus === 'RUNNING'
		            && items.length > 0
		            && items.every(item =>
		                item.executionStatus === 'SUCCESS'
		                || item.executionStatus === 'FAILED'
		            );

		        return running || aggregating;
		    }

		    function stopPolling() {
		        if (timer != null) {
		            clearTimeout(timer);
		            timer = null;
		        }
		    }

		    function schedulePolling() {
		        if (disposed || posting || !dataFresh || !needsPolling(detail)) {
		            stopPolling();
		            return;
		        }

		        if (timer != null) {
		            return;
		        }

		        timer = setTimeout(() => {
		            timer = null;
		            load();
		        }, 1500);
		    }

			function requestLabel(data) {
			    if (terminalStatuses.has(data.requestStatus)) {
			        return requestStateLabel[data.requestStatus];
			    }

			    const items = sqlItems(data);

			    if (data.batchStatus === 'RUNNING') {
			        return '전체 SQL 실행 중';
			    }

			    if (items.some(item => item.executionStatus === 'RUNNING')) {
			        return '개별 SQL 실행 중';
			    }

			    if (data.requestStatus === 'RUNNING') {
			        const allFinished = items.length > 0
			            && items.every(item =>
			                item.executionStatus === 'SUCCESS'
			                || item.executionStatus === 'FAILED'
			            );

			        return allFinished
			            ? '결과 집계 중'
			            : '일부 SQL 처리 완료';
			    }

			    return requestStateLabel[data.requestStatus]
			        || data.requestStatus;
			}

		    function statusKind(status) {
		        if (status === 'SUCCESS' || status === 'COMPLETED') {
		            return 'success';
		        }

		        if (status === 'FAILED' || status === 'PARTIAL_FAILED') {
		            return 'danger';
		        }

		        if (status === 'RUNNING' || status === 'PENDING') {
		            return 'waiting';
		        }

		        return 'neutral';
		    }

		    function runButton(data, item) {
		        const enabled = canRunIndividual(data, item);

		        return '<button type="button" class="button sql-run"'
		            + ' data-query="' + esc(item.queryId) + '"'
		            + (enabled ? '' : ' disabled')
		            + '>SQL 실행</button>';
		    }

		    function render(data) {
		        const items = sqlItems(data);

		        $id('detail-title').textContent =
		            (data.requestNo || '요청')
		            + ' · '
		            + (data.title || '제목 없음');

		        $id('sql-count').textContent = 'SQL ' + items.length + '건';

		        const status = $id('detail-status');
		        status.textContent = requestLabel(data);
		        status.className = 'badge ' + statusKind(data.requestStatus);

		        $id('request-info').innerHTML =
		            '<div><dt>요청자</dt><dd>'
		            + esc(data.drafterName) + ' · ' + esc(data.deptName)
		            + '</dd></div>'
		            + '<div><dt>요청 ID</dt><dd>'
		            + esc(data.requestId)
		            + '</dd></div>'
		            + '<div><dt>요청 상태</dt><dd>'
					+ esc(
					    requestStateLabel[data.requestStatus]
					    || data.requestStatus
					)
		            + '</dd></div>'
		            + '<div><dt>최종 승인</dt><dd>'
		            + esc(data.approvalStatus)
		            + '</dd></div>'
		            + '<div><dt>제출 시각</dt><dd>'
		            + esc(dt(data.submittedAt))
		            + '</dd></div>'
		            + '<div><dt>목적</dt><dd>'
		            + esc(data.purpose || '—')
		            + '</dd></div>';

					const hasExecutedSql = items.some(item =>
					    item.executionStatus === 'SUCCESS'
					    || item.executionStatus === 'FAILED'
					);

					const wholeLabel = data.batchId == null && hasExecutedSql
					    ? '남은 SQL 실행'
					    : '전체 SQL 실행';

					let actions =
					    '<button id="run-all"'
					    + ' class="button primary" type="button"'
					    + (canRunWhole(data) ? '' : ' disabled')
					    + '>' + esc(wholeLabel) + '</button>';

					actions += '<a class="button" href="'
					    + BASE + '/execution-results">'
					    + '요청자 실행 결과 메뉴</a>';

					$id('batch-actions').innerHTML = actions;

		        $id('sql-items').innerHTML = items.map(item => {
					const label = item.executionStatus == null
						? '미실행'
						: sqlStateLabel[item.executionStatus]
						    || item.executionStatus;

		            const error = item.errorMessage
		                ? '<p class="queue-error"><strong>실패 원인:</strong> '
		                    + esc(item.errorMessage) + '</p>'
		                : '';

		            return '<article class="queue-sql-card">'
		                + '<div class="panel-heading"><div>'
		                + '<h3>' + esc(item.queryOrder) + '. '
		                + esc(item.queryTitle || 'SQL') + '</h3>'
		                + '<span class="caption">실행 ID: '
		                + esc(item.executionId == null ? '—' : item.executionId)
		                + '</span></div>'
		                + badge(label, statusKind(item.executionStatus))
		                + '</div>'
		                + '<pre><code>' + esc(item.sqlText) + '</code></pre>'
		                + '<div class="queue-sql-footer">'
		                + error
		                + runButton(data, item)
		                + '</div></article>';
		        }).join('') || '<p class="empty">등록된 SQL이 없습니다.</p>';

		        $id('detail-refresh').disabled = posting;

		        bindActions();
		    }

		    async function load() {
		        const version = ++loadVersion;

		        try {
		            const data = await api(
		                '/executions/requests/' + encodeURIComponent(requestId)
		            );

		            // 늦게 도착한 이전 조회 응답으로 화면을 덮어쓰지 않는다.
		            if (disposed || version !== loadVersion) {
		                return false;
		            }

		            if (!data || !Array.isArray(data.sqlItems)) {
		                throw new Error('실행 상태 응답을 확인할 수 없습니다.');
		            }

		            detail = data;
		            dataFresh = true;
		            render(detail);
		            schedulePolling();

		            return true;
		        } catch (error) {
		            if (disposed || version !== loadVersion) {
		                return false;
		            }

		            dataFresh = false;
		            stopPolling();

		            if (detail) {
		                render(detail);
		            }

		            showMessage(
		                error.message
		                + ' 상태 새로고침으로 다시 확인하세요.',
		                true
		            );

		            return false;
		        }
		    }

		    async function submitAction(action, successMessage) {
		        if (posting || !dataFresh || !detail) {
		            return;
		        }

		        posting = true;
		        stopPolling();
		        render(detail);

		        let requestError = null;

		        try {
		            await action();
		            showMessage(successMessage);
		        } catch (error) {
		            requestError = error;
		            showMessage(error.message, true);
		        } finally {
		            // 성공·실패 모두 서버 상태를 다시 읽고 버튼 상태를 결정한다.
		            const refreshed = await load();

		            posting = false;

		            if (!disposed && detail) {
		                render(detail);
		            }

		            if (!disposed && requestError) {
		                showMessage(
		                    requestError.message
		                    + (refreshed
		                        ? ''
		                        : ' 상태 새로고침으로 처리 상태를 확인하세요.'),
		                    true
		                );
		            }

		            schedulePolling();
		        }
		    }

			function bindActions() {
			    const whole = $id('run-all');

			    if (whole) {
			        whole.onclick = async () => {
			            if (!canRunWhole(detail)) {
			                return;
			            }

			            const legacyBatch = detail.batchId != null;

			            const targets = sqlItems(detail).filter(item =>
			                legacyBatch
			                    ? item.executionStatus === 'PENDING'
			                    : item.executionId == null
			                        && item.executionStatus == null
			            );

			            const targetLabel = legacyBatch
			                ? '실행 대기 SQL'
			                : '미실행 SQL';

			            if (!confirm(
			                targetLabel + ' ' + targets.length
			                + '개를 요청 순서대로 실행할까요?'
			            )) {
			                return;
			            }

			            // 이미 등록된 과거 배치는 중복 생성하지 않고 실행만 한다.
			            // 새 요청은 기안서 ID로 자동 배치 등록·실행을 접수한다.
			            const path = legacyBatch
			                ? '/executions/batches/'
			                    + encodeURIComponent(detail.batchId)
			                    + '/run'
			                : '/executions/requests/'
			                    + encodeURIComponent(requestId)
			                    + '/run';

			            await submitAction(
			                () => api(path, { method: 'POST' }),
			                '전체 실행을 접수했습니다.'
			            );
			        };
			    }

			    document.querySelectorAll('.sql-run').forEach(button => {
			        button.onclick = async () => {
			            const queryId = button.dataset.query;

			            const item = sqlItems(detail).find(row =>
			                String(row.queryId) === queryId
			            );

			            if (!item || !canRunIndividual(detail, item)) {
			                return;
			            }

			            if (!confirm('선택한 SQL을 실행할까요?')) {
			                return;
			            }

			            await submitAction(
			                () => api(
			                    '/executions/requests/'
			                    + encodeURIComponent(requestId)
			                    + '/queries/'
			                    + encodeURIComponent(queryId)
			                    + '/run',
			                    { method: 'POST' }
			                ),
			                'SQL 실행을 접수했습니다.'
			            );
			        };
			    });
			}

		    $id('detail-refresh').onclick = async () => {
		        if (posting) {
		            return;
		        }

		        const refreshed = await load();

		        if (refreshed) {
		            showMessage('실행 상태를 갱신했습니다.');
		        }
		    };

		    window.addEventListener('pagehide', () => {
		        disposed = true;
		        loadVersion++;
		        stopPolling();
		    });
			
			window.addEventListener('pageshow', event => {
			    if (!event.persisted) {
			        return;
			    }

			    disposed = false;
			    dataFresh = false;

			    if (detail) {
			        render(detail);
			    }

			    load();
			});

		    load();
		})();
		</script>
		<%@ include file="fragments/bottom.jspf" %>