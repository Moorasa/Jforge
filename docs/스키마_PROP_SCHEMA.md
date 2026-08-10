# PROP_SCHEMA_JSON 스키마 규약 (P2-2)

> `TB_FRG_MODULE_TYPE.PROP_SCHEMA_JSON` 컬럼에 저장되는 **속성패널 선언 스키마**의 계약 문서.
> 기획서_v0.1.md 2장 "주요 속성"을 폼 렌더 가능한 형태로 정규화한 것이다.

## 0. 공유 계약 범위 (Shared Contract)

이 문서가 정의하는 스키마는 아래 3개 산출물이 **공유하는 단일 계약**이다. 규약 변경 시 세 곳을 함께 검토한다.

| 소비자 | 사용 방식 |
|---|---|
| **P2-5 폼렌더 UI** | `PROP_SCHEMA_JSON`을 읽어 우측 속성패널 폼을 동적 렌더. 이 스키마가 **입력 계약**. |
| **P3 라이브 프리뷰** | 폼에서 채워진 값(`DEFINITION_JSON`의 모듈 인스턴스 props)을 번들 런타임에 전달해 미리보기. |
| **P4 생성기(FreeMarker)** | 동일 props를 `TemplateContext`로 받아 JSP+JS+CSS 3종을 렌더. |

또한 이 문서의 3모듈 초안은 **P2-1(V3 DDL 시드)**의 `PROP_SCHEMA_JSON` 컬럼 값의 원본이다. 아래 JSON은 그대로 시드에 투입 가능한 유효 JSON이어야 한다.

---

## 1. 최상위 스키마 계약

```
{ title: string, fields: Field[] }
```

- `title` (string, 필수): 속성패널 상단에 표시할 모듈 표제(예: "테이블 뷰").
- `fields` (Field[], 필수): 이 모듈이 노출하는 속성 필드 목록. 순서대로 렌더한다.

### 1.1 Field 필수키

모든 `Field`는 아래 5개 키를 **반드시** 포함한다.

| 키 | 타입 | 설명 |
|---|---|---|
| `key` | string | 속성 식별자. **camelCase**. `DEFINITION_JSON` props의 키가 된다. 식별자 화이트리스트(`^[a-z][a-zA-Z0-9]*$`)로 검증. |
| `label` | string | 한글 라벨(폼 필드 표시명). |
| `type` | string | 아래 §2 허용 집합 중 하나. |
| `required` | boolean | 필수 입력 여부. |
| `default` | any | 초기값. 타입별 형태를 따른다(§2 참조). |

> `key`는 코드로 평가되지 않으며, 화이트리스트 정규식을 통과한 식별자만 허용한다(템플릿 인젝션 방지).

### 1.2 type별 추가 하위 정의

| `type` | 추가 필수 하위키 | `default` 형태 |
|---|---|---|
| `text` | — | string |
| `number` | — | number |
| `boolean` | — | boolean (체크박스) |
| `select` | `options: [{ value, label }]` | string (options의 한 value) |
| `columns` | `columns: [{ key, label, type }]` | array of row objects |
| `chips` | — | string[] |

- `select.options[]`: 각 원소는 `{ value: string, label: string }`.
- `columns.columns[]`: **반복 행 그리드**의 각 행이 가지는 컬럼(셀) 정의. 각 원소는 `{ key: string(camelCase), label: string, type: string }`. 여기서 셀 `type`은 `text` / `number` / `boolean` / `select` 등 단순 타입만 허용(중첩 `columns` 금지).
- `chips`: 문자열 배열. UI에서 태그(chip) 입력으로 렌더.

### 1.3 미지원 type 처리 규칙

- 폼렌더러는 §2 허용 집합에 없는 `type`을 만나면 **해당 필드만 스킵**한다.
- **에러를 던지지 않는다.** `console.warn`으로 경고만 남긴다.
  예: `console.warn("[PROP_SCHEMA] unsupported field type '" + type + "' (key=" + key + "), skipped")`
- 이로써 스키마에 신규 type이 선반영되어도 구버전 폼렌더가 안전하게 동작한다(forward-compat).

---

## 2. 허용 type 집합 (P2 확정)

`text`, `number`, `boolean`, `select`, `columns`, `chips` — 이 6종만 유효. 그 외는 §1.3에 따라 스킵.

