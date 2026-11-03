<%@ page pageEncoding="UTF-8" %>
<%@ include file="fragments/top.jspf" %>

<div id="view-create-edit-config"
     data-request-id="<c:out value='${requestId}'/>"
     data-edit-mode="${viewCreateEdit}"
     hidden></div>

<div class="page-heading">
    <h1>${viewCreateEdit ? 'DB 변경 요청 수정' : 'DB 변경 요청 작성'}</h1>
    <span class="badge">${viewCreateEdit ? '초안 수정' : '초안 작성'}</span>
</div>

<div class="workflow" aria-label="처리 단계">
    <strong>1 요청·SQL 작성</strong>
    <span>2 초안 저장·결재선 확인</span>
    <span>3 결재</span>
    <span>4 DB팀 외부 처리·결과 등록</span>
</div>

<form id="view-create-request-form" class="panel form-panel">
    <div class="panel-heading">
        <h2>요청 정보</h2>
    </div>

    <div class="form-grid">
        <label>
            변경 유형
            <select id="db-change-type"
                    aria-describedby="db-change-guide"
                    ${viewCreateEdit ? 'disabled' : ''}>
                <option value="CREATE_VIEW">뷰 생성</option>
                <option value="DATA_CHANGE">데이터 변경</option>
            </select>
        </label>

        <label>
            요청 제목 <span class="required">*</span>
            <input id="view-request-title"
                   name="title"
                   required
                   maxlength="200"
                   placeholder="예: 월별 매출 집계 뷰 생성">
        </label>
    </div>

    <p id="db-change-guide" class="caption">
        신규 뷰를 만드는 CREATE VIEW 문을 요청합니다.
    </p>

    <label for="view-request-purpose">
        요청 목적 <span class="required">*</span>
    </label>
    <textarea id="view-request-purpose"
              name="purpose"
              rows="3"
              required
              placeholder="변경이 필요한 이유와 사용 목적을 입력하세요."></textarea>

    <label for="view-request-detail">상세 내용</label>
    <textarea id="view-request-detail"
              name="detailContent"
              rows="4"
              placeholder="변경 대상, 요청 배경과 처리 시 주의사항을 입력하세요."></textarea>

    <div class="form-grid">
        <label class="urgent-option">
            <input id="view-request-urgent" name="isUrgent" type="checkbox">
            <span>
                <strong>긴급 요청</strong>
                <small>빠른 처리가 필요한 경우 체크하세요.</small>
            </span>
        </label>

        <label>
            희망일
            <input id="view-request-date" name="desiredDate" type="date">
        </label>
    </div>

    <div class="panel-heading section-gap">
        <h2>변경 SQL <span id="view-sql-count" class="muted"></span></h2>
        <div>
            <button id="view-add-sql" type="button" class="button">
                + SQL 추가
            </button>
            <button id="view-validate-sqls" type="button" class="button">
                구문 검사
            </button>
        </div>
    </div>

    <p id="view-sql-guide" class="caption">
        항목마다 CREATE VIEW 문 한 개를 작성하세요. 위에서 아래 순서로 처리됩니다.
    </p>

    <div id="view-sql-items"></div>

    <p id="view-form-message" class="caption" role="status" aria-live="polite"></p>

    <div class="form-footer">
        <span id="view-save-guide" class="caption">
            초안 저장 후 내 기안함에서 결재선을 지정하고 제출할 수 있습니다.
        </span>

        <button id="view-save-draft"
                type="button"
                class="button primary"
                aria-describedby="view-save-guide"
                ${viewCreateEdit ? 'disabled' : ''}>
            ${viewCreateEdit ? '수정 저장' : '초안 저장'}
        </button>
    </div>
</form>

<template id="view-sql-item-template">
    <section class="sql-item">
        <div class="panel-heading">
            <h3 class="view-sql-heading"></h3>
            <div>
                <button type="button" class="text-button" data-action="up">위로</button>
                <button type="button" class="text-button" data-action="down">아래로</button>
                <button type="button" class="text-button" data-action="remove">삭제</button>
            </div>
        </div>

        <label>
            SQL 제목 <span class="caption">(선택)</span>
            <input class="view-sql-title" maxlength="200" placeholder="예: 상품 목록 변경">
        </label>

        <label>
            SQL 구문 <span class="required">*</span>
            <textarea class="sql-editor"
                      rows="8"
                      required
                      maxlength="20000"
                      spellcheck="false"
                      aria-describedby="view-sql-guide"></textarea>
        </label>
    </section>
</template>

<script src="${pageContext.request.contextPath}/js/view-create-request-form.js?v=20261103-change-form"
        defer></script>

<%@ include file="fragments/bottom.jspf" %>