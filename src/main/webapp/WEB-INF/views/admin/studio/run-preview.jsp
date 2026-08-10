<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html lang="ko">
<head>
    <%-- P9 실행 미리보기 래퍼. 생성 shell 의 매니페스트 include(../common/header.jsp)를 이 실제
         include 가 대체한다(동일 번들 런타임). per-screen CSS/JS 는 asset 엔드포인트로 주입 —
         생성물과 동일하게 "외부 파일" 문맥을 유지한다(GenEscaper 이스케이프 계약 보존).
         ★ forge-theme.css(빌더 테마)는 로드하지 않는다 — 생성물 룩 그대로. --%>
    <jsp:include page="../common/header.jsp" />
    <c:forEach var="k" items="${previewCssKeys}">
    <link rel="stylesheet" href="${ctx}/admin/studio/run-preview/${previewScreenId}/asset/${k}" />
    </c:forEach>
    <title>실행 미리보기 — <c:out value="${previewScreenName}" /></title>
    <style>
        /* 미리보기 안내 바(빌더 전용 오버레이 — 생성물 마크업 밖) */
        .frg-run-banner {
            position: sticky; top: 0; z-index: 9999;
            display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
            padding: 7px 14px;
            background: #161b24; color: #e2e8f2;
            font: 600 12px 'NanumBarunGothic', 'Malgun Gothic', sans-serif;
            border-bottom: 1px solid #323d4e;
        }
        .frg-run-banner b { color: #7fa9f5; }
        .frg-run-note { color: #8e9bb0; font-weight: 400; }
    </style>
</head>
<body>
    <div class="frg-run-banner">
        실행 미리보기 — <b><c:out value="${previewScreenName}" /></b>
        <span class="frg-run-note">저장본 기준 · 빌더가 대신 렌더(타겟 앱 불필요) ·
            데이터 API 미연결이라 목록은 비어 있음</span>
    </div>
    <%-- 변환된 생성 화면 본문(무이스케이프 의도 출력).
         내용물 = 생성 템플릿 산출(HTML 문맥은 htmlText/htmlAttr 이스케이프 완료) — 생성물과 동일 신뢰. --%>
    ${previewBody}
    <c:forEach var="k" items="${previewJsKeys}">
    <script defer src="${ctx}/admin/studio/run-preview/${previewScreenId}/asset/${k}"></script>
    </c:forEach>
</body>
</html>
