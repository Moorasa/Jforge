# Golden 스냅샷 (P4-7 생성 엔진 검증)

생성 엔진(FreeMarker 렌더 → 파이프라인 산출)의 **기대 산출물(golden)** 을 커밋해 두고,
테스트가 매 실행마다 현재 산출과 **개행 정규화(CRLF/CR → LF) 후 바이트 비교**한다.

## 디렉터리

| 경로 | 내용 | 대응 테스트 |
|------|------|-------------|
| `golden/mgmtListDetail_tableView/` | MGMT_LIST_DETAIL + listArea TABLE_VIEW 고정 입력의 화면 산출 **7종** | `GoldenSnapshotTest` |
| `golden/mgmtListDetail_cardView/` | (P5-6) MGMT_LIST_DETAIL + listArea CARD_VIEW 고정 입력의 화면 산출 **7종**(shell/list 4종 + CardView 3종) | `ListViewGoldenSnapshotTest` |
| `golden/mgmtListDetail_treeView/` | (P5-6) MGMT_LIST_DETAIL + listArea TREE_VIEW 고정 입력의 화면 산출 **7종**(shell/list 4종 + TreeView 3종) | `ListViewGoldenSnapshotTest` |
| `golden/mgmtListDetail_formView/` | (P5-6) MGMT_LIST_DETAIL + listArea FORM_VIEW 고정 입력의 화면 산출 **7종**(shell/list 4종 + FormView 3종) | `ListViewGoldenSnapshotTest` |
| `golden/injection_mgmtListDetail/`  | props에 악성 페이로드를 심은 입력의 **이스케이프된** 산출 3종(list.jsp / tableView.jsp / tableView.js) | `InjectionGoldenTest` |
| `golden/injection_cardView/`  | (P5-6) CARD_VIEW props에 악성 페이로드를 심은 입력의 **이스케이프된** 산출 2종(cardView.jsp / cardView.js) | `InjectionGoldenTest` |
| `golden/injection_treeView/`  | (P5-6) TREE_VIEW props에 악성 페이로드를 심은 입력의 **이스케이프된** 산출 2종(treeView.jsp / treeView.js) | `InjectionGoldenTest` |
| `golden/injection_formView/`  | (P5-6) FORM_VIEW 폼필드 props(미허용 type 포함)에 악성 페이로드를 심은 입력의 **이스케이프된** 산출 2종(formView.jsp / formView.js) | `InjectionGoldenTest` |
| `golden/mgmtListDetail_detail/` | (P5.5a/b) detailBasic+detailTabs+detailToolbar 배치 화면의 P5.5 신규 표면 **4종**(shell `with-detail` + Detail 3종) | `DetailGoldenSnapshotTest` |
| `golden/dualLayout/`            | (P5-5c) DUAL_LAYOUT(좌우 2단) iframe 패인 화면의 산출 **3종**(`{stem}.jsp/.js/.css`) | `DualLayoutGoldenSnapshotTest` |
| `golden/fullCombination/`       | (P6-4) **풀조합** MGMT(search+listToolbar+TABLE_VIEW+detailToolbar+detailBasic+detailTabs)의 산출 **10종** | `FullCombinationGoldenSnapshotTest` |
| `golden/injection_fullCombination/` | (P6-4) 풀조합 화면의 **전 슬롯·전 문맥 악성 props → 이스케이프된** 산출 10종 | `FullCombinationGoldenSnapshotTest` |
| `golden/freeCanvas_empty/` | (P13-6) FREE_CANVAS 인스턴스 0개(빈 시트) 산출 **3종** | `FreeCanvasGoldenTest` |
| `golden/freeCanvas_controls/` | (P13-6) 원자 컨트롤 3종(BUTTON/LABEL/TEXT_INPUT) 좌표 배치 산출 **3종** | `FreeCanvasGoldenTest` |
| `golden/freeCanvas_nested/` | (P13-6) 3단 중첩 + §17.10 컨테이너 격리 + §17.11 깊이 들여쓰기 산출 **3종** | `FreeCanvasGoldenTest` |
| `golden/freeCanvas_composite/` | (P13-6) 복합 모듈 2종 + 같은 타입 3개(§17.4 3파일 고정) 산출 **3종** | `FreeCanvasGoldenTest` |
| `golden/freeCanvas_injection/` | (P13-6) 캔버스 전 문맥 악성 props → 이스케이프/좌표 0바이트 산출 **3종** | `FreeCanvasGoldenTest` |
| `golden/dashboard_widgets/` | DASHBOARD(BAR_CHART+EMPTY_STATE) 산출 **7종**(shell 1 + 위젯별 3종×2) | `DashboardGoldenTest` |
| `golden/dashboard_injection/` | DASHBOARD 전 문맥 악성 props + 숫자 자리 문자열 산출 **7종** | `DashboardGoldenTest` |

