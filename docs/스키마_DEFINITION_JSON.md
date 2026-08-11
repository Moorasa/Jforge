# DEFINITION_JSON 문서모델 계약 (P3-2)

> `TB_FRG_SCREEN.DEFINITION_JSON` 컬럼(JSONB)에 저장되는 **화면 1개의 설계 원본(source of truth)** 계약 문서.
> 기획서_v0.1.md 5장 "설계는 문서모델(DEFINITION_JSON)로 저장(유연)"의 정규 스키마다.
> PROP_SCHEMA_JSON(모듈이 **어떤 속성을 갖는가**)과 달리, DEFINITION_JSON은 **이 화면이 어떤 모듈 인스턴스를 어느 슬롯에 배치했고 각 인스턴스의 props 값이 무엇인가**를 담는다.

## 0. 공유 계약 범위 (Shared Contract)

이 문서가 정의하는 스키마는 아래 산출물이 **공유하는 단일 계약**이다. P3-2를 다른 P3 태스크보다 **먼저 고정**한 이유이며, 규약 변경 시 아래 소비자를 함께 검토한다.

| 소비자 | 사용 방식 |
|---|---|
| **P3-1 저장 컬럼 처리** | CREATE/PUT 시 이 스키마 형태의 JSON을 `DEFINITION_JSON`(JSONB)에 **무가공 저장**(바이트 동등 왕복). |
| **P3-3 스튜디오 셸** | 화면 단건 로드 시 이 JSON을 편집중 상태로 보관, 오케스트레이션 허브가 하위 컨트롤러에 배포. |
| **P3-4 속성패널** | 선택 인스턴스의 `props`를 해당 `moduleTypeCode`의 PROP_SCHEMA_JSON 폼으로 편집 → `props` 갱신. |
| **P3-5 저장(직렬화)** | 편집 상태를 이 스키마로 직렬화해 P3-1 API로 전송. |
| **P3-5b 서버 검증** | §5 신뢰경계의 **구조 방어**(slotKey/moduleTypeCode/instanceId 화이트리스트)를 저장 시점에 강제. |
| **P3-6 라이브 프리뷰** | `slots` 순서대로 인스턴스를 번들 런타임에 전달해 미리보기(§5 표시문자열 이스케이프 책임). |
| **P4 생성기(FreeMarker)** | 동일 문서를 `TemplateContext`로 받아 "1 화면 = N개의 3종 세트 + include 배선"(기획서 4장) 산출. |

이 문서의 §6 예시 JSON은 그대로 저장 API에 투입 가능한 **유효 DEFINITION_JSON**이어야 한다.

---

## 1. 최상위 스키마 계약

```
{
  schemaVersion: number,
  archetype:     string,   // ARCHETYPE 공통코드 (MGMT_LIST_DETAIL / SIMPLE_LIST / DUAL_LAYOUT)
  stem:          string,   // 파일 접두(stem). TB_FRG_SCREEN.STEM 과 동일 값
  role:          string,   // ROLE 공통코드 (admin / user)
  slots: {
    <slotKey>: [ Instance, ... ]
  }
}
```

### 1.1 최상위 필수키

모든 DEFINITION_JSON은 아래 5개 키를 **반드시** 포함한다.

| 키 | 타입 | 설명 |
|---|---|---|
| `schemaVersion` | number | 문서모델 버전. 현재 **`1`**. forward-compat 규칙은 §4. |
| `archetype` | string | 화면 아키타입. `TB_FRG_COMMON_CODE(GRP_CODE='ARCHETYPE')` 실존 코드. **슬롯 화이트리스트를 결정**(§2). |
| `stem` | string | 파일 접두. `^[a-z][a-zA-Z0-9]*$` 화이트리스트 검증(파일명·식별자로 쓰이므로). `TB_FRG_SCREEN.STEM`과 일치. |
| `role` | string | `admin` / `user` (`GRP_CODE='ROLE'`). 생성 경로(admin/user)·`window.JWorks_JS{Domain}{Role}` 네임스페이스 결정. |
| `slots` | object | 슬롯키 → 인스턴스 배열 맵. 키 집합은 `archetype`의 슬롯 화이트리스트(§2)로 제한. |