> **미사용 고지**: 3모듈 초안(§3)에서는 `chips`를 사용하지 않는다. 허용 type이지만 아직 실검증 경로가 없는 **예약** 상태다. `chips`를 실제로 쓰는 첫 모듈 스키마를 추가할 때 폼렌더(P2-5)·생성기(P4) 경로를 함께 검증할 것.

---

## 2.1 신뢰경계 (Trust Boundary) — 반드시 준수

이 스키마는 **구조(`key`, `type`)만 방어**하고, **표시 문자열(`label`, `title`, `select.options[].label`, `columns.columns[].label`, 인스턴스 값으로 채워지는 `styleClass` 등)은 방어하지 않는다.** 소비자가 각자의 렌더 맥락에서 이스케이프할 책임을 진다.

| 필드 성격 | 방어 지점 | 소비자 책임 |
|---|---|---|
| `key` (구조 식별자) | 이 문서 §1.1 화이트리스트(`^[a-z][a-zA-Z0-9]*$`) | **템플릿 조립 금지.** P4는 `key`를 FreeMarker 문자열 인터폴레이션으로 조립하지 말고, 항상 `context.get(key)` 같은 **맵 조회**로만 사용한다. 정규식은 형태만 보장하며 `true`/`false`/`null` 등 언어 예약어 충돌까지는 차단하지 않으므로, `key`를 식별자로 직접 평가·삽입하는 경로를 만들지 않는 것이 유일한 안전장치다. |
| `label`/`title`/`options[].label`/`columns[].label` 등 표시 문자열 (스키마 정의값) | 없음(자유 문자열) | **P2-5(폼렌더)**: DOM 삽입 시 `innerHTML` 금지, `textContent`/`.text()`만 사용(작업분해_P2.md P2-5 AC와 동일 기준). **P4(생성기)**: 산출 JSP/JS/CSS에 삽입 시 대상 문법(HTML/JS 문자열/CSS)에 맞는 이스케이프를 거친다. |
| 사용자가 입력한 인스턴스 값(`DEFINITION_JSON`의 props, 예: `styleClass`, `columns` 반복행 값) | 없음(자유 문자열) | 위와 동일. 특히 `styleClass`류는 CSS 클래스 화이트리스트/토큰 검증(공백·`<`/`>`/`"` 등 배제) 권고. |

> 즉 "스키마가 유효 JSON이며 화이트리스트를 통과했다"는 것이 "이 값을 그대로 DOM/템플릿에 꽂아도 안전하다"는 뜻이 아니다. 이스케이프는 항상 마지막 소비자의 책임이다.

---

## 3. 3모듈 PROP_SCHEMA_JSON 초안

> 근거: 저장소 반입 런타임 `src/main/resources/static/js/admin/common/commonListTableView.js`에서
> 실제 속성명을 확인함 — 컬럼 렌더는 `item.name`/`item.displayName`/`item.displayYn`/`item.sortYn`,
> 선택 모드는 `selectionType`('checkbox'|'radio', 기본 'checkbox'),
> 필터는 `getCurrentSearchData()`가 `.filter` 하위 SELECT(`data-filter-name`/`name`)와
> `.filter-datepicker`(start/end) 를 수집. 스키마 `key`는 이 런타임 계약에 맞춰 명명함.

### 3.1 TABLE_VIEW

```json
{
  "title": "테이블 뷰",
  "fields": [
    {
      "key": "columns",
      "label": "컬럼 목록",
      "type": "columns",
      "required": true,
      "default": [],
      "columns": [
        { "key": "name", "label": "필드명", "type": "text" },
        { "key": "displayName", "label": "표시명", "type": "text" },
        { "key": "displayYn", "label": "표시", "type": "boolean" },
        { "key": "sortYn", "label": "정렬", "type": "boolean" }
      ]
    },
    {
      "key": "selectMode",
      "label": "행 선택 방식",
      "type": "select",
      "required": true,
      "default": "none",
      "options": [
        { "value": "none", "label": "선택 없음" },
        { "value": "checkbox", "label": "체크박스(다중)" },
        { "value": "radio", "label": "라디오(단일)" }
      ]
    },
    { "key": "pagingYn", "label": "페이징 사용", "type": "boolean", "required": false, "default": true },
    { "key": "excelYn",  "label": "엑셀 내보내기", "type": "boolean", "required": false, "default": false },
    { "key": "csvYn",    "label": "CSV 내보내기",  "type": "boolean", "required": false, "default": false }
  ]
}
```

