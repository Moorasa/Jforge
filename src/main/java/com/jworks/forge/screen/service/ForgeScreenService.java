package com.jworks.forge.screen.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.jworks.forge.common.web.NotFoundException;
import com.jworks.forge.project.mapper.ForgeProjectMapper;
import com.jworks.forge.screen.domain.ForgeScreen;
import com.jworks.forge.screen.dto.ForgeScreenDuplicateRequest;
import com.jworks.forge.screen.dto.ForgeScreenRequest;
import com.jworks.forge.screen.mapper.ForgeScreenMapper;
import com.jworks.forge.screen.validation.DefinitionValidationException;
import com.jworks.forge.screen.validation.DefinitionValidator;

/**
 * 화면 CRUD 비즈니스 로직 (P3-1).
 *
 * <p>DEFINITION_JSON은 무가공 왕복이 원칙이나, <b>생성 시 최소 골격</b>은 서버가 채운다
 * (스키마_DEFINITION_JSON.md §1: schemaVersion/archetype/stem/role/slots). 이 골격은
 * 사용자 자유입력을 문자열로 조립하지 않고 Jackson {@link ObjectNode}로 안전하게 구성해
 * 템플릿/JSON 인젝션을 배제한다. 본문 갱신(slots 조립)은 이 태스크 범위 밖(P3-6b).
 *
 * <p>논리삭제는 STATUS_CODE='DELETED' 전환으로 표현한다(V1 DDL에 USE_YN 없음 — P3-1 결정).
 */
@Service
public class ForgeScreenService {

    private final ForgeScreenMapper mapper;
    private final ForgeProjectMapper projectMapper;
    private final DefinitionValidator definitionValidator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ForgeScreenService(ForgeScreenMapper mapper, ForgeProjectMapper projectMapper,
                              DefinitionValidator definitionValidator) {
        this.mapper = mapper;
        this.projectMapper = projectMapper;
        this.definitionValidator = definitionValidator;
    }

