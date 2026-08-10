<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html lang="ko">
<head>
    <jsp:include page="../common/header.jsp" />
    <link rel="stylesheet" href="${ctx}/css/admin/common/forge-theme.css?v=20260803-4" />
    <link rel="stylesheet" href="${ctx}/css/admin/common/schemaForm.css?v=20260713-1" />
    <link rel="stylesheet" href="${ctx}/css/admin/studio/studio.css?v=20260803-4" />
    <script defer src="${ctx}/js/admin/studio/studioApp.js?v=20260803-4"></script>
    <script defer src="${ctx}/js/admin/studio/studioLayout.js?v=20260713-1"></script>
    <%-- P7-2: 슬롯 메타 단일 소스(속성패널/팔레트/캔버스 공용). 소비자들 앞에 로드 --%>
    <script defer src="${ctx}/js/admin/studio/slotMeta.js?v=20260803-4"></script>
    <%-- P3-4: 우측 속성패널 — 순수 렌더러(재사용) + 얇은 컨트롤러. studioApp(허브) 뒤 로드 --%>
    <script defer src="${ctx}/js/admin/catalog/schemaFormRenderer.js?v=20260713-3"></script>
    <script defer src="${ctx}/js/admin/studio/propsPanel.js?v=20260803-4"></script>
    <%-- P11: 타겟 DB 테이블/컬럼 선택기(속성패널 '데이터' 탭에서 호출) --%>
    <script defer src="${ctx}/js/admin/studio/dbTablePicker.js?v=20260730-1"></script>
    <%-- §19: 프레임에 넣을 화면 고르기. propsPanel 이 LAYOUT_FRAME 선택 시 호출 --%>
    <script defer src="${ctx}/js/admin/studio/screenPicker.js?v=20260807-1"></script>
    <%-- P3-5: 좌측 팔레트 + 슬롯 조립. studioApp(허브)/propsPanel(선택 연동) 뒤 로드 --%>
    <script defer src="${ctx}/js/admin/studio/palette.js?v=20260803-4"></script>
    <%-- P3-6: 프리뷰 iframe 브리지. studioApp.onDefinitionChanged 구독 → iframe 에 postMessage.
         studioApp.js(허브)는 수정하지 않고 별도 파일에서 공개 API 로만 결선한다. --%>
    <link rel="stylesheet" href="${ctx}/css/admin/studio/previewBridge.css?v=20260713-1" />
    <script defer src="${ctx}/js/admin/studio/previewBridge.js?v=20260803-4"></script>
    <%-- P7-4: 화면 관리(이름변경/복제/삭제) + 생성 dry-run 컨트롤러. studioApp(허브) 뒤 로드 --%>
    <script defer src="${ctx}/js/admin/studio/screenMgmt.js"></script>
    <%-- P7-5: 시작 가이드(빈 상태 안내 + 스튜디오 내 프로젝트 생성). studioApp(허브) 뒤 로드 --%>
    <script defer src="${ctx}/js/admin/studio/studioGuide.js"></script>
    <%-- 레이어 패널: 배치된 부품의 계층 표시. slotMeta(트리 계산)·previewBridge(선택 소유) 뒤 로드 --%>
    <script defer src="${ctx}/js/admin/studio/layerPanel.js?v=20260807-1"></script>