> `archetype`/`role`/`stem`은 코드로 평가되지 않으며, 화이트리스트(공통코드 실존 또는 정규식)를 통과한 값만 허용한다(템플릿 인젝션 방지). PROP_SCHEMA §1.1의 `key` 규칙과 동일 원칙.

### 1.2 Instance 구조

`slots.<slotKey>` 배열의 각 원소(모듈 인스턴스)는 아래 3개 키를 **반드시** 포함한다.

| 키 | 타입 | 설명 |
|---|---|---|
| `instanceId` | string | 화면 내 유일 식별자. **클라 생성**. 형식·유일성 규칙은 §1.3. |
| `moduleTypeCode` | string | `TB_FRG_MODULE_TYPE.MODULE_TYPE_CODE` 실존 코드(예: `TABLE_VIEW`). 이 코드가 `props`의 스키마와 슬롯 배치 가능 카테고리를 결정(§3). |
| `props` | object | 이 인스턴스의 속성 값. **키·형태는 `moduleTypeCode`의 PROP_SCHEMA_JSON을 따른다**(§3). |
| `data` | object (선택) | 모듈 공통 데이터 바인딩 선언. `endpoint`(같은 도메인의 `/...` 경로, ≤500자), `method`(`GET`/`POST`), 선택 `resultPath`(≤200자), `autoLoad`(boolean)만 허용. 서버 바인딩(계약 §14)을 쓰면 선택 `table`/`keyColumn`(DB 식별자 `^[A-Za-z][A-Za-z0-9_]{0,62}$`)을 함께 둔다. `props`와 분리한다. |
| `events` | array (선택) | 모듈 공통 이벤트 선언(최대 20개). 각 원소는 `event`(`click`/`change`/`select`), `action`(`reload`/`openDetail`/`submit`/`custom`), 선택 `target`(≤200자)이다. |

- **배열 순서 = 렌더 순서.** 같은 슬롯에 여러 인스턴스가 있으면 배열 인덱스 오름차순으로 렌더/생성한다.
- 배치된 모듈이 없는 슬롯은 **빈 배열 `[]`** 로 두거나 키를 생략할 수 있다(소비자는 둘을 동치로 취급).
- `data`/`events`는 화면 조립 구조나 모듈 고유 `props`를 대체하지 않는 **선언형 공통 메타**다. 생성된 `{stem}Design.js`는 같은 도메인의 API를 조회하고, `TABLE_VIEW` 응답 배열은 기본 행 렌더링까지 수행한다. `reload`는 해당 모듈(또는 `target` 인스턴스)을 다시 조회하며, `openDetail`/`submit`/`custom`은 `frg:design:<action>` DOM 이벤트로 업무 화면에 전달한다.

### 1.3 instanceId 생성 규칙

- **생성 주체**: 클라이언트(P3-3 스튜디오). 모듈을 슬롯에 배치하는 순간 부여한다. 서버는 값을 **재발급하지 않고** 보존한다.
- **형식(택1, 둘 다 화이트리스트 통과)**:
  - `<moduleTypeCode 소문자 camel>_<seq>` 형태 — 예: `tableView_1`, `searchFilterBar_1`, `toolbar_2`.
  - 또는 UUIDv4 — 예: `9f1c2d34-...` (하이픈 포함 형태).
- **형태 검증 정규식**: `^[a-zA-Z][a-zA-Z0-9_-]{0,63}$` (영문 시작, 영숫자·`_`·`-`만, 64자 이내). 서버는 이 정규식으로 형태만 검증한다(§5).
  > 이 정규식은 **형태만 보장**하며 언어 예약어(`true`/`false`/`null`/`class`/`function` 등) 충돌까지는 차단하지 않는다(PROP_SCHEMA §2.1의 `key` 규칙과 동일 한계). `instanceId`가 P4에서 JS 변수명·DOM id·파일명 일부로 재사용될 경우에도 **문자열 조립으로 평가·삽입하지 말고 항상 맵 키 조회(`slots[key].find(i => i.instanceId === id)` 등)로만 사용**해야 한다(§5.1).
