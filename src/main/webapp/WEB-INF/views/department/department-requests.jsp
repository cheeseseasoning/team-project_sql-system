<%@ page pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<main class="department-requests-page">
    <div class="department-requests-heading">
        <div>
            <h1>부서 요청 내역</h1>
            <p class="caption">
                <c:out value="${deptName}"/>에서 제출한 요청과 처리 상태를 확인하세요.
            </p>
        </div>

        <span class="department-scope">
            조회 범위 · <c:out value="${deptName}"/>
        </span>
    </div>

    <form id="department-filter-form" class="department-filter">
        <label class="department-filter-keyword" for="department-keyword">
            요청 검색
            <input id="department-keyword"
                   name="keyword"
                   maxlength="120"
                   placeholder="제목 또는 요청번호">
        </label>

        <label for="department-type">
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

        <div class="department-filter-actions">
            <button type="submit" class="button primary">검색</button>
            <button id="department-clear" type="button" class="button">
                초기화
            </button>
        </div>
    </form>

    <div class="department-list-heading">
        <h2>요청 목록</h2>
        <span id="department-count" class="caption" aria-live="polite"></span>
    </div>

    <div class="department-table-wrap">
        <table class="department-table">
            <thead>
                <tr>
                    <th scope="col">요청번호</th>
                    <th scope="col">요청명</th>
                    <th scope="col">유형</th>
                    <th scope="col">작성자</th>
                    <th scope="col">부서</th>
                    <th scope="col">처리 현황</th>
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
         class="department-pagination"
         aria-label="부서 기안함 페이지 이동">
    </nav>
</main>

<script src="${pageContext.request.contextPath}/js/department-requests.js"></script>

<%@ include file="/WEB-INF/views/fragments/bottom.jspf" %>