<%@ page pageEncoding="UTF-8" %>
	<%@ include file="fragments/top.jspf" %>

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
			</div>
		</div>

		<section id="request-info" class="panel" hidden>
			<h2>요청 정보</h2>

			<dl class="key-values">
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
					<dd><span id="request-urgency" class="badge"></span></dd>
				</div>

				<div>
					<dt>희망일</dt>
					<dd id="request-desired-date"></dd>
				</div>
			</dl>

			<hr>

			<h2>조회 목적</h2>
			<p id="request-purpose" class="notice-body"></p>
			
			<hr>

			<h2>상세 내용</h2>
			<p id="request-detail-content" class="notice-body"></p>
		</section>

		<section class="panel section-gap">
			<div class="panel-heading">
				<h2>등록한 SQL</h2>
				<span id="sql-count" class="caption"></span>
			</div>

			<div id="detail-items">불러오는 중입니다.</div>
		</section>

		<style>
			#approval-dialog {
				width: min(520px, calc(100% - 32px));
				max-height: 85vh;
				overflow: auto;
				border: 1px solid var(--line);
				border-radius: 9px;
				padding: 24px;
			}

			#approval-dialog::backdrop {
				background: rgba(0, 0, 0, 0.35);
			}

			#approval-dialog label {
				margin-top: 18px;
			}

			#approval-fields {
				border: 0;
				padding: 0;
				margin: 0;
				min-width: 0;
			}

			#approval-dialog-message {
				margin: 16px 0;
			}
		</style>

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

					// 초안 수정 화면으로 이동할 주소
					$('#draft-edit-link').href =
						BASE + '/requests/'
						+ encodeURIComponent(request.requestId)
						+ '/edit';

					// 초안 상태에서만 수정 버튼 표시
					$('#draft-edit-action').hidden =
						request.requestStatus !== 'DRAFT';

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

			loadDetail();
		</script>

		<%@ include file="fragments/bottom.jspf" %>
