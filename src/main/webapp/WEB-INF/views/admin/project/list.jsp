<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html lang="ko">
<head>
    <jsp:include page="../common/header.jsp" />
    <link rel="stylesheet" href="${ctx}/css/admin/common/forge-theme.css?v=${assetVer}" />
    <link rel="stylesheet" href="${ctx}/css/admin/project/projectList.css?v=${assetVer}" />
    <script defer src="${ctx}/js/admin/project/projectList.js?v=${assetVer}"></script>
</head>
<body class="frg-app">
    <jsp:include page="../common/shell-top.jsp"><jsp:param name="active" value="projects" /></jsp:include>
    <nav class="frg-breadcrumb" aria-label="위치"><span>관리</span> &rsaquo; <span>프로젝트</span></nav>

    <main id="frg-content" class="frg-content frg-project">
        <section class="frg-section">
            <h2 id="frg-form-title">프로젝트 등록</h2>
            <form id="frg-project-form" class="frg-form" autocomplete="off">
                <div class="frg-form-grid">
                    <label class="frg-fld">
                        <span class="frg-label">이름</span>
                        <input class="frg-input" type="text" name="projectName" required maxlength="200">
                    </label>
                    <label class="frg-fld frg-fld-wide">
                        <span class="frg-label">타겟 루트 경로</span>
                        <input class="frg-input frg-mono" type="text" name="targetRootPath" required maxlength="1000"
                               placeholder="C:\parkDev\my-target (이 폴더 아래 jsp/js/css 생성)">
                        <small id="frg-path-hint" class="frg-hint"></small>
                    </label>
                    <%-- 생성 엔진이 Controller/Mapper stub 의 폴더 경로로 쓴다 — 비어 있으면 파일 생성이 막힌다. --%>
                    <label class="frg-fld">
                        <span class="frg-label">패키지 베이스</span>
                        <input class="frg-input frg-mono" type="text" name="packageBase" required maxlength="300"
                               pattern="[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)*"
                               title="소문자 자바 패키지 형식 (예: com.acme.app)"
                               placeholder="com.acme.app">
                        <small class="frg-hint">생성될 Controller/Mapper 의 패키지가 됩니다.</small>
                    </label>
                </div>
                <div class="frg-actions">
                    <%-- 수정 모드일 때 라벨이 '수정 저장'으로 바뀌고 취소 버튼이 나타난다(projectList.js) --%>
                    <button type="submit" id="frg-form-submit" class="frg-btn frg-btn-primary">저장</button>
                    <button type="button" id="frg-form-cancel" class="frg-btn frg-btn-secondary" hidden>취소</button>
                    <span id="frg-form-msg" class="frg-msg" role="status" aria-live="polite"></span>
                </div>
            </form>
        </section>

        <section class="frg-section">
            <h2>프로젝트 목록</h2>
            <table class="frg-table">
                <thead>
                    <tr><th>ID</th><th>이름</th><th>타겟 루트</th><th>패키지</th><th></th></tr>
                </thead>
                <tbody id="frg-project-rows">
                    <tr><td colspan="5" class="frg-empty">로딩 중…</td></tr>
                </tbody>
            </table>
        </section>

        <%-- P11: 타겟 DB 연결 설정 모달. JDBC URL은 받지 않는다(서버가 조각으로 조립 — 계약 §15). --%>
        <div id="frg-db-backdrop" class="frg-modal-backdrop" hidden>
            <div class="frg-modal" role="dialog" aria-modal="true" aria-labelledby="frg-db-title">
                <div class="frg-modal-head">
                    <span id="frg-db-title">타겟 DB 연결</span>
                    <button type="button" id="frg-db-close" class="frg-modal-close" aria-label="닫기">×</button>
                </div>
                <div class="frg-modal-body">
                    <p class="frg-hint" id="frg-db-target"></p>
                    <form id="frg-db-form" class="frg-form" autocomplete="off">
                        <div class="frg-form-grid">
                            <label class="frg-fld">
                                <span class="frg-label">호스트</span>
                                <input class="frg-input frg-mono" type="text" name="host" required
                                       maxlength="255" placeholder="localhost">
                            </label>
                            <label class="frg-fld">
                                <span class="frg-label">포트</span>
                                <input class="frg-input frg-mono" type="number" name="port" required
                                       min="1" max="65535" value="5432">
                            </label>
                            <label class="frg-fld">
                                <span class="frg-label">데이터베이스</span>
                                <input class="frg-input frg-mono" type="text" name="database" required
                                       maxlength="64" placeholder="postgres">
                            </label>
                            <label class="frg-fld">
                                <span class="frg-label">스키마</span>
                                <input class="frg-input frg-mono" type="text" name="schema"
                                       maxlength="64" placeholder="public">
                            </label>
                            <label class="frg-fld">
                                <span class="frg-label">사용자</span>
                                <input class="frg-input frg-mono" type="text" name="username" required
                                       maxlength="64" placeholder="읽기전용 계정 권장">
                            </label>
                            <label class="frg-fld">
                                <span class="frg-label">비밀번호</span>
                                <input class="frg-input" type="password" name="password" maxlength="200"
                                       placeholder="비워두면 저장된 값 유지">
                            </label>
                        </div>
                        <small class="frg-hint">스키마 조회(테이블·컬럼)에만 쓰입니다. 비밀번호는 암호화해 저장하며
                            화면에 다시 표시되지 않습니다. <strong>읽기전용 계정</strong>을 권장합니다.</small>
                    </form>
                </div>
                <div class="frg-modal-foot">
                    <span id="frg-db-msg" class="frg-msg" role="status" aria-live="polite"></span>
                    <button type="button" id="frg-db-delete" class="frg-btn frg-btn-danger frg-btn-sm">연결 해제</button>
                    <button type="button" id="frg-db-test" class="frg-btn frg-btn-secondary">연결 테스트</button>
                    <button type="button" id="frg-db-save" class="frg-btn frg-btn-primary">저장</button>
                </div>
            </div>
        </div>
    </main>
</body>
</html>
