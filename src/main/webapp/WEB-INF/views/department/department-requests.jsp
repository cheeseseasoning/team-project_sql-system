<%@ page pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<div class="department-requests-page">
    <div class="page-heading department-requests-heading">
        <h1><c:out value="${pageTitle}"/></h1>
    </div>

    <section id="department-request-list" class="panel">
        <form id="department-filter-form" class="filter-bar">
            <label class="department-filter-keyword" for="department-keyword">
                요청 검색
                <input id="department-keyword"
                       name="keyword"
                       maxlength="120"
                       placeholder="요청번호 또는 제목">
            </label>

            <label class="request-type-filter" for="department-type">
                요청 유형
                <select id="department-type" name="type">
                    <option value="">전체 유형</option>
                    <option value="SELECT">조회</option>
                    <option value="CREATE_VIEW">뷰 생성</option>
                    <option value="INSERT">데이터 등록</option>
                    <option value="UPDATE">데이터 수정</option>
                    <option value="DELETE">데이터 삭제</option>
                </select>
            </label>

            <label for="department-status">
                처리 상태
                <select id="department-status" name="status">
                    <option value="">전체 상태</option>
                </select>
            </label>

            <button type="submit" class="button primary">검색</button>
            <button id="department-clear"
                    type="button"
                    class="button refresh-button"
                    title="검색 조건 초기화 및 새로고침"
                    aria-label="검색 조건 초기화 및 새로고침">
                <img src="${pageContext.request.contextPath}/icons/RefreshCw.svg" alt="">
            </button>
        </form>

        <div class="panel-heading request-list-heading">
            <h2>요청 목록</h2>
            <span id="department-count" class="caption request-list-count" aria-live="polite"></span>
        </div>

        <div class="table-scroll">
            <table class="request-list-table">
                <caption class="sr-only">부서 기안함 목록</caption>
                <colgroup>
                    <col class="request-number-column">
                    <col class="request-title-column">
                    <col class="request-type-column">
                    <col class="request-drafter-column">
                    <col class="request-department-column">
                    <col class="request-status-column">
                    <col class="request-date-column">
                </colgroup>
                <thead>
                    <tr>
                        <th scope="col">요청번호</th>
                        <th scope="col">요청명</th>
                        <th scope="col">유형</th>
                        <th scope="col">작성자</th>
                        <th scope="col">부서</th>
                        <th scope="col">처리현황</th>
                        <th scope="col">요청일</th>
                    </tr>
                </thead>
                <tbody id="department-rows">
                    <tr>
                        <td colspan="7" class="empty">불러오는 중입니다.</td>
                    </tr>
                </tbody>
            </table>
        </div>

        <nav id="department-pagination"
             class="request-pagination"
             aria-label="부서 기안함 페이지 이동">
        </nav>
    </section>
</div>

<script src="${pageContext.request.contextPath}/js/department-requests.js?v=20261113-inbox"></script>

<%@ include file="/WEB-INF/views/fragments/bottom.jspf" %>