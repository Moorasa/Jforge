package com.jworks.forge.gen.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 🔒 P12(계약 §16.4): 보호구역 안의 사람 코드가 재생성에서 살아남는지 고정한다. */
class CustomRegionMergerTest {

    private static final String GENERATED = """
            package app;

            public class Foo {

                // 생성기 소관(항상 갱신)
                void generated() { }

                // <j-forge:custom id="body">
                // 이 사이에 쓴 코드는 재생성해도 보존됩니다.
                // </j-forge:custom>
            }
            """;

    @Test
    void 사람이_쓴_코드는_보존되고_생성구간은_갱신된다() {
        String existing = """
                package app;

                public class Foo {

                    // 옛 생성물(사라져야 함)
                    void old() { }

                    // <j-forge:custom id="body">
                    void myBusinessLogic() {
                        System.out.println("직접 짠 코드");
                    }
                    // </j-forge:custom>
                }
                """;

        String merged = CustomRegionMerger.merge(GENERATED, existing);

        assertTrue(merged.contains("myBusinessLogic"), "사람 코드 보존");
        assertTrue(merged.contains("직접 짠 코드"));
        assertTrue(merged.contains("void generated()"), "생성 구간은 새 내용으로 갱신");
        assertFalse(merged.contains("void old()"), "옛 생성 구간은 사라진다");
        assertFalse(merged.contains("이 사이에 쓴 코드는"), "안내 문구는 사람 코드로 대체됨");
    }

    @Test
    void 사람이_손대지_않았으면_생성물과_바이트가_같다() {
        // 1회차 산출을 그대로 기존 파일로 두고 재생성 → 완전히 동일해야 한다(멱등).
        assertEquals(GENERATED, CustomRegionMerger.merge(GENERATED, GENERATED));
    }

    @Test
    void 기존_파일이_없으면_생성물_그대로다() {
        assertEquals(GENERATED, CustomRegionMerger.merge(GENERATED, null));
        assertEquals(GENERATED, CustomRegionMerger.merge(GENERATED, ""));
    }

    /** 새 템플릿에서 사라진 구역의 옛 내용은 옮기지 않는다(유령 코드 삽입 방지). */
    @Test
    void 새_내용에_없는_구역은_옮기지_않는다() {
        String existing = """
                public class Foo {
                    // <j-forge:custom id="gone">
                    void ghost() { }
                    // </j-forge:custom>
                }
                """;

        String merged = CustomRegionMerger.merge(GENERATED, existing);

        assertFalse(merged.contains("ghost"), "새 내용에 같은 id가 없으면 삽입하지 않는다");
        assertEquals(GENERATED, merged);
    }

    /** 🔒 미종료 구역은 병합하지 않고 새 내용으로 수렴한다(실패는 안전측으로). */
    @Test
    void 종료마커가_없으면_병합을_생략한다() {
        String broken = """
                public class Foo {
                    // <j-forge:custom id="body">
                    void unterminated() { }
                }
                """;

        assertEquals(GENERATED, CustomRegionMerger.merge(GENERATED, broken));
    }

    @Test
    void 중첩_구역은_병합을_생략한다() {
        String nested = """
                public class Foo {
                    // <j-forge:custom id="body">
                    // <j-forge:custom id="inner">
                    void x() { }
                    // </j-forge:custom>
                    // </j-forge:custom>
                }
                """;

        assertEquals(GENERATED, CustomRegionMerger.merge(GENERATED, nested));
    }

    /** XML 주석 문법에서도 같은 마커가 동작한다(Mapper XML). */
    @Test
    void XML_주석_마커도_인식한다() {
        String generatedXml = """
                <mapper namespace="app.FooMapper">
                    <!-- <j-forge:custom id="statements"> -->
                    <!-- </j-forge:custom> -->
                </mapper>
                """;
        String existingXml = """
                <mapper namespace="app.FooMapper">
                    <!-- <j-forge:custom id="statements"> -->
                    <select id="myOwn" resultType="map">SELECT 1</select>
                    <!-- </j-forge:custom> -->
                </mapper>
                """;

        String merged = CustomRegionMerger.merge(generatedXml, existingXml);

        assertTrue(merged.contains("myOwn"), "XML 보호구역도 보존");
    }
}
