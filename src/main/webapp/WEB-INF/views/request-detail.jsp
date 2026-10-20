<%@ page pageEncoding="UTF-8" %>
	<%@ include file="fragments/top.jspf" %>

		<div class="request-detail-page">

			<div id="detail-config" data-request-id="<c:out value='${requestId}'/>" hidden>
			</div>

			<a class="back-link" href="${pageContext.request.contextPath}/requests">
				← 내 요청 목록
			</a>

			<div class="page-heading">
				<div>
					<span id="request-number" class="caption"></span>
					<h1 id="detail-title">조회 요청을 불러오는 중입니다.</h1>
				</div>

				<div>
					<span id="detail-state"></span>

					<span id="draft-edit-action" hidden>
						<a id="draft-edit-link" class="button">
							초안 수정
						</a>

						<button id="draft-delete" type="button" class="button">
							초안 삭제
						</button>

						<button id="request-submit-open" type="button" class="button primary">
							제출
						</button>
					</span>

					<button id="request-redraft" type="button" class="button primary" hidden>
						재기안
					</button>
				</div>
			</div>

			<div class="request-detail-layout">

				<!-- 왼쪽: 요청 정보와 본문 -->
				<section id="request-info" class="panel request-info-panel" hidden>
					<h2>요청 정보</h2>

					<dl class="key-values request-info-grid">
						<div id="source-request-row" class="request-source-row" hidden>
							<dt>원본 요청</dt>
							<dd>
								<a id="source-request-link"></a>
							</dd>
						</div>

						<div>
							<dt>작성자</dt>
							<dd id="drafter-name"></dd>
						</div>

						<div>
							<dt>요청 부서</dt>
							<dd id="dept-name"></dd>
						</div>

						<div>
							<dt>조회 유형</dt>
							<dd id="request-class"></dd>
						</div>

						<div>
							<dt>결재 상태</dt>
							<dd id="approval-status"></dd>
						</div>

						<div>
							<dt>생성 시각</dt>
							<dd id="created-at"></dd>
						</div>

						<div>
							<dt>제출 시각</dt>
							<dd id="submitted-at"></dd>
						</div>

						<div>
							<dt>긴급 여부</dt>
							<dd>
								<span id="request-urgency" class="badge"></span>
							</dd>
						</div>

						<div>
							<dt>희망일</dt>
							<dd id="request-desired-date"></dd>
						</div>
					</dl>

					<div class="request-content-section">
						<h2>조회 목적</h2>
						<p id="request-purpose" class="notice-body"></p>
					</div>

					<div class="request-content-section">
						<h2>상세 내용</h2>
						<p id="request-detail-content" class="notice-body"></p>
					</div>
				</section>

				<!-- 오른쪽: 결재선 배치 미리보기 -->
				<aside class="panel request-approval-panel" aria-labelledby="request-approval-title">

					<div class="panel-heading">
						<h2 id="request-approval-title">결재선</h2>
						<span class="badge">배치 미리보기</span>
					</div>

					<p class="request-approval-description">
						중간 결재자 → 소속 부서장 → DB팀 실행 승인 순서로 진행됩니다.
					</p>

					<ol class="request-approval-list">
						<li>
							<span class="request-approval-number" aria-hidden="true">1</span>
							<div>
								<strong>소속 부서장</strong>
								<p>자동 지정 예정</p>
								<span>등록 기능 연결 후 표시합니다.</span>
							</div>
						</li>

						<li>
							<span class="request-approval-number" aria-hidden="true">2</span>
							<div>
								<strong>DB팀 결재자</strong>
								<p>결재자 선택 예정</p>
								<span>등록 기능 연결 후 표시합니다.</span>
							</div>
						</li>
					</ol>

					<button id="approval-line-open" type="button" class="button primary request-approval-register"
						aria-haspopup="dialog" aria-controls="approval-line-dialog"
						aria-describedby="approval-preview-help" disabled>
						결재선 등록
					</button>

					<p id="approval-preview-help" class="request-approval-help">
						초안 상태에서 결재선 등록 화면을 열 수 있습니다.
					</p>
				</aside>

			</div>

			<!-- 아래: SQL은 전체 너비 사용 -->
			<section class="panel section-gap request-sql-panel">
				<div class="panel-heading">
					<h2>등록한 SQL</h2>
					<span id="sql-count" class="caption"></span>
				</div>

				<div id="detail-items">불러오는 중입니다.</div>
			</section>

		</div><!-- request-detail-page 끝 -->

		<!-- 결재선 등록 모달 -->
		<dialog id="approval-line-dialog" aria-labelledby="approval-line-dialog-title"
			aria-describedby="approval-line-dialog-description">

			<header class="approval-line-dialog-heading">
				<h2 id="approval-line-dialog-title">결재선 등록</h2>

				<p id="approval-line-dialog-description">
					중간 결재자 → 소속 부서장 → DB팀 실행 승인 순서로 진행됩니다.
				</p>
			</header>

			<div class="approval-line-dialog-body">
				<fieldset id="approval-line-search-fields" class="approval-line-search-fields" disabled>
					<legend class="sr-only">결재자 조회</legend>

					<div class="approval-line-search">
						<label for="approval-line-keyword">사용자명</label>

						<input id="approval-line-keyword" type="search" maxlength="100" placeholder="사용자명 입력">

						<button id="approval-line-search" type="button" class="button">
							조회
						</button>

						<div class="approval-line-add-toolbar">
							<button id="approval-line-add" type="button" class="button" disabled>
								선택한 결재자 추가
							</button>

							<button id="approval-line-reset" type="button" class="button" disabled>
								초기화
							</button>
						</div>
					</div>
				</fieldset>

				<div class="approval-line-candidate-area">
					<div class="approval-line-table-scroll">
						<table class="approval-line-candidate-table">
							<caption class="sr-only">결재자 후보 목록</caption>

							<thead>
								<tr>
									<th scope="col">선택</th>
									<th scope="col">사용자명</th>
									<th scope="col">사용자 번호</th>
									<th scope="col">부서</th>
									<th scope="col">직급</th>
								</tr>
							</thead>

							<tbody id="approval-line-candidates">
								<tr>
									<td colspan="5" class="empty" role="status">
										사용자명을 입력하고 조회하세요.
									</td>
								</tr>
							</tbody>
						</table>
					</div>
				</div>

				<section class="approval-line-current-area" aria-label="현재 결재선">

					<div class="approval-line-name-row">
						<fieldset class="approval-line-personal-fields" disabled>
							<legend class="sr-only">개인 결재선 불러오기</legend>

							<div class="approval-line-personal-toolbar">
								<label for="approval-line-saved">내 결재선</label>

								<select id="approval-line-saved">
									<option value="">선택</option>
								</select>

								<button id="approval-line-load" type="button" class="button">
									불러오기
								</button>
							</div>
						</fieldset>

						<div class="approval-line-name-editor">
							<label for="approval-line-name">결재선명</label>

							<input id="approval-line-name" type="text" placeholder="개인 결재선 저장 시 입력" disabled>

							<div class="approval-line-management-buttons">
								<button id="approval-line-new" type="button" class="button" disabled>
									신규
								</button>

								<button id="approval-line-save" type="button" class="button" disabled>
									저장
								</button>
							</div>
						</div>
					</div>

					<div class="approval-line-current-scroll">
						<table class="approval-line-current-table">
							<caption class="sr-only">현재 결재선</caption>

							<thead>
								<tr>
									<th scope="col">순번</th>
									<th scope="col">역할</th>
									<th scope="col">결재자명</th>
									<th scope="col">부서</th>
									<th scope="col">직급</th>
									<th scope="col">관리</th>
								</tr>
							</thead>

							<tbody id="approval-line-selected">
								<tr data-line-role="DEPARTMENT_HEAD">
									<td>1</td>
									<td>소속 부서장</td>
									<td id="approval-line-head-preview">
										자동 지정 예정
									</td>
									<td id="approval-line-head-department">—</td>
									<td id="approval-line-head-position">—</td>
									<td>
										<span class="approval-line-fixed">필수</span>
									</td>
								</tr>

								<tr data-line-role="DB_FINAL">
									<td>2</td>
									<td>DB팀 실행 승인</td>
									<td id="approval-line-db-preview">
										선택해 주세요
									</td>
									<td id="approval-line-db-department">DB팀</td>
									<td id="approval-line-db-position">—</td>
									<td>
										<span class="approval-line-fixed">
											필수·최종
										</span>
									</td>
								</tr>
							</tbody>
						</table>
					</div>

					<p class="approval-line-section-help">
						중간 결재자는 부서장 앞에 추가됩니다.
						부서장·DB팀 실행 승인은 필수입니다.
					</p>

					<p id="approval-line-message" class="approval-line-section-help" role="status"></p>
				</section>
			</div>

			<footer class="approval-line-dialog-footer">
				<button id="approval-line-apply" type="button" class="button primary" disabled>
					이 요청에 적용
				</button>

				<button id="approval-line-cancel" type="button" class="button" autofocus>
					취소
				</button>
			</footer>
		</dialog>

		<dialog id="approval-dialog" aria-labelledby="approval-dialog-title">
			<h2 id="approval-dialog-title">결재선 확인 및 제출</h2>

			<p id="approval-dialog-message" role="status"></p>

			<form id="approval-form">
				<fieldset id="approval-fields" disabled>
					<label for="department-approver">
						1. 소속 부서장
					</label>

					<input id="department-approver" readonly>

					<label for="db-approver">
						2. DB팀 결재자
						<span class="required">*</span>
					</label>

					<select id="db-approver" required>
						<option value="">DB팀 결재자를 선택하세요.</option>
					</select>

					<p class="caption section-gap">
						제출 후에는 요청 내용과 SQL을 수정할 수 없습니다.
					</p>

					<div class="form-footer">
						<button id="request-submit-confirm" type="submit" class="button primary">
							제출 확정
						</button>
					</div>
				</fieldset>
			</form>

			<button id="approval-cancel" type="button" class="button section-gap">
				취소
			</button>
		</dialog>

		<script>
			const requestId = $('#detail-config').dataset.requestId;

			const detailStatusNames = {
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

			const detailClassNames = {
				GENERAL: '일반 조회',
				SENSITIVE: '민감 조회'
			};

			const detailApprovalNames = {
				NOT_REQUIRED: '결재 불필요',
				PENDING: '결재 대기',
				APPROVED: '승인',
				REJECTED: '반려'
			};

			const validationNames = {
				PENDING: '검증 전',
				VALID: '검증 통과',
				INVALID: '검증 실패'
			};

			const authoringNames = {
				MANUAL: '직접 작성',
				AI: 'AI 작성'
			};

			function formatDetailDate(value) {
				return value
					? value.replace('T', ' ').substring(0, 19)
					: '-';
			}

			function renderDetailItems(items) {
				if (items.length === 0) {
					return '<p class="empty">등록된 SQL이 없습니다.</p>';
				}

				return items.map(function (item) {
					const validationLabel =
						validationNames[item.validationStatus]
						|| item.validationStatus
						|| '-';

					const authoringLabel =
						authoringNames[item.authoringType]
						|| item.authoringType
						|| '-';

					return '<article class="result-item">'
						+ '<div class="panel-heading">'
						+ '<h3>'
						+ esc(item.queryTitle || ('SQL ' + item.queryOrder))
						+ '</h3>'
						+ badge(validationLabel)
						+ '</div>'
						+ '<p class="caption">'
						+ '순서 ' + esc(item.queryOrder)
						+ ' · ' + esc(authoringLabel)
						+ '</p>'
						+ '<pre><code>'
						+ esc(item.sqlText)
						+ '</code></pre>'
						+ '</article>';
				}).join('');
			}

			async function loadDetail() {
				try {
					const request = await api(
						'/requests/mine/' + encodeURIComponent(requestId)
					);

					$('#request-number').textContent = request.requestNo;
					$('#detail-title').textContent = request.title;

					const hasSource =
						request.sourceRequestId != null
						&& Boolean(request.sourceRequestNo);

					$('#source-request-row').hidden = !hasSource;

					const sourceLink = $('#source-request-link');

					if (hasSource) {
						sourceLink.textContent = request.sourceRequestNo;
						sourceLink.href =
							BASE + '/requests/'
							+ encodeURIComponent(request.sourceRequestId);
					} else {
						sourceLink.textContent = '';
						sourceLink.removeAttribute('href');
					}

					// 초안 수정 화면으로 이동할 주소
					$('#draft-edit-link').href =
						BASE + '/requests/'
						+ encodeURIComponent(request.requestId)
						+ '/edit';

					// 초안 상태에서만 수정 버튼 표시
					$('#draft-edit-action').hidden =
						request.requestStatus !== 'DRAFT';

					$('#approval-line-open').disabled =
						request.requestStatus !== 'DRAFT';

					// 반려된 본인 요청에서만 재기안 버튼 표시
					$('#request-redraft').hidden =
						request.requestStatus !== 'REJECTED';

					$('#detail-state').innerHTML = badge(
						detailStatusNames[request.requestStatus]
						|| request.requestStatus
					);

					$('#drafter-name').textContent = request.drafterName;
					$('#dept-name').textContent = request.deptName;
					$('#request-purpose').textContent = request.purpose;

					const isUrgent = request.isUrgent === 'Y';
					$('#request-urgency').textContent = isUrgent ? '긴급' : '일반';
					$('#request-urgency').classList.toggle('urgent-badge', isUrgent);

					$('#request-desired-date').textContent =
						request.desiredDate || '미지정';

					$('#request-detail-content').textContent =
						request.detailContent || '입력된 상세 내용이 없습니다.';

					$('#request-class').textContent =
						detailClassNames[request.requestClass]
						|| request.requestClass
						|| '미분류';

					// 초안의 NOT_REQUIRED 기본값을 확정된 결재 정책처럼 표시하지 않음
					$('#approval-status').textContent =
						request.requestStatus === 'DRAFT'
							? '제출 전'
							: (
								detailApprovalNames[request.approvalStatus]
								|| request.approvalStatus
								|| '-'
							);

					$('#created-at').textContent =
						formatDetailDate(request.createdAt);

					$('#submitted-at').textContent = request.submittedAt
						? formatDetailDate(request.submittedAt)
						: '아직 제출하지 않았습니다.';

					$('#sql-count').textContent =
						'총 ' + request.items.length + '개';

					$('#detail-items').innerHTML =
						renderDetailItems(request.items);

					$('#request-info').hidden = false;

				} catch (error) {
					$('#detail-title').textContent = '조회 요청 상세';
					$('#detail-items').textContent =
						'요청을 불러오지 못했습니다. 내 요청 목록으로 돌아가 확인하세요.';

					$('#draft-edit-action').hidden = true;
					$('#request-redraft').hidden = true;
					$('#source-request-row').hidden = true;

					notify(error.message, true);
				}
			}

			$('#draft-delete').addEventListener('click', function () {
				const confirmed = window.confirm(
					'이 초안을 삭제하시겠습니까?\n'
					+ '등록한 SQL도 함께 삭제되며 복구할 수 없습니다.'
				);

				if (!confirmed) {
					return;
				}

				busy(this, async function () {
					await api(
						'/requests/drafts/' + encodeURIComponent(requestId),
						{
							method: 'DELETE'
						}
					);

					location.href = BASE + '/requests';
				});
			});

			const approvalDialog = $('#approval-dialog');

			let approvalLoadVersion = 0;
			let approvalReady = false;
			let requestSubmitting = false;

			function showApprovalMessage(message, error = false) {
				const element = $('#approval-dialog-message');
				element.textContent = message;
				element.classList.toggle('error-text', error);
				element.setAttribute('role', error ? 'alert' : 'status');
			}

			$('#request-submit-open').addEventListener('click', async function () {
				if (approvalDialog.open) {
					return;
				}

				const loadVersion = ++approvalLoadVersion;

				approvalReady = false;
				$('#approval-fields').disabled = true;
				$('#department-approver').value = '';
				$('#db-approver').replaceChildren(
					new Option('DB팀 결재자를 선택하세요.', '')
				);

				showApprovalMessage('결재자 정보를 불러오는 중입니다.');
				approvalDialog.showModal();

				try {
					// 준오와 공유한 후보 조회 API 기준안
					const candidates = await api(
						'/requests/'
						+ encodeURIComponent(requestId)
						+ '/approval-candidates'
					);

					// 모달을 닫았거나 새 조회가 시작됐다면 이전 응답은 무시
					if (!approvalDialog.open
						|| loadVersion !== approvalLoadVersion) {
						return;
					}

					const departmentApprover = candidates?.departmentApprover;
					const dbApprovers = candidates?.dbApprovers;

					if (!departmentApprover?.userId) {
						throw Error('소속 부서장이 지정되어 있지 않습니다.');
					}

					if (!Array.isArray(dbApprovers) || dbApprovers.length === 0) {
						throw Error('선택 가능한 DB팀 결재자가 없습니다.');
					}

					$('#department-approver').value =
						departmentApprover.userName
						+ ' · '
						+ departmentApprover.deptName;

					dbApprovers.forEach(function (user) {
						const option = new Option(
							user.userName + ' · ' + user.deptName,
							String(user.userId)
						);

						$('#db-approver').add(option);
					});

					// 후보가 한 명이면 자동 선택
					if (dbApprovers.length === 1) {
						$('#db-approver').value = String(dbApprovers[0].userId);
					}

					approvalReady = true;
					$('#approval-fields').disabled = false;

					showApprovalMessage(
						'부서장 → DB팀 순서로 결재가 진행됩니다.'
					);
				} catch (error) {
					if (approvalDialog.open
						&& loadVersion === approvalLoadVersion) {
						showApprovalMessage(
							'결재자 정보를 불러오지 못했습니다. '
							+ error.message,
							true
						);
					}
				}
			});

			$('#approval-cancel').addEventListener('click', function () {
				if (!requestSubmitting) {
					approvalDialog.close();
				}
			});

			// 제출 처리 중에는 Esc로 모달을 닫지 않음
			approvalDialog.addEventListener('cancel', function (event) {
				if (requestSubmitting) {
					event.preventDefault();
				}
			});

			approvalDialog.addEventListener('close', function () {
				approvalLoadVersion++;
				approvalReady = false;
			});

			$('#approval-form').addEventListener('submit', function (event) {
				event.preventDefault();

				if (!approvalReady || requestSubmitting) {
					return;
				}

				const dbApproverId = Number($('#db-approver').value);

				if (!Number.isSafeInteger(dbApproverId) || dbApproverId <= 0) {
					showApprovalMessage('DB팀 결재자를 선택하세요.', true);
					return;
				}

				if (!window.confirm('선택한 결재선으로 요청을 제출하시겠습니까?')) {
					return;
				}

				busy($('#request-submit-confirm'), async function () {
					requestSubmitting = true;
					$('#approval-fields').disabled = true;
					$('#approval-cancel').disabled = true;

					showApprovalMessage('요청을 제출하는 중입니다.');

					try {
						await post(
							'/requests/'
							+ encodeURIComponent(requestId)
							+ '/submit',
							{dbApproverId}
						);

						// 상세 정보를 새로 조회하여 제출 상태 표시
						location.reload();
					} catch (error) {
						showApprovalMessage(error.message, true);
					} finally {
						requestSubmitting = false;
						$('#approval-fields').disabled = false;
						$('#approval-cancel').disabled = false;
					}
				});
			});

			let redraftCreating = false;

			$('#request-redraft').addEventListener('click', function () {
				if (redraftCreating) {
					return;
				}

				const confirmed = window.confirm(
					'이 요청을 복사하여 새 초안을 작성하시겠습니까?\n'
					+ '기존 요청과 결재 이력은 유지됩니다.'
				);

				if (!confirmed) {
					return;
				}

				redraftCreating = true;

				busy(this, async function () {
					try {
						const result = await api(
							'/requests/'
							+ encodeURIComponent(requestId)
							+ '/redraft',
							{method: 'POST'}
						);

						if (!result?.requestId) {
							throw Error('생성된 초안 정보를 확인할 수 없습니다.');
						}

						location.href =
							BASE + '/requests/'
							+ encodeURIComponent(result.requestId)
							+ '/edit';

					} catch (error) {
						redraftCreating = false;
						throw error;
					}
				});
			});

			// 결재선 등록 모달
			const approvalLineDialog = $('#approval-line-dialog');
			const approvalLineOpenButton = $('#approval-line-open');
			const approvalLineSearchFields = $('#approval-line-search-fields');
			const approvalLineCandidateBody = $('#approval-line-candidates');
			const approvalLineSelectedBody = $('#approval-line-selected');

			let approvalLineCandidateVersion = 0;
			let approvalLineLoading = false;

			let approvalLineVisibleCandidates = [];
			let approvalLineMiddleApprovers = [];
			let approvalLineDepartmentApprover = null;

			const approvalLineCheckedCandidateIds = new Set();


			// --------------------------------------------------
			// API 연결부: 준오가 정한 경로·응답에 맞출 부분
			// --------------------------------------------------

			async function fetchApprovalLineUsers(keyword) {
				const users = await api(
					'/requests/'
					+ encodeURIComponent(requestId)
					+ '/approval-users?keyword='
					+ encodeURIComponent(keyword)
				);

				if (!Array.isArray(users)) {
					throw Error('사용자 목록 응답을 확인할 수 없습니다.');
				}

				return users;
			}


			// --------------------------------------------------
			// 공통 표시·상태
			// --------------------------------------------------

			function approvalLineUserId(user) {
				return String(user.userId);
			}

			function showApprovalLineMessage(message) {
				$('#approval-line-message').textContent = message;
			}

			function showApprovalLineCandidateMessage(message) {
				approvalLineCandidateBody.innerHTML =
					'<tr>'
					+ '<td colspan="5" class="empty" role="status">'
					+ esc(message)
					+ '</td>'
					+ '</tr>';
			}

			function isApprovalLineUserAssigned(user) {
				const userId = approvalLineUserId(user);

				const isDepartmentApprover =
					approvalLineDepartmentApprover
					&& approvalLineUserId(approvalLineDepartmentApprover) === userId;

				const isMiddleApprover =
					approvalLineMiddleApprovers.some(function (item) {
						return approvalLineUserId(item) === userId;
					});

				return Boolean(isDepartmentApprover || isMiddleApprover);
			}

			function updateApprovalLineControls() {
				approvalLineSearchFields.disabled = approvalLineLoading;

				$('#approval-line-add').disabled =
					approvalLineLoading
					|| !approvalLineDepartmentApprover
					|| approvalLineCheckedCandidateIds.size === 0;

				$('#approval-line-reset').disabled =
					approvalLineLoading
					|| approvalLineMiddleApprovers.length === 0;

				// 아직 저장·요청 적용은 연결하지 않음
				$('#approval-line-apply').disabled = true;

				approvalLineSelectedBody
					.querySelectorAll('[data-middle-action]')
					.forEach(function (button) {
						const index = Number(button.dataset.index);
						const action = button.dataset.middleAction;

						button.disabled =
							approvalLineLoading
							|| (action === 'up' && index === 0)
							|| (
								action === 'down'
								&& index === approvalLineMiddleApprovers.length - 1
							);
					});
			}


			// --------------------------------------------------
			// 후보 목록
			// --------------------------------------------------

			function renderApprovalLineCandidates(users) {
				approvalLineVisibleCandidates = users;

				if (users.length === 0) {
					showApprovalLineCandidateMessage(
						'조회된 사용자가 없습니다.'
					);
					return;
				}

				approvalLineCandidateBody.innerHTML = users.map(function (user) {
					const userId = approvalLineUserId(user);
					const assigned = isApprovalLineUserAssigned(user);

					const checked =
					assigned
					|| approvalLineCheckedCandidateIds.has(userId);

					return '<tr class="' + (checked ? 'is-selected' : '') + '">'
						+ '<td>'
						+ '<input type="checkbox"'
						+ ' class="approval-line-candidate-check"'
						+ ' name="approval-line-candidate"'
						+ ' value="' + esc(userId) + '"'
						+ ' aria-label="' + esc(user.userName) + ' 선택"'
						+ (checked ? ' checked' : '')
						+ (assigned ? ' disabled' : '')
						+ '>'
						+ '</td>'
						+ '<td>' + esc(user.userName) + '</td>'
						+ '<td>' + esc(user.userId) + '</td>'
						+ '<td>' + esc(user.deptName) + '</td>'
						+ '<td>' + esc(user.positionName || '—') + '</td>'
						+ '</tr>';
				}).join('');
			}


			// --------------------------------------------------
			// 구성한 결재선
			// 기본 부서장·DB팀 행은 유지하고 중간 행만 생성
			// --------------------------------------------------

			function renderApprovalLineMiddleApprovers() {
				approvalLineSelectedBody
					.querySelectorAll('tr[data-line-role="MIDDLE"]')
					.forEach(function (row) {
						row.remove();
					});

				const departmentRow = approvalLineSelectedBody.querySelector(
					'tr[data-line-role="DEPARTMENT_HEAD"]'
				);

				const rows = approvalLineMiddleApprovers
					.map(function (user, index) {
						return '<tr data-line-role="MIDDLE">'
							+ '<td>' + (index + 1) + '</td>'
							+ '<td>중간 결재자</td>'
							+ '<td>' + esc(user.userName) + '</td>'
							+ '<td>' + esc(user.deptName) + '</td>'
							+ '<td>' + esc(user.positionName || '—') + '</td>'
							+ '<td>'
							+ '<div class="approval-line-row-actions">'

							+ '<button type="button" class="button"'
							+ ' data-middle-action="up"'
							+ ' data-index="' + index + '"'
							+ ' aria-label="' + esc(user.userName) + ' 위로 이동"'
							+ (index === 0 ? ' disabled' : '')
							+ '>↑</button>'

							+ '<button type="button" class="button"'
							+ ' data-middle-action="down"'
							+ ' data-index="' + index + '"'
							+ ' aria-label="' + esc(user.userName) + ' 아래로 이동"'
							+ (
								index === approvalLineMiddleApprovers.length - 1
									? ' disabled'
									: ''
							)
							+ '>↓</button>'

							+ '<button type="button" class="button"'
							+ ' data-middle-action="delete"'
							+ ' data-index="' + index + '"'
							+ ' aria-label="' + esc(user.userName) + ' 결재선에서 삭제"'
							+ '>삭제</button>'

							+ '</div>'
							+ '</td>'
							+ '</tr>';
					}).join('');

				departmentRow.insertAdjacentHTML('beforebegin', rows);

				// 중간 결재자 → 부서장 → DB팀 순서로 번호 재계산
				approvalLineSelectedBody.querySelectorAll('tr')
					.forEach(function (row, index) {
						row.cells[0].textContent = String(index + 1);
					});

				updateApprovalLineControls();
			}


			// --------------------------------------------------
			// 후보 조회
			// --------------------------------------------------

			async function loadApprovalLineCandidates(showResults) {
				const version = ++approvalLineCandidateVersion;
				const keyword = $('#approval-line-keyword').value.trim();

				approvalLineLoading = true;
				approvalLineCheckedCandidateIds.clear();
				approvalLineVisibleCandidates = [];

				updateApprovalLineControls();
				showApprovalLineMessage('');

				approvalLineCandidateBody.setAttribute('aria-busy', 'true');

				showApprovalLineCandidateMessage(
					'결재자 정보를 불러오는 중입니다.'
				);

				try {
					const requests = [
						api(
							'/requests/'
							+ encodeURIComponent(requestId)
							+ '/approval-candidates'
						)
					];

					if (showResults) {
						requests.push(fetchApprovalLineUsers(keyword));
					}

					const results = await Promise.all(requests);
					const defaults = results[0];
					const users = showResults ? results[1] : [];

					if (!approvalLineDialog.open
						|| version !== approvalLineCandidateVersion) {
						return;
					}

					const head = defaults?.departmentApprover;

					if (!head?.userId) {
						throw Error('소속 부서장이 지정되어 있지 않습니다.');
					}

					approvalLineDepartmentApprover = head;

					$('#approval-line-head-preview').textContent =
						head.userName;

					$('#approval-line-head-department').textContent =
						head.deptName;

					$('#approval-line-head-position').textContent =
						head.positionName || '—';

					// 부서장이 변경됐다면 중간 결재자와 중복되지 않도록 정리
					approvalLineMiddleApprovers =
						approvalLineMiddleApprovers.filter(function (user) {
							return approvalLineUserId(user)
								!== approvalLineUserId(head);
						});

					renderApprovalLineMiddleApprovers();

					if (showResults) {
						renderApprovalLineCandidates(users);
					} else {
						showApprovalLineCandidateMessage(
							'사용자명을 입력하고 조회하세요.'
						);
					}

				} catch (error) {
					if (!approvalLineDialog.open
						|| version !== approvalLineCandidateVersion) {
						return;
					}

					showApprovalLineCandidateMessage(
						error.message || '사용자를 조회하지 못했습니다.'
					);

					if (!approvalLineDepartmentApprover) {
						$('#approval-line-head-preview').textContent =
							'확인하지 못했습니다.';

						$('#approval-line-head-department').textContent = '—';
						$('#approval-line-head-position').textContent = '—';
					}

				} finally {
					if (approvalLineDialog.open
						&& version === approvalLineCandidateVersion) {
						approvalLineLoading = false;

						approvalLineCandidateBody.setAttribute(
							'aria-busy',
							'false'
						);

						updateApprovalLineControls();
					}
				}
			}


			// --------------------------------------------------
			// 모달 열기·검색
			// --------------------------------------------------

			approvalLineOpenButton.addEventListener('click', function () {
				if (this.disabled || approvalLineDialog.open) {
					return;
				}

				approvalLineDepartmentApprover = null;
				approvalLineMiddleApprovers = [];
				approvalLineVisibleCandidates = [];
				approvalLineCheckedCandidateIds.clear();

				$('#approval-line-keyword').value = '';
				$('#approval-line-name').value = '';

				$('#approval-line-head-preview').textContent =
					'확인 중입니다.';

				$('#approval-line-head-department').textContent = '—';
				$('#approval-line-head-position').textContent = '—';

				$('#approval-line-db-preview').textContent = '선택해 주세요';
				$('#approval-line-db-department').textContent = 'DB팀';
				$('#approval-line-db-position').textContent = '—';

				showApprovalLineMessage('');
				renderApprovalLineMiddleApprovers();

				approvalLineDialog.showModal();
				loadApprovalLineCandidates(false);
			});

			$('#approval-line-search').addEventListener('click', function () {
				if (!approvalLineLoading) {
					loadApprovalLineCandidates(true);
				}
			});

			$('#approval-line-keyword').addEventListener('keydown', function (event) {
				if (event.key !== 'Enter' || event.isComposing) {
					return;
				}

				event.preventDefault();

				if (!approvalLineLoading) {
					loadApprovalLineCandidates(true);
				}
			});


			// --------------------------------------------------
			// 체크 선택
			// --------------------------------------------------

			approvalLineCandidateBody.addEventListener('change', function (event) {
				const checkbox = event.target;

				if (!checkbox.matches('.approval-line-candidate-check')
					|| checkbox.disabled) {
					return;
				}

				const user = approvalLineVisibleCandidates.find(function (item) {
					return approvalLineUserId(item) === checkbox.value;
				});

				if (!user || isApprovalLineUserAssigned(user)) {
					return;
				}

				if (checkbox.checked) {
					approvalLineCheckedCandidateIds.add(checkbox.value);
				} else {
					approvalLineCheckedCandidateIds.delete(checkbox.value);
				}

				checkbox.closest('tr').classList.toggle(
					'is-selected',
					checkbox.checked
				);

				updateApprovalLineControls();
			});


			// --------------------------------------------------
			// 중간 결재자 추가
			// --------------------------------------------------

			$('#approval-line-add').addEventListener('click', function () {
				if (approvalLineLoading || !approvalLineDepartmentApprover) {
					return;
				}

				const addedIds = new Set(
					approvalLineMiddleApprovers.map(approvalLineUserId)
				);

				let addedCount = 0;

				// 현재 검색 결과에 표시된 순서대로 추가
				approvalLineVisibleCandidates.forEach(function (user) {
					const userId = approvalLineUserId(user);

					if (!approvalLineCheckedCandidateIds.has(userId)
						|| isApprovalLineUserAssigned(user)
						|| addedIds.has(userId)) {
						return;
					}

					approvalLineMiddleApprovers.push(user);
					addedIds.add(userId);
					addedCount++;
				});

				approvalLineCheckedCandidateIds.clear();

				renderApprovalLineMiddleApprovers();
				renderApprovalLineCandidates(approvalLineVisibleCandidates);

				showApprovalLineMessage(
					addedCount + '명을 중간 결재자로 추가했습니다.'
				);
			});


			// --------------------------------------------------
			// 중간 결재자 순서 변경·삭제
			// --------------------------------------------------

			approvalLineSelectedBody.addEventListener('click', function (event) {
				const button = event.target.closest('[data-middle-action]');

				if (!button || button.disabled || approvalLineLoading) {
					return;
				}

				const index = Number(button.dataset.index);
				const action = button.dataset.middleAction;

				if (!Number.isInteger(index)
					|| index < 0
					|| index >= approvalLineMiddleApprovers.length) {
					return;
				}

				if (action === 'delete') {
					approvalLineMiddleApprovers.splice(index, 1);

				} else if (action === 'up' || action === 'down') {
					const nextIndex = action === 'up' ? index - 1 : index + 1;

					if (nextIndex < 0
						|| nextIndex >= approvalLineMiddleApprovers.length) {
						return;
					}

					[
						approvalLineMiddleApprovers[index],
						approvalLineMiddleApprovers[nextIndex]
					] = [
							approvalLineMiddleApprovers[nextIndex],
							approvalLineMiddleApprovers[index]
						];

				} else {
					return;
				}

				renderApprovalLineMiddleApprovers();

				if (approvalLineVisibleCandidates.length > 0) {
					renderApprovalLineCandidates(approvalLineVisibleCandidates);
				}

				showApprovalLineMessage('');
			});


			// --------------------------------------------------
			// 초기화
			// --------------------------------------------------

			$('#approval-line-reset').addEventListener('click', function () {
				if (approvalLineLoading
					|| approvalLineMiddleApprovers.length === 0) {
					return;
				}

				if (!window.confirm('추가한 중간 결재자를 모두 제거하시겠습니까?')) {
					return;
				}

				approvalLineMiddleApprovers = [];
				approvalLineCheckedCandidateIds.clear();

				renderApprovalLineMiddleApprovers();

				if (approvalLineVisibleCandidates.length > 0) {
					renderApprovalLineCandidates(approvalLineVisibleCandidates);
				}

				showApprovalLineMessage(
					'중간 결재자를 제거하고 기본 구성으로 초기화했습니다.'
				);
			});


			// --------------------------------------------------
			// 닫기
			// --------------------------------------------------

			$('#approval-line-cancel').addEventListener('click', function () {
				approvalLineDialog.close();
			});

			approvalLineDialog.addEventListener('close', function () {
				approvalLineCandidateVersion++;
				approvalLineLoading = false;

				approvalLineCheckedCandidateIds.clear();
				approvalLineVisibleCandidates = [];

				approvalLineCandidateBody.setAttribute('aria-busy', 'false');
			});

			loadDetail();
		</script>

		<%@ include file="fragments/bottom.jspf" %>