</head>
<body class="frg-app frg-studio-body">
    <jsp:include page="../common/shell-top.jsp"><jsp:param name="active" value="studio" /></jsp:include>

    <main id="frg-content" class="frg-content frg-studio">
        <%-- 상단바(다크): 프로젝트/화면 선택 + 화면관리 + undo/redo + 저장/생성 --%>
        <div class="frg-studio-topbar frg-dark">
            <div class="frg-topbar-group">
                <label class="frg-topbar-label" for="frg-project-select">프로젝트</label>
                <select id="frg-project-select" class="frg-select frg-topbar-select" aria-label="프로젝트 선택">
                    <option value="">로딩 중…</option>
                </select>
            </div>
            <div class="frg-topbar-group">
                <label class="frg-topbar-label" for="frg-screen-select">화면</label>
                <select id="frg-screen-select" class="frg-select frg-topbar-select" aria-label="화면 선택" disabled>
                    <option value="">프로젝트를 먼저 선택하세요</option>
                </select>
            </div>
            <%-- P7-4: 화면 관리 메뉴(이름변경/복제/삭제). screenMgmt.js 가 결선 --%>
            <button type="button" id="frg-btn-screen-menu" class="frg-btn frg-btn-secondary frg-btn-icon"
                    title="화면 관리 (이름변경·복제·삭제)" aria-haspopup="menu" disabled>⋯</button>
            <div id="frg-screen-menu" class="frg-screen-menu" role="menu" hidden>
                <button type="button" class="frg-menu-item" role="menuitem" data-menu="rename">이름 변경</button>
                <button type="button" class="frg-menu-item" role="menuitem" data-menu="duplicate">복제</button>
                <button type="button" class="frg-menu-item frg-menu-danger" role="menuitem" data-menu="delete">삭제</button>
            </div>

            <div class="frg-topbar-group frg-topbar-actions">
                <span id="frg-dirty-flag" class="frg-dirty-flag" hidden>● 미저장</span>
                <span id="frg-save-msg" class="frg-save-msg" role="status" aria-live="polite" hidden></span>
                <%-- P7-3: undo/redo. studioApp 히스토리 스택이 결선 --%>
                <span class="frg-topbar-undo-group">
                    <button type="button" id="frg-btn-undo" class="frg-btn frg-btn-secondary frg-btn-icon"
                            title="실행 취소 (Ctrl+Z)" disabled>↶</button>
                    <button type="button" id="frg-btn-redo" class="frg-btn frg-btn-secondary frg-btn-icon"
                            title="다시 실행 (Ctrl+Y)" disabled>↷</button>
                </span>
                <button type="button" id="frg-btn-new-screen" class="frg-btn frg-btn-secondary" disabled>새 화면</button>
                <%-- 저장 버튼(P3-6b 결선): 화면 선택 + 미저장 변경 시에만 활성. 저장 = DB (Ctrl+S) --%>
                <button type="button" id="frg-btn-save" class="frg-btn frg-btn-primary"
                        title="설계를 빌더 DB에 저장합니다 (Ctrl+S)" disabled>저장</button>
                <%-- 실행 미리보기(P9): 타겟 앱 없이 생성 화면을 빌더가 대신 렌더(새 탭, 저장본 기준) --%>
                <button type="button" id="frg-btn-run-preview" class="frg-btn frg-btn-secondary"
                        title="타겟 앱을 띄우지 않고, 저장된 설계를 실제 마크업·런타임으로 새 탭에서 봅니다" disabled>실행 미리보기</button>
                <%-- 생성 버튼(P4-5/P7-4): 화면 선택 시 활성. 생성 = 파일쓰기(dry-run 확인 경유) --%>
                <button type="button" id="frg-btn-generate" class="frg-btn frg-btn-generate"
                        title="저장된 설계로 타겟 프로젝트에 JSP/JS/CSS 파일을 씁니다" disabled>파일 생성</button>
            </div>
        </div>

        <%-- 새 화면 폼(P7-5: 쉬운 만들기 — 화면 유형 카드 + 기본 구성 프리셋 + 용어 순화) --%>
        <form id="frg-new-screen-form" class="frg-new-screen-form" hidden aria-label="새 화면 만들기">
            <div class="frg-modal">
                <div class="frg-modal-head">
                    <span>새 화면 만들기</span>
                    <button type="button" id="frg-nsf-cancel" class="frg-modal-close" aria-label="닫기">×</button>
                </div>
                <div class="frg-modal-body frg-nsf-body">
                    <div class="frg-nsf-field">
                        <label class="frg-label" for="frg-nsf-name">화면 이름</label>
                        <input type="text" id="frg-nsf-name" class="frg-input" maxlength="200"
                               placeholder="예: 사용자 관리" required />
                    </div>
                    <div class="frg-nsf-field">
                        <label class="frg-label" for="frg-nsf-stem">파일 이름 (영문)</label>
                        <input type="text" id="frg-nsf-stem" class="frg-input frg-mono" maxlength="100"
                               pattern="[a-z][a-zA-Z0-9]*" placeholder="예: userMgmt" required />
                        <span class="frg-nsf-hint">생성될 파일들의 이름이 됩니다 — 예: userMgmt →
                            userMgmt.jsp, userMgmtList.js … (소문자로 시작, 영문/숫자만)</span>
                    </div>

                    <div class="frg-nsf-field">
                        <span class="frg-label">화면 유형</span>
                        <div class="frg-arch-cards" role="radiogroup" aria-label="화면 유형">
                            <label class="frg-arch-card">
                                <input type="radio" name="frg-nsf-archetype" value="MGMT_LIST_DETAIL" checked />
                                <span class="frg-arch-title">관리 화면</span>
                                <span class="frg-arch-desc">검색 + 목록 + 상세 편집. 가장 일반적인 형태</span>
                            </label>
                            <label class="frg-arch-card">
                                <input type="radio" name="frg-nsf-archetype" value="SIMPLE_LIST" />
                                <span class="frg-arch-title">단순 목록</span>
                                <span class="frg-arch-desc">검색 + 목록만. 상세 영역 없음</span>
                            </label>
                            <label class="frg-arch-card">
                                <input type="radio" name="frg-nsf-archetype" value="DUAL_LAYOUT" />
                                <span class="frg-arch-title">좌우 분할</span>
                                <span class="frg-arch-desc">두 화면을 나란히 배치 (iframe 2단)</span>
                            </label>
                            <label class="frg-arch-card">
                                <input type="radio" name="frg-nsf-archetype" value="POPUP" />
                                <span class="frg-arch-title">팝업</span>
                                <span class="frg-arch-desc">추가·수정 입력 폼과 확인 버튼을 가진 팝업</span>
                            </label>
                            <label class="frg-arch-card">
                                <input type="radio" name="frg-nsf-archetype" value="DASHBOARD" />
                                <span class="frg-arch-title">대시보드</span>
                                <span class="frg-arch-desc">차트·빈 상태·채팅 위젯을 조합하는 화면</span>
                            </label>
                            <%-- P13: FREE_CANVAS — 슬롯 없이 좌표로 배치하는 화면(계약 §17) --%>
                            <label class="frg-arch-card">
                                <input type="radio" name="frg-nsf-archetype" value="FREE_CANVAS" />
                                <span class="frg-arch-title">자유 배치</span>
                                <span class="frg-arch-desc">부품을 원하는 자리에 놓고 크기를 직접 정합니다 (고정폭)</span>
                            </label>
                        </div>
                    </div>

                    <div class="frg-nsf-field">
                        <span class="frg-label">사용 영역</span>
                        <div class="frg-role-pills" role="radiogroup" aria-label="사용 영역">
                            <label class="frg-role-pill">
                                <input type="radio" name="frg-nsf-role" value="admin" checked />
                                <span>관리자 (admin)</span>
                            </label>
                            <label class="frg-role-pill">
                                <input type="radio" name="frg-nsf-role" value="user" />
                                <span>사용자 (user)</span>
                            </label>
                        </div>
                    </div>

                    <label class="frg-nsf-preset">
                        <input type="checkbox" id="frg-nsf-preset" checked />
                        <span><strong>기본 구성으로 시작</strong> — 화면 유형에 맞는 기본 모듈
                            (검색·툴바·목록 등)을 미리 배치해 드립니다. 빈 화면에서 시작하지 않아도 됩니다.</span>
                    </label>

                    <p id="frg-nsf-msg" class="frg-nsf-msg" role="alert" aria-live="polite"></p>
                </div>
                <div class="frg-modal-foot">
                    <button type="submit" class="frg-btn frg-btn-primary">만들기</button>
                </div>
            </div>
        </form>

        <%-- 3-pane 그리드: 좌(팔레트)/중(캔버스)/우(속성) --%>
        <div class="frg-studio-grid">
            <section id="frg-pane-palette" class="frg-pane frg-pane-palette frg-dark" aria-label="팔레트">
                <h2 class="frg-pane-title">팔레트</h2>
                <div class="frg-pane-body frg-empty">모듈 팔레트 (P3-5)</div>
                <%-- 레이어 패널: 배치된 부품의 계층을 드러낸다(§17.8 중첩은 캔버스 그림에만 있었다).
                     layerPanel.js 가 createElement/textContent 로만 채운다(XSS 0). --%>
                <section id="frg-pane-layers" class="frg-layers" aria-label="레이어">
                    <h2 class="frg-pane-title frg-layers-title">레이어</h2>
                    <ul id="frg-layer-list" class="frg-layer-list" aria-label="배치된 부품 계층"></ul>
                </section>
            </section>
            <div id="frg-resize-palette" class="frg-pane-resizer" role="separator" aria-orientation="vertical"
                 aria-label="팔레트 너비 조절" tabindex="0"></div>
            <section id="frg-pane-preview" class="frg-pane frg-pane-preview frg-dark" aria-label="캔버스">
                <h2 class="frg-pane-title">캔버스 <span class="frg-pane-approx">라이브 프리뷰</span></h2>
                <%-- P3-6: 별도 문서를 iframe 으로 배선. DEFINITION_JSON 은 studioApp 이 postMessage 로 주입. --%>
                <iframe id="frg-preview-frame" class="frg-preview-frame"
                        src="${ctx}/admin/studio/preview" title="라이브 프리뷰"></iframe>
                <%-- P7-5: 시작 가이드 오버레이 — 화면 미선택 상태에서 "다음 할 일"을 안내.
                     studioGuide.js 가 createElement/textContent 로만 채운다(XSS 0). --%>
                <div id="frg-canvas-guide" class="frg-canvas-guide" hidden></div>
            </section>
            <div id="frg-resize-props" class="frg-pane-resizer" role="separator" aria-orientation="vertical"
                 aria-label="속성 패널 너비 조절" tabindex="0"></div>
            <section id="frg-pane-props" class="frg-pane frg-pane-props frg-dark" aria-label="속성">
                <h2 class="frg-pane-title">속성</h2>
                <div class="frg-pane-body frg-empty">모듈을 선택하세요 (P3-4)</div>
            </section>
        </div>

        <%-- 생성 결과/이력 패널(P4-5, P7-1: 하단 도킹). studioApp 이 textContent/createElement 로만 채운다(XSS 0). --%>
        <div id="frg-gen-panel" class="frg-gen-panel frg-dark" hidden>
            <div class="frg-gen-head">
                <span class="frg-gen-title">생성 결과</span>
                <span id="frg-gen-status" class="frg-gen-status" role="status" aria-live="polite"></span>
            </div>
            <ul id="frg-gen-files" class="frg-gen-files" aria-label="생성 파일 목록"></ul>
            <div class="frg-gen-hist-head">
                <span class="frg-gen-title">생성 이력</span>
            </div>
            <ul id="frg-gen-history" class="frg-gen-history" aria-label="생성 이력"></ul>
        </div>
    </main>
</body>
</html>