    public List<ForgeScreen> listByProject(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId는 필수");
        }
        return mapper.selectListByProject(projectId);
    }

    public ForgeScreen get(Long id) {
        ForgeScreen s = mapper.selectById(id);
        if (s == null) {
            throw new NotFoundException("screen not found: " + id);
        }
        return s;
    }

    @Transactional
    public ForgeScreen create(ForgeScreenRequest req) {
        // FK(PROJECT_ID) 위반을 사용자 오류로 변환: 없는 프로젝트면 404.
        if (projectMapper.selectActiveById(req.projectId()) == null) {
            throw new NotFoundException("project not found: " + req.projectId());
        }
        // 🔒 archetype/role 공통코드 실존 검증(§5.1 쓰기 경계). 화이트리스트 밖이면 400.
        validateMetaCodes(req);
        ForgeScreen s = new ForgeScreen();
        s.setProjectId(req.projectId());
        s.setScreenName(req.screenName());
        s.setStem(req.stem());
        s.setArchetypeCode(req.archetypeCode());
        s.setRoleCode(req.roleCode());
        s.setStatusCode("DRAFT");
        s.setDefinitionJson(buildInitialDefinition(req));
        mapper.insert(s);
        return get(s.getScreenId());
    }

    @Transactional
    public ForgeScreen updateMeta(Long id, ForgeScreenRequest req) {
        ForgeScreen current = get(id); // 존재 확인(없으면 404)
        // 🔒 archetype/role 공통코드 실존 검증(§5.1 쓰기 경계). 화이트리스트 밖이면 400.
        validateMetaCodes(req);
        current.setScreenName(req.screenName());
        current.setArchetypeCode(req.archetypeCode());
        current.setRoleCode(req.roleCode());
        // statusCode 미지정 시 기존값 유지.
        current.setStatusCode(
                (req.statusCode() == null || req.statusCode().isBlank())
                        ? current.getStatusCode() : req.statusCode());
        mapper.updateMeta(current);
        return get(id);
    }

    /**
     * DEFINITION_JSON 본문 저장(P3-5b 검증 경유 → P3-6b 저장 엔드포인트 재사용 지점).
     *
     * <p>순서: (1) 화면 존재 확인(없으면 404) → (2) {@link DefinitionValidator}로 구조 검증(위반 시
     * {@link com.jworks.forge.screen.validation.DefinitionValidationException} → 400) → (3) 무가공
     * {@code ::jsonb} 저장(바이트 동등 왕복, 문자열 조립 없음). props의 자유문자열 값은 검증하지 않고
     * 그대로 저장한다(구조만 방어 — 스키마_DEFINITION_JSON.md §5, 표시 이스케이프는 소비자 책임).
     *
     * @param screenId          대상 화면 ID
     * @param definitionJsonRaw 저장할 DEFINITION_JSON 전체(JSON 문자열, 무가공)
     * @return 저장 후 재조회한 화면(DEFINITION_JSON 원문 포함)
     */
    @Transactional
    public ForgeScreen updateDefinition(Long screenId, String definitionJsonRaw) {
        get(screenId); // 존재 확인(폐기 포함 미존재면 404). 검증 실패로 400 나기 전에 존재부터 확인.
        // 구조 검증(화이트리스트 밖이면 DefinitionValidationException → 400). props 값은 무가공 통과.
        definitionValidator.validate(definitionJsonRaw);
        // 검증 통과분만 무가공 저장(::jsonb 캐스팅, #{} 바인딩).
        int affected = mapper.updateDefinition(screenId, definitionJsonRaw);
        if (affected == 0) {
            throw new NotFoundException("screen not found: " + screenId);
        }
        return get(screenId);
    }

    /**
     * 화면 복제 (P7-4). 원본 DEFINITION_JSON의 <b>slots(모듈 배치·props)는 무가공 보존</b>하고,
     * 골격 필드(stem)만 새 값으로 교체한다(archetype/role/schemaVersion 은 원본과 동일 —
     * 슬롯 구조가 아키타입에 종속되므로 아키타입 변경 복제는 지원하지 않는다).
     *
     * <p>안전 조립: 문자열 연결이 아닌 Jackson 트리({@link ObjectNode})로 stem 을 교체하고,
     * 결과 전체를 {@link DefinitionValidator}로 재검증한 뒤 저장한다(§5 쓰기 경계 재통과).
     *
     * @return 생성된 새 화면(단건 재조회)
     */
    @Transactional
    public ForgeScreen duplicate(Long sourceId, ForgeScreenDuplicateRequest req) {
        ForgeScreen src = get(sourceId); // 없으면 404

        // 원본 DEFINITION_JSON 파싱 → stem 교체(ObjectNode.put — 인젝션 배제).
        String newDefinition;
        try {
            var tree = objectMapper.readTree(
                    src.getDefinitionJson() == null ? "{}" : src.getDefinitionJson());
            if (!(tree instanceof ObjectNode root)) {
                throw new DefinitionValidationException(
                        List.of("원본 DEFINITION_JSON 이 객체가 아닙니다."));
            }
            root.put("stem", req.stem());
            newDefinition = objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new DefinitionValidationException(
                    List.of("원본 DEFINITION_JSON 파싱 실패: " + e.getOriginalMessage()));
        }
        // 교체 결과 전체 재검증(구조 화이트리스트 — 위반 시 400).
        definitionValidator.validate(newDefinition);

        ForgeScreen copy = new ForgeScreen();
        copy.setProjectId(src.getProjectId());
        copy.setScreenName(req.screenName());
        copy.setStem(req.stem());
        copy.setArchetypeCode(src.getArchetypeCode());
        copy.setRoleCode(src.getRoleCode());
        copy.setStatusCode("DRAFT");
        copy.setDefinitionJson(newDefinition);
        mapper.insert(copy);
        return get(copy.getScreenId());
    }

    @Transactional
    public void delete(Long id) {
        int affected = mapper.softDelete(id);
        if (affected == 0) {
            throw new NotFoundException("screen not found: " + id);
        }
    }

    /**
     * 메타(archetype/role) 공통코드 실존 검증(§5.1 쓰기 경계). DEFINITION 본문 검증과 동일한
     * {@link DefinitionValidator#isCodeInGroup}(codeExists) 경로를 재사용한다(중복 구현 금지).
     * 정상 케이스(role=admin/user, archetype=MGMT_LIST_DETAIL 등 실존 코드)는 통과한다.
     * 위반 시 {@link DefinitionValidationException}(→400).
     */
    private void validateMetaCodes(ForgeScreenRequest req) {
        List<String> violations = new java.util.ArrayList<>();
        if (!definitionValidator.isCodeInGroup("ARCHETYPE", req.archetypeCode())) {
            violations.add("archetypeCode '" + req.archetypeCode()
                    + "'는 ARCHETYPE 공통코드에 존재하지 않습니다.");
        }
        if (!definitionValidator.isCodeInGroup("ROLE", req.roleCode())) {
            violations.add("roleCode '" + req.roleCode()
                    + "'는 ROLE 공통코드에 존재하지 않습니다.");
        }
        if (!violations.isEmpty()) {
            throw new DefinitionValidationException(violations);
        }
    }

    /**
     * 생성 시 초기 DEFINITION_JSON 최소 골격(스키마_DEFINITION_JSON.md §1).
     * 사용자 입력(archetype/stem/role)은 이미 화이트리스트/정규식을 통과했으나,
     * 여기서는 문자열 연결이 아닌 ObjectNode.put 으로 값을 안전하게 실어 인젝션을 배제한다.
     */
    private String buildInitialDefinition(ForgeScreenRequest req) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("archetype", req.archetypeCode());
        root.put("stem", req.stem());
        root.put("role", req.roleCode());
        root.set("slots", objectMapper.createObjectNode());
        // P13(§17.2): 자유 배치 화면은 시트 크기를 골격에 함께 넣는다 — 캔버스가 첫 렌더부터
        // 확정된 폭·높이를 갖도록(값은 상수이며 사용자 입력이 아니다).
        if ("FREE_CANVAS".equals(req.archetypeCode())) {
            ObjectNode canvas = objectMapper.createObjectNode();
            canvas.put("widthPx", 1280);
            canvas.put("heightPx", 800);
            root.set("canvas", canvas);
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            // ObjectNode 직렬화 실패는 사실상 발생 불가(방어적 처리).
            throw new IllegalStateException("초기 DEFINITION_JSON 직렬화 실패", e);
        }
    }
}
