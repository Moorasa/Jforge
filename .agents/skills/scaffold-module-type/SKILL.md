---
name: scaffold-module-type
description: J-FORGE 팔레트에 새 모듈 타입 1개를 "한 세트"로 생성 — TB_FRG_MODULE_TYPE 카탈로그행 + PROP_SCHEMA_JSON(속성패널 스키마) + FreeMarker 생성 템플릿(3종) + 라이브 프리뷰 파셜. 새 팔레트 모듈을 추가하거나 기존 모듈 정의를 골격부터 만들 때 사용.
---

# scaffold-module-type

J-FORGE 빌더에 **새 팔레트 모듈 타입**을 추가하는 스킬. 빌더판 "3종 1세트"로, 한 모듈은 아래 **4개 산출물**이 항상 함께 만들어져야 한다. 하나라도 빠지면 팔레트에서 조립·프리뷰·생성이 깨진다.

## 만들어야 하는 4개 산출물
1. **카탈로그 행** — `TB_FRG_MODULE_TYPE` 시드 (MODULE_TYPE_CODE, MODULE_NAME, CATEGORY_CODE, PROP_SCHEMA_JSON, TEMPLATE_KEY, PREVIEW_KEY)
2. **속성 스키마** — `PROP_SCHEMA_JSON`: 우측 속성패널이 폼으로 렌더할 필드 정의(타입/기본값/검증/옵션)
3. **생성 템플릿** — 이 모듈이 산출할 JSP+JS+CSS FreeMarker 템플릿 (`TEMPLATE_KEY`로 참조)
4. **프리뷰 파셜** — 중앙 라이브 프리뷰(iframe, 번들 런타임 로드)에서 렌더할 조각 (`PREVIEW_KEY`로 참조)

## 절차
1. **레퍼런스 확인** — 대응하는 MagicIAM 컴포넌트를 `C:\DEV\MagicIAM_advancement\MagicIAM2.0_EGI1.0`에서 찾아 읽는다(예: TableView → `commonListTableView.js`). 없으면 사용자에게 근거 파일을 묻는다.
2. **MODULE_TYPE_CODE 결정** — 대문자 스네이크, 카테고리(VIEW/FILTER/ACTION/DETAIL/WIDGET 등) 지정. 중복 검사.
3. **PROP_SCHEMA_JSON 설계** — 그 모듈이 화면마다 바꿔야 할 속성만 필드로. 예 TableView: `columns[{name,displayName,displayYn,sortYn}]`, 선택모드(checkbox/radio/none), 페이징, 엑셀/CSV.
4. **FreeMarker 템플릿 작성** — template-author 규약을 따른다. JSP 스크립트릿 금지, JS는 `MagicIAM_JS{Domain}{Role}` 네임스페이스+IIFE+`__defined`. **jQuery 3.7.1**, **저작권 배너 미포함**.
5. **프리뷰 파셜 작성** — 속성 기본값으로 즉시 렌더 가능한 최소 마크업.
6. **골든 픽스처 추가** — 대표 DEFINITION_JSON 입력과 기대 산출물을 tester 골든셋에 등록.

## 규약·제약 (필수)
- DB 네이밍: 대문자 스네이크/_CODE/_ID/_YN, `SELECT *` 금지.
- 경로안전: 이 모듈이 만들 파일도 `TARGET_ROOT_PATH` 하위·확장자 화이트리스트를 벗어나지 않도록 템플릿 경로를 상대경로로만.
- 사용자 입력(컬럼명/도메인명)은 식별자 화이트리스트 검증 전제 — 템플릿 인젝션 방지.

## 완료 기준
- 4개 산출물이 모두 존재하고 서로 `TEMPLATE_KEY`/`PREVIEW_KEY`로 연결됨.
- 팔레트에 뜨고, 속성패널이 스키마대로 렌더되고, 프리뷰가 그려지고, 생성 시 3종이 나옴.
- tester 골든 통과, reviewer 경로/인젝션 체크 통과.