- **유일성**: 하나의 DEFINITION_JSON **문서 전체(모든 슬롯 통틀어)에서 유일**해야 한다. 슬롯 간 중복도 금지. P3-4가 인스턴스를 이 값으로 선택·조회하므로 충돌 시 편집이 깨진다.
- **불변성**: 한 번 부여된 `instanceId`는 편집 세션·저장 왕복 전반에서 유지한다(속성 변경으로 재발급 금지).

---

## 2. 아키타입별 슬롯 화이트리스트

슬롯 모델의 대전제(기획서 0장 2번 원칙): **자유 좌표 캔버스가 아니라, 아키타입이 정의하는 정해진 슬롯 집합에만 모듈을 배치**한다. `slots`에 등장할 수 있는 `slotKey`는 `archetype`별로 아래 표에 **고정**되며, 그 밖의 키는 §5 구조 방어에서 거부하거나(서버) §4에 따라 스킵한다(소비자).

### 2.1 MGMT_LIST_DETAIL — 관리화면(목록+상세)

기획서 4장 화면 조립 계약(shell → List → List{View} + Detail(basic-info/associate-info))에 대응한다.

| slotKey | 대응 조립 영역(기획서 4장) | 허용 카테고리 | cardinality |
|---|---|---|---|
| `searchArea` | `{stem}List.jsp` 상단 검색/필터 바 | FILTER | 0..1 (단일) |
| `listArea` | `{stem}List{View}.jsp` 목록 본문(뷰 세트) | VIEW | 1..1 (필수·단일) |
| `listToolbar` | 목록 상단 액션 버튼군(추가/삭제/엑셀 등) | ACTION | 0..1 (단일) |
| `detailBasic` | Detail 영역 basic-info(view/edit 필드) | DETAIL, VIEW | 0..1 (단일) |
| `detailTabs` | Detail 영역 associate-info(탭→iframe) | DETAIL, VIEW | 0..N (다중) |
| `detailToolbar` | 상세 영역 액션 버튼군(저장/수정 등) | ACTION | 0..1 (단일) |

> `listArea`는 아키타입의 정체성(목록)이므로 **최소 1개 필수**. 나머지 슬롯은 선택. `detailTabs`만 다중 배치를 허용(탭 여러 개).

### 2.2 SIMPLE_LIST — 단순 목록

| slotKey | 대응 조립 영역 | 허용 카테고리 | cardinality |
|---|---|---|---|
| `searchArea` | 목록 상단 검색/필터 바 | FILTER | 0..1 (단일) |
| `listArea` | 목록 본문(뷰 세트) | VIEW | 1..1 (필수·단일) |
| `listToolbar` | 목록 상단 액션 버튼군 | ACTION | 0..1 (단일) |

> `SIMPLE_LIST`는 상세(Detail) 슬롯을 갖지 않는다. `detailBasic`/`detailTabs`/`detailToolbar` 등장 시 §5 구조 방어에서 거부.

### 2.3 DUAL_LAYOUT — 좌우 2단 (P5 확장 예약)

좌우 resizer iframe 2단(기획서 2장) 골격만 예약한다. 각 영역 내부의 세부 슬롯화·오케스트레이션 배선은 **P5 확장 시 상세화**한다.

| slotKey | 대응 조립 영역 | 허용 카테고리 | cardinality |
|---|---|---|---|
| `leftArea` | 좌측 pane(iframe) | VIEW, FILTER, DETAIL | 0..N (다중) |
| `rightArea` | 우측 pane(iframe) | VIEW, FILTER, DETAIL | 0..N (다중) |

