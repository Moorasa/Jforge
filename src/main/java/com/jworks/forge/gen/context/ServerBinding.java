package com.jworks.forge.gen.context;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🔒 서버 바인딩 해석기 (P10, 계약 §14). <b>순수 함수 + 불변 record</b> — 파일 접근 0, 상태 0.
 *
 * <p>화면 설계(model.slots)의 {@code listArea[0]} 인스턴스에서 선언형 {@code data} 노드를 읽어
 * "이 화면이 어떤 테이블을 어떤 경로로 조회하는가"를 해석한다. 결과는 {@link com.jworks.forge.gen.pipeline.StubGenerator}가
 * Controller/Mapper/XML <b>내용</b>을 승격하는 데 쓴다(경로·파일명은 불변 — 계약 §14.3).
 *
 * <p><b>보안 모델(계약 §14.2/§14.4)</b>: 산출 코드에 들어가는 값은 전부 아래 화이트리스트 정규식을
 * 통과한 <b>원본</b>이다. 즉 이스케이프로 무해화하는 것이 아니라 <b>인젝션 표면 자체를 없앤다</b>.
 * 게이트를 하나라도 통과하지 못하면 {@link Optional#empty()}를 돌려주고, 호출측은 <b>기존 빈 stub</b>을
 * 그대로 산출한다(실패가 항상 안전측으로 수렴 — 골든 무손상의 근거).
 *
 * <p>자유문자열(label/displayName/styleClass 등)은 <b>어떤 경우에도</b> 이 해석기를 통과하지 못한다.
 */
public record ServerBinding(
        String table,
        String keyColumn,
        String endpoint,
        String method,
        String resultPath,
        List<String> columns) {

    private static final Logger log = LoggerFactory.getLogger(ServerBinding.class);

    /** DB 식별자(테이블·컬럼) 화이트리스트(계약 §14.1). */
    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,62}$");
    /** API 경로 화이트리스트 — §5 저장검증(SAME_ORIGIN_ENDPOINT)보다 <b>좁힌</b> 재검증(계약 §14.2). */
    private static final Pattern ENDPOINT = Pattern.compile("^/[A-Za-z0-9/_-]{1,200}$");
    /** 응답 래핑 키(계약 §14.2). */
    private static final Pattern RESULT_PATH = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");
    /** 허용 HTTP 메서드(리터럴). */
    private static final Set<String> METHODS = Set.of("GET", "POST");

    /** 바인딩 소스 슬롯(계약 §14.2 — 단일 cardinality). */
    private static final String SLOT_LIST_AREA = "listArea";
    /** 컬럼 상한(계약 §14.2). 초과분은 잘라낸다. */
    private static final int MAX_COLUMNS = 100;

    /**
     * model에서 서버 바인딩을 해석한다. 게이트 미통과 시 {@link Optional#empty()}(= stub 폴백).
     *
     * @param model {@link TemplateContextBuilder#build}가 만든 렌더 모델(맵 조회 전용)
     * @return 게이트를 전부 통과한 불변 바인딩, 없으면 empty
     */
    public static Optional<ServerBinding> resolve(Map<String, Object> model) {
        if (model == null) {
            return Optional.empty();
        }
        Map<?, ?> inst = listAreaInstance(model);
        if (inst == null) {
            return Optional.empty();
        }
        if (!(inst.get("data") instanceof Map<?, ?> data)) {
            return Optional.empty(); // 선언형 data 없음 = 서버 바인딩 대상 아님.
        }

        // 1) table — 서버 바인딩의 트리거. 없거나 형식 위반이면 승격하지 않는다.
        String table = gated(text(data.get("table")), IDENTIFIER);
        if (table == null) {
            return Optional.empty();
        }

        // 2) endpoint — 좁힌 재검증(§14.2). 점·공백·중괄호 등은 여기서 전부 탈락.
        String endpoint = gated(text(data.get("endpoint")), ENDPOINT);
        if (endpoint == null) {
            log.warn("[ServerBinding] endpoint 게이트 미통과 — stub 폴백(table={})", table);
            return Optional.empty();
        }

        // 3) method — GET/POST 리터럴만(§5 저장검증이 이미 강제하므로 정상 문서는 항상 통과).
        String method = text(data.get("method"));
        if (method == null || !METHODS.contains(method)) {
            log.warn("[ServerBinding] method 게이트 미통과 — stub 폴백(table={})", table);
            return Optional.empty();
        }

        // 4) resultPath — 없으면 배열 그대로 반환(null). 있으면 형식 통과 필수.
        String rawResultPath = text(data.get("resultPath"));
        String resultPath = null;
        if (rawResultPath != null && !rawResultPath.isBlank()) {
            resultPath = gated(rawResultPath, RESULT_PATH);
            if (resultPath == null) {
                log.warn("[ServerBinding] resultPath 게이트 미통과 — stub 폴백(table={})", table);
                return Optional.empty();
            }
        }

        // 5) keyColumn — 선택. 미통과면 상세 핸들러만 생략하고 목록은 승격한다(§14.2).
        String keyColumn = gated(text(data.get("keyColumn")), IDENTIFIER);

        // 6) columns — 뷰 props에서 식별자 통과분만. 0개면 SELECT를 만들 수 없으므로 승격 중단.
        List<String> columns = resolveColumns(inst);
        if (columns.isEmpty()) {
            log.warn("[ServerBinding] 유효 컬럼 0 — stub 폴백(table={})", table);
            return Optional.empty();
        }

        return Optional.of(new ServerBinding(table, keyColumn, endpoint, method, resultPath, columns));
    }

    /** 상세(단건) 핸들러·selectOne 산출 여부. */
    public boolean hasKeyColumn() {
        return keyColumn != null;
    }

    /** 목록 응답을 {@code {resultPath: [...]}}로 감쌀지 여부(§14.3 — §9 런타임 valueAtPath 정합). */
    public boolean wrapsResult() {
        return resultPath != null;
    }

    /** {@code listArea[0]} 인스턴스(없으면 null). 리터럴 슬롯키 조회만(계약 §2.2). */
    private static Map<?, ?> listAreaInstance(Map<String, Object> model) {
        if (!(model.get("slots") instanceof Map<?, ?> slots)) {
            return null;
        }
        if (!(slots.get(SLOT_LIST_AREA) instanceof List<?> instances) || instances.isEmpty()) {
            return null;
        }
        return (instances.get(0) instanceof Map<?, ?> inst) ? inst : null;
    }

    /**
     * 뷰 props의 {@code columns[].name} 중 식별자 게이트 통과분만(순서 보존·중복 제거·상한 적용).
     * 표시용 자유문자열(displayName 등)은 채택하지 않는다.
     */
    private static List<String> resolveColumns(Map<?, ?> inst) {
        List<String> picked = new ArrayList<>();
        if (!(inst.get("props") instanceof Map<?, ?> props)) {
            return picked;
        }
        if (!(props.get("columns") instanceof List<?> columns)) {
            return picked;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Object colObj : columns) {
            if (!(colObj instanceof Map<?, ?> col)) {
                continue;
            }
            String name = gated(text(col.get("name")), IDENTIFIER);
            if (name == null) {
                continue; // 형식 위반 컬럼은 조용히 제외(전체 실패로 번지지 않게).
            }
            if (seen.add(name)) {
                picked.add(name);
            }
            if (picked.size() >= MAX_COLUMNS) {
                log.warn("[ServerBinding] 컬럼 상한({}) 초과분 절단", MAX_COLUMNS);
                break;
            }
        }
        return picked;
    }

    /** 정규식 통과 시 원본 반환, 아니면 null(= 게이트 미통과). */
    private static String gated(String value, Pattern gate) {
        return (value != null && gate.matcher(value).matches()) ? value : null;
    }

    /** 맵 값에서 문자열만 추출(다른 타입·null → null). */
    private static String text(Object value) {
        return (value instanceof String s) ? s : null;
    }
}
