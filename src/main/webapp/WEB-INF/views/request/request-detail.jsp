<%@ page pageEncoding="UTF-8" %>
	<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

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

					<button id="request-withdraw"
					        type="button"
					        class="button"
					        hidden>
					    기안서 회수
					</button>
					
					<button id="request-redraft" type="button" class="button primary" hidden>
						재기안
					</button>
				</div>
			</div>

			<c:set var="requestDetailReadOnly" value="${false}" />
			<%@ include file="/WEB-INF/views/fragments/request-detail-body.jspf" %>

		</div><!-- request-detail-page 끝 -->

		<!-- 결재선 등록 모달 -->
		<dialog id="approval-line-dialog" aria-labelledby="approval-line-dialog-title"
			aria-describedby="approval-line-dialog-description">

			<header class="approval-line-dialog-heading">
				<h2 id="approval-line-dialog-title">결재선 등록</h2>

				<p id="approval-line-dialog-description">
				    결재자를 선택하고 순서를 지정하세요.
				    마지막 결재자가 승인하면 최종 승인됩니다.
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
							    <tr>
							        <td colspan="6" class="empty">
							            결재자를 1명 이상 추가하세요.
							        </td>
							    </tr>
							</tbody>
						</table>
					</div>

					<p class="approval-line-section-help">
						지정한 순서대로 결재가 진행됩니다.
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

		<script src="${pageContext.request.contextPath}/js/request-detail-renderer.js?v=20261106-flexible-approval-dml"></script>
		<script src="${pageContext.request.contextPath}/js/request-detail-author.js?v=20261106-flexible-approval-dml"></script>

		<%@ include file="/WEB-INF/views/fragments/bottom.jspf" %>
