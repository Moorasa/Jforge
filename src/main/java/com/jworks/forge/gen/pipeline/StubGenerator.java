package com.jworks.forge.gen.pipeline;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.jworks.forge.gen.context.ServerBinding;
import com.jworks.forge.gen.safety.PathSafetyException;
import com.jworks.forge.gen.safety.PathSafetyService;

/**
 * 🔒 stub 폴더/파일 생성 (P4-6, 기획서 10장 "3종+stub"). <b>P4 보안 코어의 일부 — reviewer 🔒 검수 대상.</b>
 *
 * <p>Controller/Mapper 위치에 <b>빈 stub 골격</b>을 생성한다: {@code {Stem}Controller.java},
 * {@code {Stem}Mapper.java}, {@code {Stem}Mapper.xml}. 실 로직·JWORKS 배너 없이 패키지 선언 +
 * 클래스/매퍼 골격 + TODO 주석만 담는다(수작업 채움 지점을 명시).
 *
 * <p>보안 요지(계약 §1.2/§4.3):
 * <ul>
 *   <li><b>경로/파일명에 자유문자열 0</b>. 패키지 폴더는 <b>{@link TemplateContextBuilder}에서
 *       앵커드 정규식으로 검증된 packageBase</b>의 점→슬래시 변환값 + 정적 세그먼트 + 검증된 stem만으로
 *       조립한다. props값·label·instanceId 등은 경로/파일명·본문 어디에도 넣지 않는다.</li>
 *   <li><b>모든 타겟 쓰기는 {@link PathSafetyService#resolveSafeWritePath} + {@link AtomicFileWriter}
 *       경유</b>(예외 0). 확장자는 {@code java}/{@code xml} — 화이트리스트 내.</li>
 *   <li>stem 첫 글자 대문자화({@code Stem})는 검증 통과 stem(정규식 {@code ^[a-z][a-zA-Z0-9]*$})에서만
 *       유도되므로 안전하다(특수문자/공백 불가).</li>
 * </ul>
 *
 * <p>packageBase의 정규식 검증은 {@link TemplateContextBuilder#PACKAGE_BASE_REGEX}에서 수행하며,
 * 이 클래스는 model에 담긴 <b>이미 검증된</b> packageBase만 소비한다(2차 방어는 PathSafetyService).
 *
 * <p><b>P10(계약 §14) 승격</b>: 화면 설계에 서버 바인딩({@code data.table} 등)이 선언돼 있으면
 * {@link ServerBinding#resolve}가 게이트를 통과시킨 값으로 <b>빈 골격 대신 실제 조회 API</b>
 * (Controller/Mapper/XML)를 산출한다. 바인딩이 없거나 게이트를 통과하지 못하면 <b>기존 빈 stub을
 * 바이트 그대로</b> 산출한다(골든 무손상). <b>경로·파일명·아티팩트 키는 어느 경우에도 불변</b>이므로
 * {@link GenPlanner} dry-run 계획도 영향을 받지 않는다(계약 §14.3).
 */
@Component
public class StubGenerator {

    private static final Logger log = LoggerFactory.getLogger(StubGenerator.class);

    /** Java 소스 루트(타겟 프로젝트 규약, 정적 세그먼트). */
    private static final String JAVA_SRC_ROOT = "src/main/java";
    /** Mapper XML 루트(타겟 프로젝트 규약, 정적 세그먼트). */
    private static final String MAPPER_XML_ROOT = "src/main/resources/mapper";

    private final PathSafetyService pathSafetyService;
    private final AtomicFileWriter fileWriter;

    public StubGenerator(PathSafetyService pathSafetyService, AtomicFileWriter fileWriter) {
        this.pathSafetyService = pathSafetyService;
        this.fileWriter = fileWriter;
    }

    /**
     * Controller/Mapper stub 3종을 타겟 루트 하위에 생성한다.
     *
     * @param packageBase   <b>검증 통과</b> packageBase(예: {@code com.jworks.forge}) — model 값
     * @param stem          <b>검증 통과</b> stem(예: {@code userMgmt}) — model 값
     * @param targetRoot    프로젝트 TARGET_ROOT_PATH
     * @param canonicalRoot {@code targetRoot.toRealPath()}(쓰기 직전 재확인 앵커, 계약 §5.1)
     * @return 파일 단위 결과(stub 3종)
     */
    public List<GenFile> generateStubs(
            String packageBase, String stem, Path targetRoot, Path canonicalRoot) {
        // 모델 없이 호출되면 서버 바인딩 해석 대상이 없다 → 종전과 동일한 빈 stub 3종.
        return generateStubs(packageBase, stem, targetRoot, canonicalRoot, null);
    }

