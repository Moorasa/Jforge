package com.jworks.forge.screen.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.jworks.forge.catalog.domain.ModuleType;
import com.jworks.forge.catalog.mapper.ModuleTypeMapper;
import com.jworks.forge.code.domain.CommonCode;
import com.jworks.forge.code.mapper.CommonCodeMapper;
import com.jworks.forge.screen.validation.ArchetypeSlots.Cardinality;
import com.jworks.forge.screen.validation.ArchetypeSlots.SlotRule;

/**
 * DEFINITION_JSON 구조 검증기(P3-5b — 🔒 서버 신뢰경계, 최종 방어선).
 *
 * <p>클라(P3-5 palette.js)의 화이트리스트는 UX 편의용 1차 검증이며 신뢰 대상이 아니다.
 * 저장 경로가 통과시키는 문서의 <b>구조</b>를 이 검증기가 최종적으로 방어한다
 * (스키마_DEFINITION_JSON.md §5 방어 책임 분배표).
 *
 * <p><b>검증 대상(구조만)</b>:
 * <ul>
 *   <li>{@code archetype}이 공통코드 {@code ARCHETYPE} 그룹에 실존(P3-1 리뷰 인계) + 슬롯 화이트리스트 존재.</li>
 *   <li>{@code role}이 공통코드 {@code ROLE} 그룹에 실존.</li>
 *   <li>{@code slots}의 각 {@code slotKey}가 해당 아키타입 슬롯 화이트리스트(§2)에 속함.</li>
 *   <li>각 인스턴스 {@code moduleTypeCode}가 {@code TB_FRG_MODULE_TYPE}에 {@code USE_YN='Y'}로 실존.</li>
 *   <li>그 모듈의 {@code CATEGORY_CODE}가 배치 슬롯의 허용 카테고리(§2.4)에 속함.</li>
 *   <li>{@code 1..1}(listArea 등) cardinality 위반(0개/2개 이상)은 <b>하드 실패</b>(§2.5 확정).</li>
 *   <li>{@code instanceId} 형태(§1.3 정규식) + 문서 전체 유일성.</li>
 * </ul>
 *
 * <p><b>검증하지 않는 것(무가공 저장)</b>: props의 자유문자열 값(label/displayName/styleClass/컬럼값 등).
 * 서버는 이 값을 이스케이프·가공하지 않으며 저장 허용한다 — 표시 이스케이프는 소비자(프리뷰 P3-6/생성기 P4)
 * 책임이다(스키마_DEFINITION_JSON.md §5, PROP_SCHEMA §2.1과 동일 원칙). 여기서는 <b>구조 키만</b> 방어한다.
 *
 * <p>여러 위반을 모아 {@link DefinitionValidationException}으로 한 번에 반환한다.
 * DEFINITION_JSON은 <b>데이터로만</b> 취급하며(Jackson 파싱), 문자열 조립·코드 평가는 없다.
 */
@Component
public class DefinitionValidator {

