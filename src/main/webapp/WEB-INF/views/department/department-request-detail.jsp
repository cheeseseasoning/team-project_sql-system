<%@ page pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<div class="request-detail-page request-document-page">

    <div id="department-detail-config"
         data-request-id="<c:out value='${requestId}'/>"
         hidden></div>

    <a id="department-back"
       class="back-link"
       href="${pageContext.request.contextPath}/department-requests">
        ← 부서 요청함
    </a>

    <article class="request-document" aria-labelledby="detail-title">
        <header class="request-document-heading">
            <p id="request-document-heading" class="request-document-caption">
                SQLFlow · 데이터 조회 기안서
            </p>
            <h1 id="detail-title">기안을 불러오는 중입니다.</h1>
        </header>

        <div id="department-detail-content" hidden>
            <%-- 열람·결과 처리 모드는 유지하고, 공통 기안문 배치만 사용한다. --%>
            <c:set var="requestDetailReadOnly" value="${true}" />
            <c:set var="requestDetailDocument" value="${true}" />
            <%@ include file="/WEB-INF/views/fragments/request-detail-body.jspf" %>
        </div>
    </article>

</div>

<script src="${pageContext.request.contextPath}/js/request-detail-renderer.js?v=20261113-sql-meta"></script>
<script src="${pageContext.request.contextPath}/js/department-request-detail.js?v=20261113-sql-download"></script>

<%@ include file="/WEB-INF/views/fragments/bottom.jspf" %>
