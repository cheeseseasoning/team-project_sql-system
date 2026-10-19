<%@ page pageEncoding="UTF-8" %>
	<%@ include file="fragments/top.jspf" %>
		<div id="form-config" data-mode="<c:out value='${mode}'/>" hidden></div>
		
		<div class="page-heading">
			<h1>조회 요청 작성</h1>
			<span class="badge">작성 중 · 수정 가능</span>
		</div>
		
		<div class="workflow">
			<strong>1 요청·SQL 작성</strong>
			<span>2 검증·제출</span>
			<span>3 결재 또는 실행</span>
			<span>4 결과 확인</span>
		</div>
		
		<form id="request-form" class="panel form-panel">
			<div class="form-grid">
				<label>요청 제목 
					<span class="required">*</span>
					<input id="title" required
						maxlength="120" value="9월 부서별 매출 현황">
				</label>
				<label>조회 목적 
					<span class="required">*</span>
					<input id="purpose" required maxlength="1000" value="월간 영업 실적 보고서 작성">
				</label>
			</div>
			
			<div class="tabs" role="group" aria-label="SQL 작성 방식">
				<button type="button" data-mode="ai">AI로 작성</button>
				<button type="button" data-mode="direct">직접 SQL 작성</button>
			</div>
			
			<section id="ai-section">
				<label for="prompt">자연어 조회 요청</label>
				<textarea id="prompt" rows="3" maxlength="3000">2026년 9월 부서별 매출 합계를 보여줘</textarea>
				<div class="example-actions">
					<span>시연 예시</span>
					<button type="button" data-example="general">매출 요약</button>
					<button type="button" data-example="sensitive">계약 상세</button>
				</div>
				<button id="generate" type="button" class="button primary">SQL 초안 생성</button>
				<p id="explanation" class="explanation" aria-live="polite">
					예시 AI는 매출 요약과 계약 상세 질문을 지원합니다.
				</p>
			</section>
			
			<div class="panel-heading section-gap">
				<h2>SQL 항목
					<span id="sql-count" class="muted">1 / 5</span>
				</h2>
				<button id="add-sql" type="button" class="button">+ SQL 추가</button>
			</div>
			
			<div id="sql-items"></div>
			
			<div class="form-footer">
				<span class="caption">
					제출 후 SQL 원문은 수정할 수 없습니다.
				</span>

				<div>
					<button id="save-draft" type="button" class="button">
						초안 저장
					</button>

					<button type="submit" class="button primary">
						SQL 검증 및 다음 단계
					</button>
				</div>
			</div>
		</form>
		</form>
		<script>
			const GENERAL = "SELECT department_name, SUM(sales_amount) AS sales_total\nFROM V_SALES_SUMMARY\nWHERE sales_date >= '2026-09-01'\n  AND sales_date < '2026-10-01'\nGROUP BY department_name\nORDER BY sales_total DESC;";

			let mode = $('#form-config').dataset.mode, sqls = mode === 'direct' ? [GENERAL] : [''];

			const draftKey = 'sqlflow-draft-v1';

			function readSqls() {
				sqls = Array.from(document.querySelectorAll('.sql-editor')).map(el => el.value);
			}

			function setMode(next) {
				mode = next;
				$('#ai-section').hidden = mode !== 'ai';
				document.querySelectorAll('[data-mode]').forEach(b => {
					if (b.tagName === 'BUTTON') {
						b.setAttribute('aria-pressed', String(b.dataset.mode === mode));
					}
				});
			}
			
			function renderSqls() {
				const box = $('#sql-items');
				box.replaceChildren();
				sqls.forEach((sql, index) => {
					const section = document.createElement('section');
					section.className = 'sql-item';
					const head = document.createElement('div');
					head.className = 'panel-heading';
					const label = document.createElement('label');
					label.htmlFor = 'sql-' + index;
					label.textContent = 'SQL ' + (index + 1) + ' · SELECT';
					head.append(label);
					
					if (sqls.length > 1) {
						const remove = document.createElement('button');
						remove.type = 'button';
						remove.className = 'text-button';
						remove.textContent = '삭제';
						remove.addEventListener('click', () => {
							readSqls(); sqls.splice(index, 1);
							renderSqls();}); head.append(remove);
					}
					
					const editor = document.createElement('textarea');
					editor.id = 'sql-' + index;
					editor.className = 'sql-editor';
					editor.rows = 8;
					editor.spellcheck = false;
					editor.value = sql;
					editor.required = true;
					editor.maxLength = 20000;
					section.append(head, editor);
					box.append(section);
				});
					
				$('#sql-count').textContent = sqls.length + ' / 5';
				$('#add-sql').disabled = sqls.length >= 5;
			}
			
			document.querySelectorAll('button[data-mode]').forEach(b => {
				b.addEventListener('click', () => setMode(b.dataset.mode))
			});
			
			document.querySelectorAll('[data-example]').forEach(b => {
				b.addEventListener('click', () => {
					$('#prompt').value = b.dataset.example === 'sensitive' ? '2026년 9월 계약 상세 내역을 보여줘' : '2026년 9월 부서별 매출 합계를 보여줘';
					$('#title').value = b.dataset.example === 'sensitive' ? '계약 상세 현황 확인' : '9월 부서별 매출 현황';
				});
			});
			
			$('#generate').addEventListener('click', function () {busy(this, async () => {
				const data = await post('/sql/generate', {
					prompt: $('#prompt').value
				});
				
				readSqls();
				sqls[0] = data.sql;
				renderSqls();
				$('#explanation').textContent = data.explanation; notify('SQL 초안이 생성되었습니다. 조회 조건을 확인하세요.');
				});
			});
			
			$('#add-sql').addEventListener('click', () => {
				readSqls();
				
				if (sqls.length < 5) {
					sqls.push('');
					renderSqls();
				}
				
			});
			
			$('#request-form').addEventListener('submit', e => {
				e.preventDefault();
				readSqls();
				
				const draft = {
					title: $('#title').value, purpose: $('#purpose').value, sqls, mode};
					busy(e.submitter, async () => {
						const check = await post('/sql/validate', draft);
						
						if (!check.valid) {
							throw Error(check.errors.join(' '));
						}
						
						sessionStorage.setItem(draftKey, JSON.stringify(draft));
						location.href = BASE + '/requests/review';
					});
				});
				
			try {
				const saved = JSON.parse(sessionStorage.getItem(draftKey) || 'null');
				
				if (saved && Array.isArray(saved.sqls) && saved.sqls.length > 0 && saved.sqls.length <= 5) {
					$('#title').value = saved.title || '';
					$('#purpose').value = saved.purpose || '';
					sqls = saved.sqls; mode = saved.mode || mode;
				}
				
			} catch (e) {
				sessionStorage.removeItem(draftKey);
			}
			
			renderSqls();
			setMode(mode);

			$('#save-draft').addEventListener('click', function () {
				busy(this, async () => {
					const title = $('#title').value.trim();
					const purpose = $('#purpose').value.trim();

					if (!title) {
						throw Error('요청 제목을 입력하세요.');
					}

					if (!purpose) {
						throw Error('조회 목적을 입력하세요.');
					}

					// 현재 SQL 입력값을 읽음
					readSqls();

					const payload = {
						title,
						purpose,

						// 빈 입력 칸은 초안의 SQL 목록에서 제외
						sqls: sqls.filter(sql => sql.trim().length > 0)
					};

					// 공통 post 함수가 /api를 앞에 붙임
					const result = await post('/requests/drafts', payload);

					notify(
						'초안을 저장했습니다. 요청 ID: ' + result.requestId
					);
				});
			});
		</script>
		<%@ include file="fragments/bottom.jspf" %>