<%@ page pageEncoding="UTF-8" %>
<%@ include file="fragments/top.jspf" %>

<div class="page-heading">
    <h1>뷰 생성 요청 작성</h1>
    <span class="badge">초안 작성</span>
</div>

<div class="workflow" aria-label="처리 단계">
    <strong>1 요청·SQL 작성</strong>
    <span>2 초안 저장·결재선 확인</span>
    <span>3 결재</span>
    <span>4 DB팀 외부 처리·결과 등록</span>
</div>

<form id="view-create-request-form" class="panel form-panel">
    <div class="form-grid">
        <label>
            요청 제목 <span class="required">*</span>
            <input id="view-request-title"
                   name="title"
                   required
                   maxlength="200"
                   placeholder="예: 월별 매출 집계 뷰 생성 요청">
        </label>

        <label>
            생성 목적 <span class="required">*</span>
            <textarea id="view-request-purpose"
                      name="purpose"
                      rows="3"
                      required
                      placeholder="뷰가 필요한 업무와 사용 목적을 입력하세요."></textarea>
        </label>
    </div>

    <label for="view-request-detail">상세 내용</label>
    <textarea id="view-request-detail"
              name="detailContent"
              rows="5"
              placeholder="요청 배경, 필요한 데이터·컬럼, 사용 대상과 주의사항 등을 입력하세요."></textarea>

    <div class="form-grid">
        <label class="urgent-option">
            <input id="view-request-urgent"
                   name="isUrgent"
                   type="checkbox">
            <span>
                <strong>긴급 요청</strong>
                <small>빠른 처리가 필요한 경우 체크하세요.</small>
            </span>
        </label>

        <label>
            희망일
            <input id="view-request-date"
                   name="desiredDate"
                   type="date">
        </label>
    </div>

    <p class="caption">
        긴급 여부와 희망일은 처리 담당자의 참고 정보입니다.
    </p>

    <div class="panel-heading section-gap">
        <h2>
            생성 SQL
            <span id="view-sql-count" class="muted"></span>
        </h2>

        <button id="view-add-sql" type="button" class="button">
            + SQL 추가
        </button>
    </div>

    <p id="view-sql-guide" class="caption">
        CREATE VIEW 전체 구문을 입력하세요.
        SQL은 위에서 아래 순서로 처리하도록 요청됩니다.
        앞 SQL에서 생성한 뷰를 뒤 SQL에서 참조한다면 순서를 확인하세요.
    </p>

    <div id="view-sql-items"></div>

    <p id="view-form-message"
       class="caption"
       role="status"
       aria-live="polite"></p>

    <div class="form-footer">
        <span class="caption">
            승인된 SQL은 DB팀이 외부 도구에서 실행하고 처리 결과를 등록합니다.
        </span>

        <div>
            <button type="submit" class="button">
                입력 확인
            </button>
            <button type="button"
                    class="button primary"
                    disabled
                    aria-describedby="view-save-guide">
                초안 저장
            </button>
        </div>
    </div>

    <p id="view-save-guide" class="caption">
        현재는 작성 화면 확인 단계이며, 초안 저장 기능은 연결 예정입니다.
    </p>
</form>

<template id="view-sql-item-template">
    <section class="sql-item">
        <div class="panel-heading">
            <h3 class="view-sql-heading"></h3>

            <div>
                <button type="button"
                        class="text-button"
                        data-action="up">
                    위로
                </button>
                <button type="button"
                        class="text-button"
                        data-action="down">
                    아래로
                </button>
                <button type="button"
                        class="text-button"
                        data-action="remove">
                    삭제
                </button>
            </div>
        </div>

        <label>
            SQL 제목
            <input class="view-sql-title"
                   maxlength="200"
                   placeholder="예: 월별 매출 집계 뷰 · 생략 가능">
        </label>

        <label>
            생성 SQL <span class="required">*</span>
            <textarea class="sql-editor"
                      rows="9"
                      required
                      maxlength="20000"
                      spellcheck="false"
                      aria-describedby="view-sql-guide"
                      placeholder="CREATE VIEW 뷰이름 AS&#10;SELECT 컬럼&#10;FROM 테이블;"></textarea>
        </label>
    </section>
</template>

<script src="${pageContext.request.contextPath}/js/view-create-request-form.js"
        defer></script>

<%@ include file="fragments/bottom.jspf" %>