    /**
     * Controller/Mapper stub 3종을 생성하되, 화면 설계에 서버 바인딩이 선언돼 있으면
     * <b>실제 조회 API로 승격</b>해 산출한다(P10, 계약 §14).
     *
     * @param model {@link com.jworks.forge.gen.context.TemplateContextBuilder#build} 렌더 모델
     *              (null이면 승격 없이 빈 stub). 이 클래스는 model을 <b>읽기만</b> 한다.
     */
    public List<GenFile> generateStubs(
            String packageBase, String stem, Path targetRoot, Path canonicalRoot,
            Map<String, Object> model) {

        String Stem = capitalize(stem);
        String stubPackage = packageBase + "." + stem;

        // 🔒 게이트 단일 소스. 미통과 = empty = 기존 빈 stub 폴백(실패가 안전측으로 수렴).
        Optional<ServerBinding> binding = ServerBinding.resolve(model);
        if (binding.isPresent()) {
            log.info("[Stub] 서버 바인딩 승격 — stem={} table={} endpoint={} columns={}",
                    stem, binding.get().table(), binding.get().endpoint(), binding.get().columns().size());
        }

        // 경로 계획은 planStubs 와 물리적 단일 소스(P7-4: dry-run plan 과 실산출 경로가 어긋날 수 없다).
        // 승격 여부는 파일 '내용'만 바꾸므로 이 계획(경로 목록)에는 영향이 없다(계약 §14.3).
        List<StubPlanEntry> plan = planStubs(packageBase, stem);
        List<GenFile> results = new ArrayList<>(plan.size());
        for (StubPlanEntry entry : plan) {
            String content = contentFor(entry.artifactKey(), stubPackage, Stem, binding);
            results.add(writeStub(entry.artifactKey(), entry.relativePath(),
                    content, targetRoot, canonicalRoot));
        }
        return results;
    }

    /**
     * 아티팩트 1건의 <b>생성 예정 내용</b>을 만든다(P12 diff 미리보기용 — <b>쓰기 0</b>).
     * 실산출과 물리적 단일 소스이므로 미리보기와 실제 결과가 어긋날 수 없다.
     */
    public String contentFor(String artifactKey, String packageBase, String stem,
                             Map<String, Object> model) {
        return contentFor(artifactKey, packageBase + "." + stem, capitalize(stem),
                ServerBinding.resolve(model));
    }

    private String contentFor(String artifactKey, String stubPackage, String Stem,
                              Optional<ServerBinding> binding) {
        return switch (artifactKey) {
            case "stubController" -> binding
                    .map(b -> controllerCode(stubPackage, Stem, b))
                    .orElseGet(() -> controllerStub(stubPackage, Stem));
            case "stubMapper" -> binding
                    .map(b -> mapperCode(stubPackage, Stem, b))
                    .orElseGet(() -> mapperStub(stubPackage, Stem));
            default -> binding
                    .map(b -> mapperXmlCode(stubPackage, Stem, b))
                    .orElseGet(() -> mapperXmlStub(stubPackage, Stem));
        };
    }

    /**
     * stub 1건의 경로 계획(정적 키 + 상대경로). <b>파일쓰기 없음</b> — P7-4 dry-run(GenPlanner)과
     * {@link #generateStubs}가 함께 소비하는 경로 단일 소스다.
     */
    public record StubPlanEntry(String artifactKey, String relativePath) {
    }

    /**
     * stub 3종의 상대경로 계획(P7-4, <b>읽기전용 — 쓰기 0</b>).
     *
     * <p>🔒 경로 규칙은 기존 {@code generateStubs} 조립식 그대로: 정적 세그먼트 + <b>검증 통과</b>
     * packageBase(점→슬래시) + <b>검증 통과</b> stem 만. 자유문자열 0.
     */
    public List<StubPlanEntry> planStubs(String packageBase, String stem) {
        // 🔒 검증된 packageBase의 점→슬래시 변환(정규식이 점/소문자영숫자만 허용 → 슬래시 결과 안전).
        //    stub 패키지는 {packageBase}.{stem} — 폴더 경로는 {packagePath}/{stem}.
        String packagePath = packageBase.replace('.', '/');
        String Stem = capitalize(stem);
        return List.of(
                // Controller stub: {javaSrc}/{packagePath}/{stem}/{Stem}Controller.java
                new StubPlanEntry("stubController",
                        JAVA_SRC_ROOT + "/" + packagePath + "/" + stem + "/" + Stem + "Controller.java"),
                // Mapper interface stub: {javaSrc}/{packagePath}/{stem}/{Stem}Mapper.java
                new StubPlanEntry("stubMapper",
                        JAVA_SRC_ROOT + "/" + packagePath + "/" + stem + "/" + Stem + "Mapper.java"),
                // Mapper XML stub: {mapperXml}/{stem}/{Stem}Mapper.xml
                new StubPlanEntry("stubMapperXml",
                        MAPPER_XML_ROOT + "/" + stem + "/" + Stem + "Mapper.xml"));
    }

