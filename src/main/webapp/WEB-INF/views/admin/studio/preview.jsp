<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html lang="ko">
<head>
    <%-- P3-6/P7-2: 중앙 캔버스 iframe 문서. 번들 런타임 매니페스트(jQuery 3.7.1 + jworks + commonList*)
         를 로드해 모듈 마크업이 실제 생성물과 같은 CSS 로 그려진다. 데이터는 부모(previewBridge)가
         postMessage 로 주입한다.
         ★ 경계: 이 캔버스는 근사 시각화이며 최종 생성물(생성 엔진 산출물)과 미세하게 다를 수 있다.
         ★ forge-theme.css(빌더 테마)는 여기 링크하지 않는다 — 생성물 룩 보존. --%>
    <jsp:include page="../common/header.jsp" />
    <link rel="stylesheet" href="${ctx}/css/admin/studio/preview.css?v=20260803-4" />
    <script defer src="${ctx}/js/admin/studio/slotMeta.js?v=20260803-4"></script>
    <script defer src="${ctx}/js/admin/studio/previewRenderer.js?v=20260803-4"></script>
</head>
<body class="frg-preview-doc">
    <%-- 렌더 타겟 컨테이너(비어 있음). previewRenderer.js 가 postMessage 수신 후 채운다. --%>
    <div id="frg-preview-root" class="frg-preview-root" aria-live="polite"></div>
</body>
</html>
