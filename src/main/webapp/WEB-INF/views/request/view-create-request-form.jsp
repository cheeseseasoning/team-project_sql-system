<%@ page pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<div id="view-create-edit-config"
     data-request-id="<c:out value='${requestId}'/>"
     data-edit-mode="${viewCreateEdit}"
     hidden></div>

	 <div class="page-heading panel-aligned-heading">
	     <h1>${viewCreateEdit ? 'DB 변경 요청 수정' : 'DB 변경 요청 작성'}</h1>
	     <span class="badge">${viewCreateEdit ? '초안 수정' : '초안 작성'}</span>
	 </div>

	 <form id="view-create-request-form" class="panel form-panel">
	     <div class="db-change-request-top">
	         <label>
	             요청 제목 <span class="required">*</span>
	             <input id="view-request-title"
	                    name="title"
	                    required
	                    maxlength="200"
	                    placeholder="예: 월별 매출 집계 뷰 생성">
	         </label>

	         <label>
	             처리 희망일 <span class="caption">· 선택</span>
	             <input id="view-request-date" name="desiredDate" type="date">
	         </label>

	         <label class="db-change-urgent">
	             <input id="view-request-urgent" name="isUrgent" type="checkbox">
	             <span>긴급</span>
	         </label>
	     </div>

	     <label for="view-request-purpose">
	         변경 목적 <span class="required">*</span>
	     </label>
	     <textarea id="view-request-purpose"
	               name="purpose"
	               rows="2"
	               required
	               placeholder="변경이 필요한 이유와 사용 목적을 입력하세요."></textarea>

	     <label for="view-request-detail">
	         상세 내용 <span class="caption">· 선택</span>
	     </label>
	     <textarea id="view-request-detail"
	               name="detailContent"
	               rows="3"
	               placeholder="변경 대상, 요청 배경과 처리 시 주의사항을 입력하세요."></textarea>

	     <div class="db-change-sql-header section-gap">
	         <div class="db-change-sql-type">
	             <h2>SQL 작성</h2>
	             <select id="db-change-type"
	                     aria-label="변경 유형"
	                     ${viewCreateEdit ? 'disabled' : ''}>
	                 <option value="CREATE_VIEW">CREATE VIEW · 뷰 생성</option>
	                 <option value="INSERT">INSERT · 데이터 추가</option>
	                 <option value="UPDATE">UPDATE · 데이터 수정</option>
	                 <option value="DELETE">DELETE · 데이터 삭제</option>
	             </select>
	         </div>

	         <div id="view-sql-write-modes" class="db-change-write-modes" role="group" aria-label="SQL 작성 방식">
	             <button type="button" data-write-mode="direct" aria-pressed="true">직접 작성</button>
	             <button type="button" data-write-mode="ai" aria-pressed="false">AI로 작성</button>
	         </div>
	     </div>

	     <div class="db-change-sql-toolbar">
	         <div id="view-sql-tabs" class="db-change-sql-tabs" role="group" aria-label="SQL 항목 선택"></div>
	         <div class="db-change-form-actions">
	             <span id="view-sql-count" class="caption"></span>
	             <button id="view-add-sql" type="button" class="button">+ SQL 추가</button>
	         </div>
	     </div>

	     <div id="view-sql-items"></div>
	     <p id="view-form-message" class="caption" role="status" aria-live="polite"></p>

	     <div class="form-footer">
	         <span id="view-save-guide" class="caption">
	             초안 저장 후 내 기안함에서 결재선을 지정하고 제출할 수 있습니다.
	         </span>

	         <div class="db-change-form-actions">
	             <button id="view-validate-sqls" type="button" class="button">SQL 검증</button>
	             <button id="view-save-draft"
	                     type="button"
	                     class="button primary"
	                     aria-describedby="view-save-guide"
	                     ${viewCreateEdit ? 'disabled' : ''}>
	                 ${viewCreateEdit ? '수정 저장' : '초안 저장'}
	             </button>
	         </div>
	     </div>
	 </form>

<template id="view-sql-item-template">
    <section class="sql-item">
        <div class="db-change-sql-field-heading">
            <label>
                SQL 제목 <span class="required">*</span>
                <input class="view-sql-title" required maxlength="200" placeholder="예: 상품 목록 변경">
            </label>
            <button type="button" class="text-button" data-action="remove">삭제</button>
        </div>

        <div class="db-change-sql-workspace">
            <div class="db-change-ai-prompt">
                <label>
                    자연어 요청
                    <textarea rows="6" placeholder="어떤 변경이 필요한지 설명해 주세요."></textarea>
                </label>
                <div class="db-change-ai-actions">
                    <span class="caption">AI 생성 연결 예정</span>
                    <button type="button" class="button primary" data-ai-generate disabled>SQL 초안 생성 →</button>
                </div>
            </div>
            <label>
                <span class="db-change-direct-label">SQL 구문</span>
                <span class="db-change-ai-label">생성된 SQL</span>
                <span class="required">*</span>
                <textarea class="sql-editor" rows="8" required
                          maxlength="20000" spellcheck="false"></textarea>
            </label>
        </div>
    </section>
</template>

<script src="${pageContext.request.contextPath}/js/view-create-request-form.js?v=20261109-db-change-ai-layout"
        defer></script>

<%@ include file="/WEB-INF/views/fragments/bottom.jspf" %>
