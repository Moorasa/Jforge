<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%-- J-FORGE 번들 런타임 매니페스트 (JWorks admin/common/header.jsp 규약 복제, 스크립트릿 없음) --%>
<%-- 경로는 context-path 인식을 위해 EL 사용. jQuery는 3.7.1, JWORKS 저작권 배너 없음. --%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />

<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0, user-scalable=no">
<meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1" />

<title>J-FORGE</title>

<link rel="stylesheet" href="${ctx}/external/jworks/jworks-loadingspinner-1.0.0.css?v=${assetVer}" />
<link rel="stylesheet" href="${ctx}/external/jworks/jworks-snackbar-1.0.0.css?v=${assetVer}" />
<link rel="stylesheet" href="${ctx}/external/jworks/jworks-alert-1.0.0.css?v=${assetVer}" />
<link rel="stylesheet" href="${ctx}/external/jworks/jworks-confirm-1.0.0.css?v=${assetVer}" />
<link rel="stylesheet" href="${ctx}/external/jworks/jworks-tooltip-1.0.0.css?v=${assetVer}" />
<link rel="stylesheet" href="${ctx}/external/jworks/jworks-empty-view-0.0.1.css?v=${assetVer}" />
<link rel="stylesheet" href="${ctx}/css/admin/common/init.css?v=${assetVer}" />
<link rel="stylesheet" href="${ctx}/css/admin/common/commonSection.css?v=${assetVer}" />
<link rel="stylesheet" href="${ctx}/css/admin/common/commonPopup.css?v=${assetVer}" />
<link rel="stylesheet" href="${ctx}/css/admin/common/commonListTableView.css?v=${assetVer}" />
<link rel="stylesheet" href="${ctx}/css/admin/common/commonListCardView.css?v=${assetVer}" />
<link rel="stylesheet" href="${ctx}/css/admin/common/commonListTreeView.css?v=${assetVer}" />
<link rel="stylesheet" href="${ctx}/css/admin/common/commonListFormView.css?v=${assetVer}" />
<link rel="stylesheet" href="${ctx}/css/admin/common/commonScreenLayout.css?v=${assetVer}" />

<script defer src="${ctx}/external/jquery/jquery-3.7.1.min.js?v=${assetVer}"></script>
<script defer src="${ctx}/external/jworks/jworks-loadingspinner-1.0.0.js?v=${assetVer}"></script>
<script defer src="${ctx}/external/jworks/jworks-snackbar-1.0.0.js?v=${assetVer}"></script>
<script defer src="${ctx}/external/jworks/jworks-alert-1.0.0.js?v=${assetVer}"></script>
<script defer src="${ctx}/external/jworks/jworks-confirm-1.0.0.js?v=${assetVer}"></script>
<script defer src="${ctx}/external/jworks/jworks-tooltip-1.0.0.js?v=${assetVer}"></script>
<script defer src="${ctx}/external/jworks/jworks-empty-view-0.0.1.js?v=${assetVer}"></script>
<jsp:include page="./js-singleton.jsp" />
<jsp:include page="./js-constants.jsp" />
<script defer src="${ctx}/js/admin/common/constants.js?v=${assetVer}"></script>
<script defer src="${ctx}/js/admin/common/sendPost.js?v=${assetVer}"></script>
<script defer src="${ctx}/js/admin/common/common-ajax.js?v=${assetVer}"></script>
<script defer src="${ctx}/js/admin/common/commonUtils.js?v=${assetVer}"></script>
<script defer src="${ctx}/js/admin/common/commonSection.js?v=${assetVer}"></script>
<script defer src="${ctx}/js/admin/common/commonPopup.js?v=${assetVer}"></script>
<script defer src="${ctx}/js/admin/common/commonList.js?v=${assetVer}"></script>
<script defer src="${ctx}/js/admin/common/commonListTableView.js?v=${assetVer}"></script>
<script defer src="${ctx}/js/admin/common/commonListCardView.js?v=${assetVer}"></script>
<script defer src="${ctx}/js/admin/common/commonListTreeView.js?v=${assetVer}"></script>
<script defer src="${ctx}/js/admin/common/commonListFormView.js?v=${assetVer}"></script>
<script defer src="${ctx}/js/admin/common/commonAttributeHandler.js?v=${assetVer}"></script>
