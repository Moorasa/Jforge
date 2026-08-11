<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html lang="ko">
<head>
    <jsp:include page="./common/header.jsp" />
    <link rel="stylesheet" href="${ctx}/css/admin/common/forge-theme.css?v=${assetVer}" />
    <link rel="stylesheet" href="${ctx}/css/admin/home/home.css?v=${assetVer}" />
    <script defer src="${ctx}/js/admin/home/home.js?v=${assetVer}"></script>
</head>
<body class="frg-app">
    <jsp:include page="./common/shell-top.jsp"><jsp:param name="active" value="home" /></jsp:include>

    <main id="frg-content" class="frg-content frg-home">
        <%-- 요약 스트립: 프로젝트/화면 수 + 스튜디오 바로가기. home.js 가 textContent 로만 채움 --%>
        <section class="frg-home-hero">
            <div class="frg-hero-copy">
                <h2>화면을 조립하고, 3종 세트로 생성하세요</h2>
                <p>팔레트에서 모듈을 골라 슬롯에 배치하면 JWorks 규약의 JSP + JS + CSS 가
                   타겟 프로젝트에 그대로 생성됩니다.</p>
            </div>
            <div class="frg-hero-stats">
                <div class="frg-stat">
                    <span id="frg-stat-projects" class="frg-stat-num">–</span>
                    <span class="frg-stat-label">프로젝트</span>
                </div>
                <div class="frg-stat">
                    <span id="frg-stat-screens" class="frg-stat-num">–</span>
                    <span class="frg-stat-label">화면</span>
                </div>
                <a class="frg-btn frg-btn-primary frg-hero-cta" href="${ctx}/admin/studio">스튜디오 열기</a>
            </div>
        </section>

        <%-- 시작 가이드(P7-5): 처음 쓰는 사람을 위한 3단계 안내 --%>
        <section class="frg-section frg-howto">
            <h2>이렇게 사용하세요</h2>
            <ol class="frg-howto-steps">
                <li class="frg-howto-step">
                    <span class="frg-howto-num">1</span>
                    <div class="frg-howto-body">
                        <strong>프로젝트 연결</strong>
                        <p>파일이 생성될 타겟 폴더(절대경로)를 등록합니다.
                           스튜디오 안에서도 바로 만들 수 있습니다.</p>
                        <a class="frg-btn frg-btn-secondary frg-btn-sm" href="${ctx}/admin/projects">프로젝트 등록</a>
                    </div>
                </li>
                <li class="frg-howto-step">
                    <span class="frg-howto-num">2</span>
                    <div class="frg-howto-body">
                        <strong>화면 만들기 &amp; 조립</strong>
                        <p>화면 유형(관리/목록/분할)을 고르면 기본 모듈이 미리 배치됩니다.
                           팔레트에서 모듈을 더하고, 우측에서 속성만 바꾸면 됩니다.</p>
                        <a class="frg-btn frg-btn-secondary frg-btn-sm" href="${ctx}/admin/studio">스튜디오 열기</a>
                    </div>
                </li>
                <li class="frg-howto-step">
                    <span class="frg-howto-num">3</span>
                    <div class="frg-howto-body">
                        <strong>파일 생성</strong>
                        <p>[파일 생성]을 누르면 어떤 파일이 만들어지는지 먼저 보여주고,
                           확인 후 타겟 폴더에 JSP/JS/CSS 를 씁니다.</p>
                    </div>
                </li>
            </ol>
        </section>

        <%-- 프로젝트 카드 그리드. home.js 가 createElement/textContent 로만 채움(XSS 0) --%>
        <section class="frg-section">
            <h2>프로젝트</h2>
            <div id="frg-home-projects" class="frg-home-projects">
                <p class="frg-empty">불러오는 중…</p>
            </div>
        </section>
    </main>
</body>
</html>