> 예약 상태: DUAL_LAYOUT을 실제로 생성하는 첫 화면을 만들 때 슬롯 세분화·cardinality를 재확정한다. 그전까지 이 두 슬롯만 유효.

### 2.4 슬롯별 허용 모듈 카테고리 규칙

- **카테고리 출처**: `TB_FRG_MODULE_TYPE.CATEGORY_CODE` (V2 시드 `GRP_CODE='MODULE_CATEGORY'`: `VIEW`/`FILTER`/`ACTION`/`DETAIL`/`WIDGET`).
- 인스턴스를 슬롯에 배치할 때, 그 `moduleTypeCode`의 `CATEGORY_CODE`가 해당 슬롯의 **허용 카테고리 집합에 속해야** 한다. 아니면 배치 거부(클라 검증) 및 §5 구조 방어(서버).
- `WIDGET` 카테고리(alert/snackbar/pagination 등)는 슬롯에 **명시 배치하지 않는다**. 번들 런타임이 자동 포함하므로(기획서 2장 "Widgets(자동)/Pagination(자동)") 어떤 슬롯 화이트리스트에도 넣지 않았다.
- **현재 3모듈 배치 가능표**(V3 시드 기준):

  | moduleTypeCode | CATEGORY_CODE | 배치 가능 슬롯 |
  |---|---|---|
  | `TABLE_VIEW` | VIEW | `listArea`, `detailBasic`, (DUAL) `leftArea`/`rightArea` |
  | `SEARCH_FILTER_BAR` | FILTER | `searchArea`, (DUAL) `leftArea`/`rightArea` |
  | `TOOLBAR` | ACTION | `listToolbar`, `detailToolbar` |

### 2.5 cardinality 표기

- `0..1` 단일: 없거나 1개. 배열 길이 ≤ 1.
- `1..1` 필수·단일: 정확히 1개. **저장 시 0개 또는 2개 이상이면 P3-5b는 400으로 거부한다(하드 실패, 확정)**. 근거: `listArea`는 아키타입의 정체성(§2.1 "최소 1개 필수")이므로 이를 어긴 문서는 화면으로서 불완전하며, 조용히 통과시키면 P3-6/P4에서 빈 목록이거나 중복 렌더라는 더 발견하기 어려운 실패로 번진다. `0..1`/`0..N` 슬롯의 미충족(0개)은 정상(경고 없음).
- `0..N` / `1..N` 다중: 여러 개 허용, 배열 순서 = 렌더 순서(§1.2).

---

## 3. props ↔ PROP_SCHEMA_JSON 연결 규칙

`props`는 **자유 오브젝트가 아니라**, 해당 인스턴스 `moduleTypeCode`의 PROP_SCHEMA_JSON(스키마_PROP_SCHEMA.md)에 종속된다.

1. **키 제약**: `props`의 키는 그 모듈 PROP_SCHEMA_JSON `fields[].key`에 **정의된 키만** 가질 수 있다. 스키마에 없는 키는 P3-4가 만들지 않으며, 소비자는 무시(경고)한다.
2. **초기값**: 인스턴스를 슬롯에 처음 배치할 때 `props`의 각 값은 해당 필드의 **`default`로 초기화**한다. 예: `TABLE_VIEW` 신규 배치 → `props.selectMode = "none"`, `props.pagingYn = true`, `props.columns = []` (각 `fields[].default` 복사).
3. **값 형태**: 각 값의 형태는 필드 `type`을 따른다(PROP_SCHEMA §1.2 — `text`→string, `boolean`→boolean, `select`→options의 한 value, `columns`→행 오브젝트 배열, `chips`→string[]).
4. **필수 필드**: `required: true` 필드는 `props`에 값이 존재해야 한다(저장 시 P3-5b 검증). `required: false`는 생략 가능하며, 생략 시 소비자가 `default`를 적용한다.
5. **미지원 필드 처리**: `props`에 스키마에 없는 잉여 키가 있으면 **해당 키만 무시**(하드 실패 아님, PROP_SCHEMA §1.3과 동형의 forward-compat).

