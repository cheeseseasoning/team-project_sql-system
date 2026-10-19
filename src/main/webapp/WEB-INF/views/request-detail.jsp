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

				    <button id="draft-delete"
				            type="button"
				            class="button">
				        초안 삭제
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
			</dl>

			<hr>

			<h2>조회 목적</h2>
			<p id="request-purpose" class="notice-body"></p>
		</section>

		<section class="panel section-gap">
			<div class="panel-heading">
				<h2>등록한 SQL</h2>
				<span id="sql-count" class="caption"></span>
			</div>

			<div id="detail-items">불러오는 중입니다.</div>
		</section>

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

			loadDetail();
		</script>

		<%@ include file="fragments/bottom.jspf" %>