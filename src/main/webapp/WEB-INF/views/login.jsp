<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>로그인 | SQLFlow</title>
	
	<link rel="stylesheet"
	      href="${pageContext.request.contextPath}/css/sqlflow.css">

	<link rel="stylesheet"
	      href="${pageContext.request.contextPath}/css/layout.css">

	<link rel="stylesheet"
	      href="${pageContext.request.contextPath}/css/pages/login.css">
</head>
<body>
    <main class="panel login-panel">
        <a class="brand" href="${pageContext.request.contextPath}/login">SQLFlow</a>
        <h1>로그인</h1>
        <p class="muted">등록된 사내 계정으로 로그인하세요.</p>

        <c:if test="${param.error != null}">
            <p class="login-error" role="alert">아이디 또는 비밀번호를 확인하거나 계정 활성 상태를 확인하세요.</p>
        </c:if>
        <c:if test="${param.logout != null}">
            <p class="muted" role="status">로그아웃했습니다.</p>
        </c:if>

        <form action="${pageContext.request.contextPath}/login" method="post">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
            <label for="username">로그인 ID
                <input id="username" name="username" autocomplete="username" required autofocus>
            </label>
            <label for="password">비밀번호
                <input id="password" type="password" name="password"
                       autocomplete="current-password" required>
            </label>
            <button class="button primary" type="submit">로그인</button>
        </form>
    </main>
</body>
</html>