> 두 스키마의 역할 분리: PROP_SCHEMA_JSON = **필드 정의(어떤 속성이 있는가)**, DEFINITION_JSON `props` = **그 필드에 채워진 값**. `key`는 두 스키마를 잇는 유일한 연결 고리이며, §5에 따라 **코드로 평가하지 않고 맵 조회로만** 사용한다.

---

## 4. schemaVersion 과 forward-compat 규칙

- `schemaVersion`은 현재 **`1`**. 구조 파괴적 변경(키 의미 변경/필수키 추가) 시에만 증가시킨다. 슬롯·모듈 추가는 데이터 확장이므로 버전을 올리지 않는다.
- **미지원 slotKey**: 소비자(프리뷰/생성기)가 자신이 모르는 `slotKey`를 만나면 **해당 슬롯만 스킵**하고 `console.warn`(또는 서버 로그)만 남긴다. 문서 전체를 실패 처리하지 않는다.
  예: `console.warn("[DEFINITION] unsupported slotKey '" + key + "' for archetype '" + archetype + "', skipped")`
- **미지원 moduleTypeCode**: 소비자가 `TB_FRG_MODULE_TYPE`에 없는(또는 자신의 카탈로그에 없는) `moduleTypeCode` 인스턴스를 만나면 **해당 인스턴스만 스킵**하고 경고만 남긴다.
  예: `console.warn("[DEFINITION] unknown moduleTypeCode '" + code + "' (instanceId=" + id + "), skipped")`
- **미지원 props 키**: §3.5 — 해당 키만 무시.
- 이 규칙으로 신규 슬롯/모듈이 선반영된 문서를 구버전 소비자가 만나도 **부분 렌더로 안전하게 동작**한다.

> 단, **저장 시점 구조 방어**(§5)는 forward-compat과 별개다. 서버는 "알 수 없으니 스킵"이 아니라 "화이트리스트 밖이면 거부"로 동작한다. 스킵은 **읽기 소비자**의 관용성, 거부는 **쓰기 경계**의 엄격성.

---

## 5. 🔒 신뢰경계 (Trust Boundary) — 반드시 준수

DEFINITION_JSON도 JSONB이므로 저장 시점에 **유효 JSON**은 강제된다. 그러나 유효 JSON이라는 사실이 "이 값을 그대로 DOM/템플릿에 꽂아도 안전하다"는 뜻은 **아니다**. 이 스키마는 **구조(`slotKey`, `moduleTypeCode`, `instanceId`, `props`의 키)만 방어**하고, **표시 문자열(`props`에 채워진 사용자 입력값: `label`/`displayName`/`styleClass`/컬럼값 등)은 방어하지 않는다.** 이스케이프는 항상 마지막 소비자의 책임이다. (스키마_PROP_SCHEMA.md §2.1과 동일 원칙.)

### 5.1 방어 책임 분배표