    /** §1.3 instanceId 형태 정규식(영문 시작, 영숫자·_·- 만, 64자 이내). */
    private static final Pattern INSTANCE_ID = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]{0,63}$");
    /** 생성 화면 런타임은 외부 호스트가 아닌 현재 앱의 절대 경로만 fetch한다. */
    private static final Pattern SAME_ORIGIN_ENDPOINT = Pattern.compile("^/(?!/)[^\\s\\\\]{0,499}$");
    private static final Set<String> DATA_METHODS = Set.of("GET", "POST");
    /** 서버 바인딩(계약 §14.1) DB 식별자 — 생성기 게이트({@code ServerBinding})와 같은 형태. */
    private static final Pattern DB_IDENTIFIER = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,62}$");
    private static final Set<String> EVENT_TYPES = Set.of("click", "change", "select");
    private static final Set<String> EVENT_ACTIONS = Set.of("reload", "openDetail", "submit", "custom");
    /** §17.8 자식을 담을 수 있는 캔버스 모듈(현재 PANEL 하나). */
    static final Set<String> CANVAS_CONTAINER_TYPES = Set.of("PANEL");
    /** §17.8 캔버스 중첩 최대 깊이(루트=1). */
    static final int MAX_CANVAS_DEPTH = 3;

    private final CommonCodeMapper commonCodeMapper;
    private final ModuleTypeMapper moduleTypeMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DefinitionValidator(CommonCodeMapper commonCodeMapper, ModuleTypeMapper moduleTypeMapper) {
        this.commonCodeMapper = commonCodeMapper;
        this.moduleTypeMapper = moduleTypeMapper;
    }

    /**
     * DEFINITION_JSON 원문을 검증한다. 위반이 하나라도 있으면 {@link DefinitionValidationException}을 던진다.
     *
     * @param definitionJsonRaw 저장하려는 DEFINITION_JSON 전체(JSON 문자열)
     * @return 파싱된 루트 노드(호출부가 재사용 가능). 검증 통과 시에만 반환.
     */
    public JsonNode validate(String definitionJsonRaw) {
        List<String> violations = new ArrayList<>();

        // 0) 유효 JSON 오브젝트인지.
        JsonNode root;
        try {
            root = objectMapper.readTree(definitionJsonRaw == null ? "" : definitionJsonRaw);
        } catch (Exception e) {
            throw new DefinitionValidationException(List.of("유효한 JSON이 아닙니다: " + e.getMessage()));
        }
        if (root == null || !root.isObject()) {
            throw new DefinitionValidationException(List.of("DEFINITION_JSON 최상위는 객체여야 합니다."));
        }

        // 1) schemaVersion(현재 1만). 형태만 확인(관용적: 없으면 경고성 위반).
        JsonNode ver = root.get("schemaVersion");
        if (ver == null || !ver.isNumber()) {
            violations.add("schemaVersion(number)은 필수입니다.");
        }

        // 2) archetype: ARCHETYPE 공통코드 실존 + 슬롯 화이트리스트 보유.
        String archetype = textOf(root, "archetype");
        boolean archetypeUsable = false;
        if (archetype == null) {
            violations.add("archetype는 필수입니다.");
        } else if (!codeExists("ARCHETYPE", archetype)) {
            violations.add("archetype '" + archetype + "'는 ARCHETYPE 공통코드에 존재하지 않습니다.");
        } else if (!ArchetypeSlots.isKnownArchetype(archetype)) {
            // 공통코드엔 있으나 서버 슬롯 화이트리스트가 아직 없는 아키타입(예: 예약만 된 경우).
            violations.add("archetype '" + archetype + "'의 슬롯 화이트리스트가 서버에 정의되어 있지 않습니다.");
        } else {
            archetypeUsable = true;
        }

        // 3) role: ROLE 공통코드 실존.
        String role = textOf(root, "role");
        if (role == null) {
            violations.add("role은 필수입니다.");
        } else if (!codeExists("ROLE", role)) {
            violations.add("role '" + role + "'는 ROLE 공통코드에 존재하지 않습니다.");
        }

        // 4) slots 구조 검증(archetype 화이트리스트가 유효할 때만 슬롯 세부 검증 수행).
        JsonNode slots = root.get("slots");
        if (slots == null || !slots.isObject()) {
            violations.add("slots(객체)는 필수입니다.");
        } else if (archetypeUsable) {
            validateSlots(archetype, slots, violations);
        }
        // archetype이 무효면 슬롯키 화이트리스트 기준이 없으므로 슬롯 세부 검증은 생략(archetype 위반이 이미 잡힘).

        // 5) canvas(§17.2, P13): FREE_CANVAS 시트 크기. 있으면 숫자·범위를 검증한다.
        //    다른 아키타입에는 존재하지 않는 노드이므로 기존 화면 검증에 영향이 없다(add-only).
        validateCanvas(root.get("canvas"), violations);

        // 6) 중첩 참조(§17.8, P13-7): canvasArea 의 layoutParentId 무결성.
        //    canvasArea 슬롯이 없으면 검사 대상이 아니다(기존 아키타입 무영향).
        if (slots != null && slots.isObject()) {
            validateCanvasNesting(slots.get("canvasArea"), violations);
        }

        if (!violations.isEmpty()) {
            throw new DefinitionValidationException(violations);
        }
        return root;
    }

    /**
     * §17.2 {@code canvas} 노드 검증(P13, add-only). 노드가 없으면 검증 대상이 아니다(기존 화면 무영향).
     *
     * <p>있으면 객체여야 하고 {@code widthPx}/{@code heightPx}는 <b>숫자 + 범위</b>를 만족해야 한다.
     * 산출측(§17.3)은 어차피 숫자 게이트를 다시 통과시키지만, 저장 시점에 잡아야 사용자가 원인을 안다.
     */
    private void validateCanvas(JsonNode canvas, List<String> violations) {
        if (canvas == null || canvas.isNull()) {
            return;
        }
        if (!canvas.isObject()) {
            violations.add("canvas는 객체여야 합니다.");
            return;
        }
        validateCanvasDimension(canvas, "widthPx", 320, 4000, violations);
        validateCanvasDimension(canvas, "heightPx", 240, 8000, violations);
    }

    private void validateCanvasDimension(JsonNode canvas, String field, int min, int max,
                                         List<String> violations) {
        JsonNode node = canvas.get(field);
        if (node == null || node.isNull()) {
            return; // 미지정 = 기본값 사용(§17.2)
        }
        if (!node.isNumber()) {
            violations.add("canvas." + field + "는 숫자여야 합니다.");
            return;
        }
        int value = node.asInt();
        if (value < min || value > max) {
            violations.add("canvas." + field + "는 " + min + "~" + max + " 범위여야 합니다.");
        }
    }

    /**
     * §17.8 캔버스 중첩 참조 검증(P13-7). {@code props.layoutParentId}는 <b>같은 canvasArea 안의
     * 컨테이너 인스턴스</b>를 가리켜야 한다.
     *
     * <p>저장 시점에는 하드 실패로 잡아 사용자가 원인을 알게 하고, 산출 시점(TemplateContextBuilder)에는
     * 같은 위반을 <b>루트로 수렴</b>시킨다 — 손상된 데이터가 화면 전체를 못 지우게 하는 이중 방어다.
     *
     * <p>검사: 자기 참조 · 미존재 부모 · 컨테이너가 아닌 부모 · 순환 · 최대 깊이 초과.
     */
    private void validateCanvasNesting(JsonNode canvasArea, List<String> violations) {
        if (canvasArea == null || !canvasArea.isArray() || canvasArea.isEmpty()) {
            return;
        }
        Map<String, String> parentOf = new HashMap<>();   // instanceId → parentId
        Map<String, String> typeOf = new HashMap<>();     // instanceId → moduleTypeCode
        for (JsonNode inst : canvasArea) {
            if (inst == null || !inst.isObject()) {
                continue;
            }
            String id = textOf(inst, "instanceId");
            if (id == null) {
                continue;
            }
            typeOf.put(id, textOf(inst, "moduleTypeCode"));
            JsonNode props = inst.get("props");
            String parentId = (props != null && props.isObject()) ? textOf(props, "layoutParentId") : null;
            if (parentId != null) {
                parentOf.put(id, parentId);
            }
        }

        for (Map.Entry<String, String> entry : parentOf.entrySet()) {
            String id = entry.getKey();
            String parentId = entry.getValue();
            String where = "canvasArea 인스턴스 '" + id + "'";

            if (id.equals(parentId)) {
                violations.add(where + "의 layoutParentId가 자기 자신입니다.");
                continue;
            }
            if (!typeOf.containsKey(parentId)) {
                violations.add(where + "의 layoutParentId '" + parentId + "'가 캔버스에 없습니다.");
                continue;
            }
            if (!CANVAS_CONTAINER_TYPES.contains(typeOf.get(parentId))) {
                violations.add(where + "의 부모 '" + parentId + "'는 자식을 담을 수 없는 모듈입니다.");
                continue;
            }
            // 순환·깊이: 부모 사슬을 타고 올라간다.
            Set<String> seen = new HashSet<>();
            seen.add(id);
            String cursor = parentId;
            int depth = 1;
            boolean broken = false;
            while (cursor != null) {
                if (!seen.add(cursor)) {
                    violations.add(where + "의 부모 관계가 순환합니다.");
                    broken = true;
                    break;
                }
                depth++;
                if (depth > MAX_CANVAS_DEPTH) {
                    violations.add(where + "의 중첩 깊이가 최대 " + MAX_CANVAS_DEPTH + "단계를 넘습니다.");
                    broken = true;
                    break;
                }
                cursor = parentOf.get(cursor);
            }
            if (broken) {
                continue;
            }
        }
    }

    /** slots 맵 순회: slotKey 화이트리스트 · moduleTypeCode 실존 · 카테고리 · cardinality · instanceId. */
    private void validateSlots(String archetype, JsonNode slots, List<String> violations) {
        Map<String, SlotRule> ruleMap = ArchetypeSlots.slotsOf(archetype);

        // moduleTypeCode → CATEGORY_CODE 조회 캐시(같은 코드 반복 조회 방지). null=미실존/미사용.
        Map<String, String> categoryCache = new HashMap<>();
        // instanceId 문서 전체 유일성 추적.
        Set<String> seenInstanceIds = new HashSet<>();

        var fieldNames = slots.fieldNames();
        while (fieldNames.hasNext()) {
            String slotKey = fieldNames.next();
            SlotRule rule = ruleMap.get(slotKey);
            JsonNode arr = slots.get(slotKey);

            // 4-1) slotKey 화이트리스트.
            if (rule == null) {
                violations.add("slotKey '" + slotKey + "'는 아키타입 '" + archetype + "'의 화이트리스트 밖입니다.");
                continue; // 규칙 없는 슬롯은 인스턴스 검증 불가.
            }
            if (arr == null || !arr.isArray()) {
                violations.add("slotKey '" + slotKey + "'의 값은 배열이어야 합니다.");
                continue;
            }

            // 4-2) cardinality.
            int count = arr.size();
            if (rule.getCardinality() == Cardinality.MIN_ONE_MAX_ONE && count != 1) {
                // 1..1 위반(0개 또는 2개 이상) = 하드 실패(§2.5 확정 정책).
                violations.add("slotKey '" + slotKey + "'는 정확히 1개(1..1)여야 하나 " + count + "개입니다.");
            } else if (rule.getCardinality() == Cardinality.ZERO_OR_ONE && count > 1) {
                violations.add("slotKey '" + slotKey + "'는 최대 1개(0..1)여야 하나 " + count + "개입니다.");
            }

            // 4-3) 각 인스턴스 검증.
            for (int i = 0; i < arr.size(); i++) {
                validateInstance(slotKey, rule, arr.get(i), i, categoryCache, seenInstanceIds, violations);
            }
        }
    }

    /** 인스턴스 1개: instanceId 형태·유일성, moduleTypeCode 실존, 카테고리 일치. */
    private void validateInstance(String slotKey, SlotRule rule, JsonNode inst, int idx,
                                  Map<String, String> categoryCache, Set<String> seenInstanceIds,
                                  List<String> violations) {
        String where = "slot '" + slotKey + "'[" + idx + "]";
        if (inst == null || !inst.isObject()) {
            violations.add(where + " 인스턴스는 객체여야 합니다.");
            return;
        }

        // instanceId 형태 + 유일성.
        String instanceId = textOf(inst, "instanceId");
        if (instanceId == null) {
            violations.add(where + " instanceId는 필수입니다.");
        } else if (!INSTANCE_ID.matcher(instanceId).matches()) {
            violations.add(where + " instanceId '" + instanceId + "'는 형식(^[a-zA-Z][a-zA-Z0-9_-]{0,63}$)에 맞지 않습니다.");
        } else if (!seenInstanceIds.add(instanceId)) {
            violations.add("instanceId '" + instanceId + "'가 문서 내에서 중복됩니다.");
        }

        // moduleTypeCode 실존(USE_YN='Y') + 카테고리 일치.
        String moduleTypeCode = textOf(inst, "moduleTypeCode");
        if (moduleTypeCode == null) {
            violations.add(where + " moduleTypeCode는 필수입니다.");
            return;
        }
        String category = lookupCategory(moduleTypeCode, categoryCache);
        if (category == null) {
            violations.add(where + " moduleTypeCode '" + moduleTypeCode
                    + "'는 사용중(USE_YN='Y') 모듈로 존재하지 않습니다.");
            return;
        }
        if (!rule.getCategories().contains(category)) {
            violations.add(where + " 모듈 '" + moduleTypeCode + "'(카테고리 " + category
                    + ")는 slotKey '" + slotKey + "'의 허용 카테고리 " + rule.getCategories() + "에 속하지 않습니다.");
        }
        // props의 자유문자열 값은 검증하지 않고 그대로 저장(구조만 방어 — §5, 소비자 이스케이프 책임).
        validateDesignMetadata(inst, where, violations);
    }

    /**
     * 인스턴스 단위 선언형 메타(data/events)의 최소 구조만 검증한다.
     * endpoint·target은 코드/경로가 아니라 데이터로만 보존하며, 실제 런타임 실행은 생성된 화면의 책임이다.
     */
    private void validateDesignMetadata(JsonNode inst, String where, List<String> violations) {
        JsonNode data = inst.get("data");
        if (data != null && !data.isNull()) {
            if (!data.isObject()) {
                violations.add(where + " data는 객체여야 합니다.");
            } else {
                String endpoint = textOf(data, "endpoint");
                if (endpoint == null || !SAME_ORIGIN_ENDPOINT.matcher(endpoint).matches()) {
                    violations.add(where + " data.endpoint는 같은 도메인의 /... 경로(최대 500자)여야 합니다.");
                }
                String method = textOf(data, "method");
                if (method == null || !DATA_METHODS.contains(method)) {
                    violations.add(where + " data.method는 GET 또는 POST여야 합니다.");
                }
                JsonNode resultPath = data.get("resultPath");
                if (resultPath != null && (!resultPath.isTextual() || resultPath.asText().length() > 200)) {
                    violations.add(where + " data.resultPath는 200자 이하 문자열이어야 합니다.");
                }
                JsonNode autoLoad = data.get("autoLoad");
                if (autoLoad != null && !autoLoad.isBoolean()) {
                    violations.add(where + " data.autoLoad는 boolean이어야 합니다.");
                }
                // 서버 바인딩(계약 §14.1). 선택 키이며, 있으면 DB 식별자 형태만 허용한다.
                // 실제 코드 산출 게이트는 생성기(ServerBinding)가 다시 건다 — 여기서는 저장 경계 방어.
                validateDbIdentifier(data, "table", where, violations);
                validateDbIdentifier(data, "keyColumn", where, violations);
            }
        }

        JsonNode events = inst.get("events");
        if (events == null || events.isNull()) {
            return;
        }
        if (!events.isArray() || events.size() > 20) {
            violations.add(where + " events는 최대 20개의 배열이어야 합니다.");
            return;
        }
        for (int i = 0; i < events.size(); i++) {
            JsonNode event = events.get(i);
            String eventWhere = where + " events[" + i + "]";
            if (event == null || !event.isObject()) {
                violations.add(eventWhere + "는 객체여야 합니다.");
                continue;
            }
            String eventType = textOf(event, "event");
            if (eventType == null || !EVENT_TYPES.contains(eventType)) {
                violations.add(eventWhere + ".event는 click, change, select 중 하나여야 합니다.");
            }
            String action = textOf(event, "action");
            if (action == null || !EVENT_ACTIONS.contains(action)) {
                violations.add(eventWhere + ".action은 허용된 동작이어야 합니다.");
            }
            JsonNode target = event.get("target");
            if (target != null && (!target.isTextual() || target.asText().length() > 200)) {
                violations.add(eventWhere + ".target은 200자 이하 문자열이어야 합니다.");
            }
        }
    }

    /**
     * {@code data}의 선택 DB 식별자 키(계약 §14.1)를 검증한다. 키가 없으면 통과(선택),
     * 있으면 문자열 + {@link #DB_IDENTIFIER} 형태만 허용한다.
     */
    private void validateDbIdentifier(JsonNode data, String field, String where, List<String> violations) {
        JsonNode node = data.get(field);
        if (node == null || node.isNull()) {
            return;
        }
        if (!node.isTextual() || !DB_IDENTIFIER.matcher(node.asText()).matches()) {
            violations.add(where + " data." + field
                    + "은(는) DB 식별자 형식(^[A-Za-z][A-Za-z0-9_]{0,62}$)이어야 합니다.");
        }
    }

    /** moduleTypeCode → CATEGORY_CODE(사용중만). 미실존/미사용이면 null. 캐시 사용. */
    private String lookupCategory(String moduleTypeCode, Map<String, String> cache) {
        if (cache.containsKey(moduleTypeCode)) {
            return cache.get(moduleTypeCode);
        }
        ModuleType mt = moduleTypeMapper.selectActiveByCode(moduleTypeCode);
        String cat = (mt == null) ? null : mt.getCategoryCode();
        cache.put(moduleTypeCode, cat);
        return cat;
    }

    /**
     * 공통코드 그룹에 code가 실존하는지 외부 노출(메타 저장 경계 재사용용 — 중복 구현 금지).
     * {@code ForgeScreenService.create/updateMeta}가 roleCode/archetypeCode를 세팅 전 실존 검증하는 데
     * 이 메서드를 재사용한다(§5.1 쓰기 경계).
     *
     * @param grpCode 공통코드 그룹(예: {@code ROLE}, {@code ARCHETYPE})
     * @param code    검사할 코드
     * @return USE_YN='Y' 목록에 실존하면 true
     */
    public boolean isCodeInGroup(String grpCode, String code) {
        return codeExists(grpCode, code);
    }

    /** 공통코드 그룹에 code가 실존하는지(USE_YN='Y' 목록 기준). */
    private boolean codeExists(String grpCode, String code) {
        List<CommonCode> list = commonCodeMapper.selectByGroup(grpCode);
        if (list == null) { return false; }
        for (CommonCode c : list) {
            if (code.equals(c.getCode())) { return true; }
        }
        return false;
    }

    /** 객체 노드에서 텍스트 필드 추출(문자열 아니거나 없으면 null). */
    private static String textOf(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v != null && v.isTextual()) ? v.asText() : null;
    }
}