    /** stub 1건: resolveSafeWritePath + (P12 보호구역 병합) + 원자적 쓰기. 개별 실패 포착(계약 §5.3). */
    private GenFile writeStub(String artifactKey, String rel, String content,
                              Path targetRoot, Path canonicalRoot) {
        try {
            // 🔒 정적 세그먼트 + 검증된 packageBase/stem만으로 조립된 rel도 경로안전 계층을 통과시킨다.
            Path safeAbs = pathSafetyService.resolveSafeWritePath(targetRoot, rel);
            // P12(계약 §16): 기존 파일의 보호구역(사람이 채운 코드)을 새 내용으로 옮겨 담는다.
            //   - 읽기 대상은 방금 경로안전을 통과한 그 경로뿐이다(임의 경로 읽기 없음).
            //   - 읽기/병합 실패는 "새 내용 그대로"로 수렴한다(생성 실패로 번지지 않음).
            String merged = CustomRegionMerger.merge(content, readIfExists(safeAbs));
            fileWriter.write(safeAbs, canonicalRoot, merged);
            return GenFile.ok(artifactKey, rel, ContentHash.sha256(merged));
        } catch (PathSafetyException e) {
            log.error("[Stub] 경로안전 위반 — artifact={} rel={} : {}", artifactKey, rel, e.getMessage());
            return GenFile.fail(artifactKey, rel, "경로안전 위반: " + e.getMessage());
        } catch (RuntimeException e) {
            log.error("[Stub] stub 생성 실패 — artifact={} rel={} : {}", artifactKey, rel, e.getMessage());
            return GenFile.fail(artifactKey, rel, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** 보호구역 병합용 기존 내용 읽기(P12). 없거나 과대하거나 읽기 실패면 null → 병합 생략. */
    private String readIfExists(Path safeAbsPath) {
        try {
            if (!java.nio.file.Files.isRegularFile(safeAbsPath, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            if (java.nio.file.Files.size(safeAbsPath) > MAX_MERGE_READ_BYTES) {
                log.warn("[Stub] 보호구역 병합 생략(파일 과대) — {}", safeAbsPath.getFileName());
                return null;
            }
            return java.nio.file.Files.readString(safeAbsPath, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[Stub] 기존 파일 읽기 실패 — 병합 생략: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    /** 보호구역 병합을 위해 읽어들일 최대 크기(1MB). 초과분은 병합 없이 덮어쓴다(백업은 남는다). */
    private static final long MAX_MERGE_READ_BYTES = 1024L * 1024L;

    /**
     * 보호구역 마커 블록(P12, 계약 §16). 재생성 시 이 사이의 내용은 보존된다.
     *
     * @param indent 들여쓰기(정적)
     * @param commentOpen 주석 시작(자바 {@code //}, XML {@code <!--})
     * @param commentClose 주석 끝(자바 빈 문자열, XML {@code -->})
     * @param id 구역 id(정적 리터럴)
     */
    private static String customRegion(String indent, String commentOpen, String commentClose, String id) {
        String close = commentClose.isEmpty() ? "" : " " + commentClose;
        return indent + commentOpen + " <j-forge:custom id=\"" + id + "\">" + close + "\n"
                + indent + commentOpen + " 이 사이에 쓴 코드는 재생성해도 보존됩니다." + close + "\n"
                + indent + commentOpen + " </j-forge:custom>" + close + "\n";
    }

    /** 검증 통과 stem의 첫 글자만 대문자화(특수문자/공백 불가라 안전). */
    private static String capitalize(String stem) {
        return Character.toUpperCase(stem.charAt(0)) + stem.substring(1);
    }

    /** Controller 빈 골격(패키지 선언 + 클래스 + TODO 주석). 실 로직·배너 0. */
    private String controllerStub(String pkg, String stem) {
        return "package " + pkg + ";\n"
                + "\n"
                + "// TODO(J-FORGE stub): " + stem + " 화면 컨트롤러 — 실 로직은 수작업으로 채운다.\n"
                + "// (요청 매핑/서비스 주입/뷰 반환 등은 생성 후 개발자가 구현)\n"
                + "public class " + stem + "Controller {\n"
                + "\n"
                + "    // TODO: @RequestMapping 핸들러 추가\n"
                + "\n"
                + customRegion("    ", "//", "", "body")
                + "}\n";
    }

    /** Mapper 인터페이스 빈 골격(패키지 선언 + 인터페이스 + TODO 주석). 실 로직·배너 0. */
    private String mapperStub(String pkg, String stem) {
        return "package " + pkg + ";\n"
                + "\n"
                + "// TODO(J-FORGE stub): " + stem + " 매퍼 인터페이스 — 실 메서드는 수작업으로 채운다.\n"
                + "// (SELECT * 금지, 컬럼 명시, #{} 바인딩 규약 준수)\n"
                + "public interface " + stem + "Mapper {\n"
                + "\n"
                + "    // TODO: 매퍼 메서드 시그니처 추가\n"
                + "\n"
                + customRegion("    ", "//", "", "methods")
                + "}\n";
    }

    /** Mapper XML 빈 골격(DOCTYPE + 빈 mapper 요소 + TODO 주석). 실 SQL·배너 0. */
    private String mapperXmlStub(String pkg, String stem) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\"\n"
                + "    \"https://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n"
                + "<!-- TODO(J-FORGE stub): " + stem + " 매퍼 XML — 실 SQL은 수작업으로 채운다.\n"
                + "     (SELECT * 금지 · 컬럼 명시 · #{} 바인딩 · ${} 금지 규약 준수) -->\n"
                + "<mapper namespace=\"" + pkg + "." + stem + "Mapper\">\n"
                + "\n"
                + "    <!-- TODO: <select>/<insert>/<update>/<delete> 추가 -->\n"
                + "\n"
                + customRegion("    ", "<!--", "-->", "statements")
                + "</mapper>\n";
    }

    // =================================================================================
    // P10(계약 §14) 승격 산출 — 아래 3개 메서드가 코드에 삽입하는 값은 전부
    // ServerBinding 게이트(화이트리스트 정규식)를 통과한 원본이다. 자유문자열 삽입 0.
    // =================================================================================

    /** 승격 Controller: 목록(+ keyColumn이 있으면 단건) 조회 API. */
    private String controllerCode(String pkg, String Stem, ServerBinding b) {
        boolean post = "POST".equals(b.method());
        StringBuilder sb = new StringBuilder(1400);
        sb.append("package ").append(pkg).append(";\n\n");
        if (b.wrapsResult()) {
            sb.append("import java.util.LinkedHashMap;\n");
        }
        sb.append("import java.util.List;\n")
          .append("import java.util.Map;\n\n");
        if (!post || b.hasKeyColumn()) {
            sb.append("import org.springframework.web.bind.annotation.GetMapping;\n");
        }
        if (b.hasKeyColumn()) {
            sb.append("import org.springframework.web.bind.annotation.PathVariable;\n");
        }
        if (post) {
            sb.append("import org.springframework.web.bind.annotation.PostMapping;\n");
        }
        sb.append("import org.springframework.web.bind.annotation.RequestMapping;\n")
          .append("import org.springframework.web.bind.annotation.RestController;\n\n");

        sb.append("// J-FORGE 생성물(계약 §14) — 화면 설계의 data 선언에서 산출한 조회 API.\n")
          .append("// ⚠ 재생성 시 덮어쓰기 대상이다(사람이 채운 로직은 별도 클래스로 분리할 것).\n")
          .append("@RestController\n")
          .append("@RequestMapping(\"").append(b.endpoint()).append("\")\n")
          .append("public class ").append(Stem).append("Controller {\n\n")
          .append("    private final ").append(Stem).append("Mapper mapper;\n\n")
          .append("    public ").append(Stem).append("Controller(")
          .append(Stem).append("Mapper mapper) {\n")
          .append("        this.mapper = mapper;\n")
          .append("    }\n\n");

        // 목록 — 응답 형태는 생성 화면 런타임(design.js valueAtPath)의 resultPath와 정합(§14.3).
        sb.append("    /** 목록 조회. 응답 형태는 생성 화면 런타임(resultPath)과 정합한다. */\n")
          .append("    @").append(post ? "PostMapping" : "GetMapping").append("\n");
        if (b.wrapsResult()) {
            sb.append("    public Map<String, Object> list() {\n")
              .append("        List<Map<String, Object>> rows = mapper.selectList();\n")
              .append("        Map<String, Object> body = new LinkedHashMap<>();\n")
              .append("        body.put(\"").append(b.resultPath()).append("\", rows);\n")
              .append("        return body;\n")
              .append("    }\n");
        } else {
            sb.append("    public List<Map<String, Object>> list() {\n")
              .append("        return mapper.selectList();\n")
              .append("    }\n");
        }

        if (b.hasKeyColumn()) {
            sb.append("\n    /** 단건 조회 — ").append(b.keyColumn()).append(" 기준. */\n")
              .append("    @GetMapping(\"/{key}\")\n")
              .append("    public Map<String, Object> detail(@PathVariable(\"key\") String key) {\n")
              .append("        return mapper.selectOne(key);\n")
              .append("    }\n");
        }
        // P12: 사람이 채우는 자리(재생성 시 보존).
        sb.append("\n").append(customRegion("    ", "//", "", "body"));
        sb.append("}\n");
        return sb.toString();
    }

    /** 승격 Mapper 인터페이스: selectList(+ selectOne). 타겟 DTO 규약에 독립적인 Map 반환. */
    private String mapperCode(String pkg, String Stem, ServerBinding b) {
        StringBuilder sb = new StringBuilder(700);
        sb.append("package ").append(pkg).append(";\n\n")
          .append("import java.util.List;\n")
          .append("import java.util.Map;\n\n")
          .append("import org.apache.ibatis.annotations.Mapper;\n");
        if (b.hasKeyColumn()) {
            sb.append("import org.apache.ibatis.annotations.Param;\n");
        }
        sb.append("\n")
          .append("// J-FORGE 생성물(계약 §14). SQL은 같은 이름의 Mapper XML에 있다.\n")
          .append("@Mapper\n")
          .append("public interface ").append(Stem).append("Mapper {\n\n")
          .append("    /** ").append(b.table()).append(" 목록 조회. */\n")
          .append("    List<Map<String, Object>> selectList();\n");
        if (b.hasKeyColumn()) {
            sb.append("\n    /** ").append(b.table()).append(" 단건 조회(")
              .append(b.keyColumn()).append("). */\n")
              .append("    Map<String, Object> selectOne(@Param(\"key\") String key);\n");
        }
        sb.append("\n").append(customRegion("    ", "//", "", "methods"));
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * 승격 Mapper XML: 컬럼 명시({@code SELECT *} 금지) + {@code AS "COL"} 별칭 고정 + {@code #{}} 바인딩.
     * 별칭은 대소문자 폴딩을 막아 <b>응답 키 == columns[].name == 테이블 헤더 data-name</b>을 성립시킨다(§14.3).
     */
    private String mapperXmlCode(String pkg, String Stem, ServerBinding b) {
        String columnList = selectColumns(b);
        StringBuilder sb = new StringBuilder(900);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
          .append("<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\"\n")
          .append("    \"https://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n")
          .append("<!-- J-FORGE 생성물(계약 §14): 컬럼 명시 · 별칭 고정(응답 키 = 화면 컬럼명) · #{} 바인딩. -->\n")
          .append("<mapper namespace=\"").append(pkg).append(".").append(Stem).append("Mapper\">\n\n")
          .append("    <select id=\"selectList\" resultType=\"map\">\n")
          .append("        SELECT\n")
          .append(columnList)
          .append("        FROM ").append(b.table()).append("\n")
          .append("    </select>\n");
        if (b.hasKeyColumn()) {
            sb.append("\n    <select id=\"selectOne\" parameterType=\"string\" resultType=\"map\">\n")
              .append("        SELECT\n")
              .append(columnList)
              .append("        FROM ").append(b.table()).append("\n")
              .append("        WHERE ").append(b.keyColumn()).append(" = #{key}\n")
              .append("    </select>\n");
        }
        sb.append("\n").append(customRegion("    ", "<!--", "-->", "statements"));
        sb.append("</mapper>\n");
        return sb.toString();
    }

    /** {@code COL AS "COL",} 목록(마지막 줄 콤마 없음). 컬럼은 게이트 통과 식별자뿐. */
    private String selectColumns(ServerBinding b) {
        StringBuilder sb = new StringBuilder(128);
        List<String> columns = b.columns();
        for (int i = 0; i < columns.size(); i++) {
            String col = columns.get(i);
            sb.append("            ").append(col).append(" AS \"").append(col).append('"');
            sb.append(i < columns.size() - 1 ? ",\n" : "\n");
        }
        return sb.toString();
    }
}