| 대상 | 성격 | 방어 지점 (누가) | 방어 방법 / 소비자 책임 |
|---|---|---|---|
| `slotKey` | 구조 키 | **서버(P3-5b, 저장 시)** | `archetype`의 슬롯 화이트리스트(§2)에 **속한 키만 허용**. 밖이면 저장 거부(읽기 소비자는 §4대로 스킵). |
| `moduleTypeCode` | 구조 코드 | **서버(P3-5b, 저장 시)** | `TB_FRG_MODULE_TYPE(USE_YN='Y')`에 **실존하는 코드만 허용**하는 화이트리스트 검증. + 그 코드의 `CATEGORY_CODE`가 배치 슬롯의 허용 카테고리(§2.4)에 속하는지 검증. |
| `instanceId` | 구조 식별자 | **서버(P3-5b, 저장 시)** | §1.3 정규식(`^[a-zA-Z][a-zA-Z0-9_-]{0,63}$`) **형태 검증** + 문서 내 유일성 검증. **코드로 평가·삽입 금지**(맵 키로만 사용). |
| `archetype`/`role`/`stem` | 구조 값 | **서버(P3-5b, 저장 시)** | `archetype`/`role`은 공통코드 실존 화이트리스트, `stem`은 `^[a-z][a-zA-Z0-9]*$`. `stem`은 파일명이 되므로 경로 문자(`/`,`.`,`\`,`..`) 차단 — 정규식이 영숫자만 허용하므로 구조적으로 배제됨. **이중 경계**: 이 정규식은 DEFINITION_JSON 저장 시점(1차, 데이터 방어)이고, P4 생성기가 실제로 파일을 쓸 때는 [PathSafetyService](../src/main/java/com/jworks/forge/gen/safety/PathSafetyService.java)(P1-1)의 `resolveSafeWritePath`가 **최종 방어선**(2차, canonical 정규화+루트하위 검증)이다. 이 정규식 통과가 경로안전을 대체하지 않는다 — P4는 반드시 PathSafetyService를 경유해야 한다. |
| `props`의 **키** | 구조 식별자 | 소비자(P3-4) 생성 + 서버 관용 | P3-4가 PROP_SCHEMA `fields[].key`(이미 `^[a-z][a-zA-Z0-9]*$` 검증됨)만으로 구성. 잉여 키는 §3.5로 무시. **key를 템플릿 문자열로 조립하지 말고 `context.get(key)` 맵 조회로만 사용**(PROP_SCHEMA §2.1 재사용). |
| `props`의 **값** (표시 문자열: `label`, `displayName`, 컬럼 반복행 값 등) | 자유 문자열 | **없음** (서버가 이스케이프하지 않음) | **P3-6 프리뷰**: DOM 삽입 시 `innerHTML` 금지, `textContent`/`.text()`만 사용. **P4 생성기**: 산출 JSP/JS/CSS에 삽입 시 대상 문법(HTML/JS 문자열/CSS)에 맞는 이스케이프. |
| `props`의 `styleClass`류 값 | 자유 문자열 (CSS 토큰) | 권고: 소비자 토큰 검증 | CSS 클래스 화이트리스트/토큰 검증 권고 — 공백·`<`/`>`/`"` 배제(PROP_SCHEMA §2.1과 **동일 문구 재사용**). 프리뷰는 `className`/`classList`로만 주입, 문자열 연결로 태그 조립 금지. |

### 5.2 경계 요약

- **쓰기 경계(서버, 엄격)**: `slotKey`/`moduleTypeCode`/`instanceId`/`archetype`/`role`/`stem` = **화이트리스트 밖이면 거부**. 이것이 구조 방어의 전부다.
- **읽기 경계(소비자, 관용+이스케이프)**: 모르는 구조는 §4대로 **스킵**, 표시 문자열은 **각 렌더 맥락에서 이스케이프**.
- 서버는 `props`의 **값을 절대 가공·이스케이프하지 않는다**(P3-1 바이트 동등 왕복 계약 보존). 값의 안전화는 100% 소비자 몫이다.

---

## 6. 예시: MGMT_LIST_DETAIL (TableView + SearchFilterBar + Toolbar)

아래는 관리화면 아키타입에 검색 필터 바 + 툴바 + 테이블 뷰를 조립한 **유효 DEFINITION_JSON 전체 예시** 1건이다. 각 인스턴스의 `props` 키는 §3에 따라 해당 모듈 PROP_SCHEMA_JSON(§V3 시드)의 `fields[].key`만 사용했다.

```json
{
  "schemaVersion": 1,
  "archetype": "MGMT_LIST_DETAIL",
  "stem": "userMgmt",
  "role": "admin",
  "slots": {
    "searchArea": [
      {
        "instanceId": "searchFilterBar_1",
        "moduleTypeCode": "SEARCH_FILTER_BAR",
        "props": {
          "filters": [
            { "name": "useYn", "label": "사용여부", "options": "Y:사용,N:미사용" }
          ],
          "keywordYn": true,
          "dateRangeYn": false
        }
      }
    ],
    "listToolbar": [
      {
        "instanceId": "toolbar_1",
        "moduleTypeCode": "TOOLBAR",
        "props": {
          "buttons": [
            { "actionCode": "add",    "label": "추가", "styleClass": "btn-primary" },
            { "actionCode": "delete", "label": "삭제", "styleClass": "btn-secondary" }
          ]
        }
      }
    ],
    "listArea": [
      {
        "instanceId": "tableView_1",
        "moduleTypeCode": "TABLE_VIEW",
        "props": {
          "columns": [
            { "name": "userId",   "displayName": "사용자ID", "displayYn": true,  "sortYn": true },
            { "name": "userName", "displayName": "이름",     "displayYn": true,  "sortYn": true },
            { "name": "regDtm",   "displayName": "등록일시", "displayYn": true,  "sortYn": false }
          ],
          "selectMode": "checkbox",
          "pagingYn": true,
          "excelYn": true,
          "csvYn": false
        }
      }
    ],
    "detailBasic": [],
    "detailTabs": []
  }
}
```

### 6.1 예시 자체 점검

- [x] 최상위 필수키 5종(`schemaVersion`/`archetype`/`stem`/`role`/`slots`) 보유.
- [x] 모든 `slotKey`(`searchArea`/`listToolbar`/`listArea`/`detailBasic`/`detailTabs`)가 `MGMT_LIST_DETAIL` 화이트리스트(§2.1) 소속.
- [x] 각 슬롯의 모듈 카테고리 준수: `searchArea`←FILTER(SEARCH_FILTER_BAR), `listToolbar`←ACTION(TOOLBAR), `listArea`←VIEW(TABLE_VIEW) (§2.4).
- [x] 필수·단일 슬롯 `listArea`에 정확히 1개(§2.5 `1..1`).
- [x] 모든 인스턴스가 `instanceId`/`moduleTypeCode`/`props` 3키 보유, `instanceId`는 §1.3 형식·정규식 통과·문서 내 유일.
- [x] 각 `props` 키가 해당 모듈 PROP_SCHEMA_JSON `fields[].key`에만 소속(§3): SEARCH_FILTER_BAR→`filters`/`keywordYn`/`dateRangeYn`, TOOLBAR→`buttons`, TABLE_VIEW→`columns`/`selectMode`/`pagingYn`/`excelYn`/`csvYn`.
- [x] 반복행(`columns`/`filters`/`buttons`) 값이 각 필드 하위 `columns[].key`(name/displayName/... 등) 형태 준수.
- [x] 표시 문자열(`displayName`/`label`/`styleClass`)은 이스케이프하지 않음 — §5대로 소비자 책임.

---

## 7. PROP_SCHEMA_JSON ↔ DEFINITION_JSON 역할 매핑표 (자체 점검)

| 관점 | PROP_SCHEMA_JSON (P2-2) | DEFINITION_JSON (P3-2) |
|---|---|---|
| 저장 위치 | `TB_FRG_MODULE_TYPE.PROP_SCHEMA_JSON` | `TB_FRG_SCREEN.DEFINITION_JSON` |
| 단위 | 모듈 타입 1개 | 화면 1개 |
| 무엇을 담나 | 필드 **정의**(key/label/type/default) | 인스턴스 배치 + `props` **값** |
| 최상위 | `{ title, fields[] }` | `{ schemaVersion, archetype, stem, role, slots{} }` |
| 연결 고리 | `fields[].key`, `fields[].default` | `props.<key>` (§3) |
| 구조 방어 | `key` 화이트리스트(§1.1) | `slotKey`/`moduleTypeCode`/`instanceId`/`archetype`/`role`/`stem` 화이트리스트(§5) |
| 표시 문자열 방어 | 없음 — 소비자 책임(§2.1) | 없음 — 소비자 책임(§5) |
| forward-compat | 미지원 `type` 스킵(§1.3) | 미지원 `slotKey`/`moduleTypeCode`/props키 스킵(§4) |
