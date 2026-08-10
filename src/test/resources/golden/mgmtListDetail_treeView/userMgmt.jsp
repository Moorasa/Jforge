<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html lang="ko">
<head>
<%-- 번들 런타임 매니페스트(jQuery 3.7.1 + jworks 6종 + commonList* + js-singleton/js-constants). 로컬 참조만. --%>
<jsp:include page="../common/header.jsp" />
<link rel="stylesheet" href="${ctx}/css/admin/userMgmt/userMgmtList.css" />
</head>
<body>
<%-- shell → List → List{View} 조립 컨테이너. List는 iframe 없이 동일 문서 내 include 배선(MVP). --%>
<div id="userMgmt-shell" class="page-shell" data-stem="userMgmt" data-archetype="MGMT_LIST_DETAIL" data-role="admin">
    <jsp:include page="./userMgmtList.jsp" />
</div>
<script defer src="${ctx}/js/admin/userMgmt/userMgmtList.js"></script>
</body>
</html>
