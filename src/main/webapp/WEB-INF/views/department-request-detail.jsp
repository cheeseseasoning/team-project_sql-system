<%@ page pageEncoding="UTF-8" %>
<%@ include file="fragments/top.jspf" %>

<div class="request-detail-page">

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
            <span id="request-number" class="caption"></span>
            <h1 id="detail-title">기안을 불러오는 중입니다.</h1>
        </div>

        <div>
            <span id="detail-state"></span>
        </div>
    </div>

    <div id="department-detail-content" hidden>
        <c:set var="requestDetailReadOnly" value="${true}" />
        <%@ include file="fragments/request-detail-body.jspf" %>
    </div>

</div>

<script src="${pageContext.request.contextPath}/js/request-detail-renderer.js?v=20261029-view-history"></script>
<script src="${pageContext.request.contextPath}/js/department-request-detail.js?v=20261029-view-history"></script>

<%@ include file="fragments/bottom.jspf" %>
