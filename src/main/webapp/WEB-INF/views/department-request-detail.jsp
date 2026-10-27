<%@ page pageEncoding="UTF-8" %>
<%@ include file="fragments/top.jspf" %>

<div id="department-detail-config"
     data-request-id="<c:out value='${requestId}'/>"
     hidden></div>

<a id="department-back"
   class="back-link"
   href="${pageContext.request.contextPath}/department-requests">
    ← 부서 기안함
</a>

<div class="page-heading">
    <div>
        <span id="department-request-no" class="caption"></span>
        <h1 id="department-title">기안을 불러오는 중입니다.</h1>
    </div>
</div>

<div id="department-detail-content" hidden>
    <section class="panel">
        <h2>기안 정보</h2>
        <dl id="department-metadata" class="key-values"></dl>
    </section>

    <section class="panel section-gap">
        <h2>조회 목적</h2>
        <p id="department-purpose"></p>

        <h3>상세 설명</h3>
        <p id="department-description"></p>
    </section>

    <section class="panel section-gap">
        <h2>제출된 SQL</h2>
        <div id="department-sql-list" class="section-gap"></div>
    </section>

    <section class="panel section-gap">
        <h2>결재선</h2>

        <div class="table-scroll">
            <table>
                <thead>
                    <tr>
                        <th>순서</th>
                        <th>결재자</th>
                        <th>부서 / 직급</th>
                        <th>상태</th>
                    </tr>
                </thead>
                <tbody id="department-approval-rows"></tbody>
            </table>
        </div>
    </section>
</div>

<script src="${pageContext.request.contextPath}/js/department-requests.js"></script>

<%@ include file="fragments/bottom.jspf" %>