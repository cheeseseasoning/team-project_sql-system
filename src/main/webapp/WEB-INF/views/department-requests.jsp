<%@ page pageEncoding="UTF-8" %>
<%@ include file="fragments/top.jspf" %>

<div class="page-heading">
    <div>
        <h1>부서 기안함</h1>
        <p class="caption">열람 권한이 있는 제출된 기안을 확인합니다.</p>
    </div>
</div>

<section class="panel">
    <form id="department-filter-form" class="filter-bar">
        <label>
            기안 검색
            <input id="department-keyword"
                   name="keyword"
                   maxlength="120"
                   placeholder="요청번호, 제목, 작성자 또는 부서">
        </label>

        <label>
            처리 상태
            <select id="department-status" name="status">
                <option value="">전체</option>
            </select>
        </label>

        <button type="submit" class="button primary">검색</button>

        <button id="department-clear" type="button" class="button">
            초기화
        </button>
    </form>

    <div class="panel-heading">
        <h2>기안 목록</h2>
        <span id="department-count" class="caption"></span>
    </div>

    <div class="table-scroll">
        <table>
            <thead>
                <tr>
                    <th>요청명 / 요청번호</th>
                    <th>작성자</th>
                    <th>부서</th>
                    <th>처리 현황</th>
                    <th>제출 시각</th>
                    <th>희망일</th>
                </tr>
            </thead>
            <tbody id="department-rows"></tbody>
        </table>
    </div>

    <nav class="form-footer" aria-label="부서 기안함 페이지 이동">
        <button id="department-previous"
                type="button"
                class="button"
                disabled>
            이전
        </button>

        <span id="department-page-info"
              class="caption"
              aria-live="polite"></span>

        <button id="department-next"
                type="button"
                class="button"
                disabled>
            다음
        </button>
    </nav>
</section>

<script src="${pageContext.request.contextPath}/js/department-requests.js"></script>

<%@ include file="fragments/bottom.jspf" %>