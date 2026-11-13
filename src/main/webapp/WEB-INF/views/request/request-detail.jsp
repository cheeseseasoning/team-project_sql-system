<%@ page pageEncoding="UTF-8" %>
	<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

		<div class="request-detail-page request-document-page">

		    <div id="detail-config"
		         data-request-id="<c:out value='${requestId}'/>"
		         hidden></div>

		    <a class="back-link"
		       href="${pageContext.request.contextPath}/requests">
		        ← 내 요청함
		    </a>

			<article class="request-document"
				     aria-labelledby="detail-title">

				<header class="request-document-heading">
				    <p id="request-document-heading"
				       class="request-document-caption">
				        SQLFlow · 데이터 조회 기안서
				    </p>

				    <h1 id="detail-title">
				        요청을 불러오는 중입니다.
				    </h1>
				</header>

		        <c:set var="requestDetailReadOnly" value="${false}" />
		        <%@ include file="/WEB-INF/views/fragments/request-detail-body.jspf" %>

				<footer class="request-document-footer">
				    <div class="request-document-actions">
				        <div id="draft-edit-action"
				             class="request-document-draft-actions"
				             hidden>

				            <button id="draft-delete"
				                    type="button"
				                    class="button request-draft-delete">
				                초안 삭제
				            </button>

				            <div class="request-document-primary-actions">
				                <a id="draft-edit-link" class="button">
				                    초안 수정
				                </a>

				                <span id="sql-validation-controls" hidden>
				                    <button id="validate-sqls"
				                            type="button"
				                            class="button"
				                            title="등록된 SQL 전체를 검증합니다.">
				                        SQL 검증
				                    </button>
				                </span>

				                <button id="approval-line-open"
				                        type="button"
				                        class="button"
				                        aria-haspopup="dialog"
				                        aria-controls="approval-line-dialog"
				                        aria-describedby="approval-preview-help"
				                        disabled>
				                    결재선 지정
				                </button>

				                <button id="request-submit-open"
				                        type="button"
				                        class="button primary">
				                    기안서 제출
				                </button>
				            </div>

							<p class="request-document-footer-help">
							    초안은 SQL 검증과 결재선 확인 후 제출할 수 있습니다.
							</p>
				        </div>

				        <button id="request-withdraw"
				                type="button"
				                class="button"
				                hidden>
				            기안서 회수
				        </button>

				        <button id="request-redraft"
				                type="button"
				                class="button primary"
				                hidden>
				            재기안
				        </button>
				    </div>
				</footer>

		    </article>
		</div>

		<!-- 결재선 등록 모달 -->
		<dialog id="approval-line-dialog" aria-labelledby="approval-line-dialog-title"
			aria-describedby="approval-line-dialog-description">

			<header class="approval-line-dialog-heading">
				<h2 id="approval-line-dialog-title">결재선 설정</h2>

				<p id="approval-line-dialog-description">
				    결재자를 선택하고 순서를 지정하세요.
				    마지막 결재자가 승인하면 최종 승인됩니다.
				</p>
			</header>

			<div class="approval-line-dialog-body">
			    <fieldset id="approval-line-search-fields"
			              class="approval-line-search-fields"
			              disabled>
			        <legend class="sr-only">직원 검색</legend>

			        <div class="approval-line-search">
			            <label for="approval-line-keyword" class="sr-only">
			                이름 또는 부서명
			            </label>

			            <input id="approval-line-keyword"
			                   type="search"
			                   maxlength="100"
			                   placeholder="이름 또는 부서명 검색">

			            <button id="approval-line-search"
			                    type="button"
			                    class="button">
			                검색
			            </button>
			        </div>
			    </fieldset>

			    <div class="approval-line-candidate-area">
			        <h3 class="approval-line-list-heading">직원 목록</h3>

			        <div class="approval-line-table-scroll">
			            <div id="approval-line-candidates"
			                 class="approval-line-departments">
			                <p class="approval-line-empty" role="status">
			                    직원을 불러오는 중입니다.
			                </p>
			            </div>
			        </div>
			    </div>

			    <div class="approval-line-transfer-actions"
			         aria-label="결재자 추가 및 제외">
			        <button id="approval-line-add"
			                type="button"
			                class="button primary"
			                disabled>
			            추가 →
			        </button>

			        <button id="approval-line-remove"
			                type="button"
			                class="button"
			                disabled>
			            ← 제외
			        </button>
			    </div>

			    <section class="approval-line-current-area"
			             aria-label="선택한 결재자">
			        <h3 class="approval-line-list-heading">
			            <span>선택한 결재자</span>
			            <span id="approval-line-selected-count"
			                  class="approval-line-selected-count"
			                  aria-live="polite">0명</span>
			        </h3>

			        <div class="approval-line-current-scroll">
			            <ol id="approval-line-selected"
			                class="approval-line-selected-list">
			                <li class="approval-line-empty">
			                    결재자를 추가해 주세요.
			                </li>
			            </ol>
			        </div>
			    </section>
			</div>

			<footer class="approval-line-dialog-footer">
			    <p id="approval-line-message"
			       class="approval-line-section-help"
			       role="status"></p>

			    <button id="approval-line-cancel"
			            type="button"
			            class="button"
			            autofocus>
			        취소
			    </button>

			    <button id="approval-line-apply"
			            type="button"
			            class="button primary"
			            disabled>
			        적용
			    </button>
			</footer>
		</dialog>

		<script src="${pageContext.request.contextPath}/js/request-detail-renderer.js?v=20261113-sql-meta"></script>
		<script src="${pageContext.request.contextPath}/js/request-detail-author.js?v=20261111-db-change-redraft"></script>

		<%@ include file="/WEB-INF/views/fragments/bottom.jspf" %>