> **DASHBOARD 골든이 왜 늦게 생겼나**: P13-9 에서 숫자가 포매터 객체로 찍히는 결함이
> `module/widgetBase.ftl` 에도 있었는데 *"대시보드 골든이 없어 아무도 못 잡고 있었다"*.
> 그 공백을 메운 것이다. `data-value="82"` 가 회귀 고정점이다.

> **FREE_CANVAS 는 화면당 3종 고정**(`{stem}.jsp/.js/.css`)이다 — 캔버스는 모듈별 파일을
> 만들지 않으므로 같은 모듈을 몇 개 놓아도 산출 파일 수가 변하지 않는다(계약 §17.4).

golden 6종(mgmtListDetail_tableView):
`userMgmt.jsp`(shell), `userMgmtList.jsp`(list), `userMgmtList.js`(listJs),
`userMgmtList.css`(listCss), `userMgmtListTableView.jsp`(module), `userMgmtListTableView.js`(moduleJs).

> **P6-2 공통추출(계약 §12)**: per-screen 뷰/상세/듀얼 CSS(`{stem}ListTableView.css`·`{stem}Detail.css`·
> `{stem}.css`(dual))는 **더 이상 생성하지 않는다** — 고정 셀렉터 규칙을 번들 `commonScreenLayout.css`
> 1벌로 추출(header.jsp가 link). 따라서 골든에서도 제외됐다. `{stem}List.css`(#{stem}-list 스템 스코프)만
> per-screen 유지. 뷰 골든은 shell/list 4종 + 뷰 jsp/js 2종 = **6종**, Detail은 **3종**, DUAL은 **2종**,
> 풀조합은 **8종**.

## 결정성 (재현성)

- 입력은 테스트 클래스 내 **고정 상수**. 타임스탬프·랜덤·절대경로가 산출 본문에 들어가지 않는다.
- 백업 파일(`.bak-{yyyyMMddHHmmss}`)은 **비교 대상에서 제외** — 생성 파일 본문만 비교.
- 비교 전 개행을 LF로 정규화하므로 OS(Windows/Unix) 개행 차이는 실패를 유발하지 않는다.

## golden 안전성 (자동 단언 + 갱신 시 육안 확인)

golden 은 **실제 안전 산출물**이어야 한다. 테스트가 매 실행 자동 단언한다:

- JWORKS **배너 0**, 저작권 배너 0.
- JSP **스크립트릿 0** (`<% `, `<%=`, `<%!` 부재).
- **jQuery 3.7.1** 외 버전 참조 없음(shell 매니페스트 경유, 인라인 다른버전 0).
- JS **네임스페이스 + IIFE(`__defined`)** 존재.
- 인젝션 golden: `<script>`·`onerror`·`${...}` 평가(49/81)·원시 U+2028·JS 내 `</script>`
  **유출 0** (이스케이프된 리터럴 `\x3C\/script>` 등으로만 존재).

## golden 갱신 절차 (⚠ 의도된 템플릿 변경 시에만)

무심코 덮어쓰지 말 것. **템플릿/컨텍스트 변경이 의도된 것**일 때만 아래를 수행하고,
반드시 **diff 리뷰**를 거쳐 커밋한다.

```bash
# 1) 골든 재생성 (mvn clean 금지 — 실행 중 서버 포트 보호)
mvn -Dtest=GoldenSnapshotTest      -Dforge.golden.update=true test  # TABLE_VIEW(P4-7)
mvn -Dtest=ListViewGoldenSnapshotTest -Dforge.golden.update=true test  # CARD/TREE/FORM(P5-6)
mvn -Dtest=InjectionGoldenTest     -Dforge.golden.update=true test  # 인젝션(TABLE + CARD/TREE/FORM)
mvn -Dtest=DetailGoldenSnapshotTest   -Dforge.golden.update=true test  # Detail 슬롯(P5.5a/b)
mvn -Dtest=DualLayoutGoldenSnapshotTest -Dforge.golden.update=true test  # DUAL_LAYOUT(P5-5c)
mvn -Dtest=FullCombinationGoldenSnapshotTest -Dforge.golden.update=true test  # 풀조합+인젝션(P6-4)
mvn -Dtest=FreeCanvasGoldenTest    -Dforge.golden.update=true test  # FREE_CANVAS 5종(P13-6)
mvn -Dtest=DashboardGoldenTest     -Dforge.golden.update=true test  # DASHBOARD 2종

# 2) 변경 검토: 배너/스크립트릿/이스케이프 회귀가 없는지 눈으로 확인
git diff src/test/resources/golden/

# 3) 검토 통과 시에만 커밋
git add src/test/resources/golden/ && git commit
```

갱신 모드(`-Dforge.golden.update=true`)에서는 비교를 건너뛰고 산출을 소스 트리
(`src/test/resources/golden/...`)에 LF 정규화하여 기록만 한다.