### 3.2 SEARCH_FILTER_BAR

```json
{
  "title": "검색 필터 바",
  "fields": [
    {
      "key": "filters",
      "label": "필터 목록",
      "type": "columns",
      "required": false,
      "default": [],
      "columns": [
        { "key": "name", "label": "필드명", "type": "text" },
        { "key": "label", "label": "라벨", "type": "text" },
        { "key": "options", "label": "선택 옵션(value:label, 콤마구분)", "type": "text" }
      ]
    },
    { "key": "keywordYn",   "label": "키워드 검색 사용", "type": "boolean", "required": false, "default": true },
    { "key": "dateRangeYn", "label": "날짜범위(datepicker) 사용", "type": "boolean", "required": false, "default": false }
  ]
}
```

> 참고: `filters` 행의 `options`는 반복행 셀에서 중첩 `select`를 만들지 않기 위해 `text`(예: `"Y:사용,N:미사용"`)로 받고,
> 폼렌더/생성기가 파싱해 실제 `<select><option>`로 전개한다. 셀 `type`에 `columns`/`select` 중첩을 금지하는 §1.2 규칙 준수.

### 3.3 TOOLBAR

```json
{
  "title": "툴바",
  "fields": [
    {
      "key": "buttons",
      "label": "버튼 목록",
      "type": "columns",
      "required": true,
      "default": [
        { "actionCode": "add",    "label": "추가", "styleClass": "btn-primary" },
        { "actionCode": "delete", "label": "삭제", "styleClass": "btn-secondary" }
      ],
      "columns": [
        { "key": "actionCode", "label": "액션코드(add/delete/save 등)", "type": "text" },
        { "key": "label",      "label": "버튼 라벨", "type": "text" },
        { "key": "styleClass", "label": "스타일 클래스", "type": "text" }
      ]
    }
  ]
}
```

---

## 4. 기획서 2장 "주요 속성" → 스키마 필드 매핑표 (자체 점검)

기획서_v0.1.md 2장 모듈 팔레트 표의 "주요 속성" 항목을 각 스키마 필드로 매핑한다. 누락 0건 확인.

### 4.1 TableView (근거: commonListTableView.js)

| 기획서 "주요 속성" | 스키마 필드 | 상태 |
|---|---|---|
| 컬럼[{name,displayName,displayYn,sortYn}] | `columns` (반복행: name/displayName/displayYn/sortYn) | 커버 |
| 선택(checkbox/radio) | `selectMode` (none/checkbox/radio) | 커버 |
| 페이징 | `pagingYn` | 커버 |
| 엑셀/CSV | `excelYn`, `csvYn` | 커버 |

### 4.2 SearchFilterBar (근거: commonListTableView.getCurrentSearchData)

| 기획서 "주요 속성" | 스키마 필드 | 상태 |
|---|---|---|
| 필터(select)들 | `filters` (반복행: name/label/options) | 커버 |
| 키워드 | `keywordYn` | 커버 |
| 날짜범위(datepicker) | `dateRangeYn` | 커버 |

### 4.3 Toolbar/ButtonGroup

| 기획서 "주요 속성" | 스키마 필드 | 상태 |
|---|---|---|
| 추가/삭제/저장 등 액션 버튼 | `buttons` (반복행: actionCode/label/styleClass) | 커버 |

### 4.4 규약 준수 self-check

- [x] 3개 스키마 모두 최상위 `{ title, fields[] }` 형태.
- [x] 모든 `Field`가 필수키 5종(`key`,`label`,`type`,`required`,`default`) 보유.
- [x] 모든 `type`이 허용 집합(`text`/`number`/`boolean`/`select`/`columns`/`chips`) 소속.
- [x] `select`는 `options[]`, `columns`는 `columns[]` 하위 정의 포함.
- [x] `columns` 셀 `type`은 단순 타입만 사용(중첩 없음).
- [x] `key`는 전부 camelCase, 화이트리스트 정규식 통과.
- [x] 기획서 2장 "주요 속성" 매핑 누락 0건(§4.1~4.3).
