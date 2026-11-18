package com.walpro.autobuilder.core.build.inject;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Withheld specification for T1-GT (T4-EZ forward case). Asserts only what the ticket text states. */
class SkeletonPlacementSpecTest {

    private static final String NESTED_DIAGNOSTIC = """
            [ERROR] /p/src/test/java/com/acme/stuck/StuckLedgerPairsTest.java:[25,25] cannot find symbol
            [ERROR]   symbol:   class AlternatingPair
            [ERROR]   location: class com.acme.stuck.StuckLedger
            """;

    private static Map<String, String> ledgerProject() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("src/main/java/com/acme/stuck/StuckLedger.java",
                "package com.acme.stuck;\npublic class StuckLedger {\n}\n");
        sources.put("src/test/java/com/acme/stuck/StuckLedgerPairsTest.java",
                "package com.acme.stuck;\nclass StuckLedgerPairsTest {\n"
                        + "  java.util.List<StuckLedger.AlternatingPair> pairs;\n}\n");
        return sources;
    }

    // (1) a member reference

    @Test
    void aNameReachedThroughItsLocationClassGetsNoTopLevelSkeleton() {
        assertThat(SkeletonPlacement.packageFor("AlternatingPair", NESTED_DIAGNOSTIC, ledgerProject(), "com.acme"))
                .isEmpty();
    }

    @Test
    void aMemberReferenceFromMainCodeGetsNoTopLevelSkeletonEither() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("src/main/java/com/acme/stuck/StuckLedger.java",
                "package com.acme.stuck;\npublic class StuckLedger {\n}\n");
        sources.put("src/main/java/com/acme/stuck/Report.java",
                "package com.acme.stuck;\nclass Report { StuckLedger.AlternatingPair pair; }\n");
        String diagnostic = """
                [ERROR] /p/src/main/java/com/acme/stuck/Report.java:[2,27] cannot find symbol
                [ERROR]   symbol:   class AlternatingPair
                [ERROR]   location: class com.acme.stuck.StuckLedger
                """;

        assertThat(SkeletonPlacement.packageFor("AlternatingPair", diagnostic, sources, "com.acme")).isEmpty();
    }

    @Test
    void aLocationClassThatUsesTheNameUnqualifiedKeepsTheAnswer() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("src/main/java/com/acme/member/MemberService.java",
                "package com.acme.member;\nclass MemberService { Loan loan; }\n");
        String diagnostic = """
                [ERROR] /p/src/main/java/com/acme/member/MemberService.java:[2,23] cannot find symbol
                [ERROR]   symbol:   class Loan
                [ERROR]   location: class com.acme.member.MemberService
                """;

        assertThat(SkeletonPlacement.packageFor("Loan", diagnostic, sources, "com.acme")).contains("com.acme.member");
    }

    @Test
    void eachLocationBelongsToTheSymbolLineBeforeIt() {
        Map<String, String> sources = ledgerProject();
        sources.put("src/main/java/com/acme/member/MemberService.java",
                "package com.acme.member;\nclass MemberService { Loan loan; }\n");
        String diagnostic = """
                [ERROR] /p/src/test/java/com/acme/stuck/StuckLedgerPairsTest.java:[3,23] cannot find symbol
                [ERROR]   symbol:   class AlternatingPair
                [ERROR]   location: class com.acme.stuck.StuckLedger
                [ERROR] /p/src/main/java/com/acme/member/MemberService.java:[2,23] cannot find symbol
                [ERROR]   symbol:   class Loan
                [ERROR]   location: class com.acme.member.MemberService
                """;

        assertThat(SkeletonPlacement.packageFor("AlternatingPair", diagnostic, sources, "com.acme")).isEmpty();
        assertThat(SkeletonPlacement.packageFor("Loan", diagnostic, sources, "com.acme")).contains("com.acme.member");
    }

    @Test
    void aSourceWritingTheNameThroughAnotherClassIsNoMemberReferenceForThisLocation() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("src/main/java/com/acme/member/MemberService.java",
                "package com.acme.member;\nclass MemberService { Loan loan; Archive.Loan old; }\n");
        String diagnostic = """
                [ERROR] /p/src/main/java/com/acme/member/MemberService.java:[2,23] cannot find symbol
                [ERROR]   symbol:   class Loan
                [ERROR]   location: class com.acme.member.MemberService
                """;

        assertThat(SkeletonPlacement.packageFor("Loan", diagnostic, sources, "com.acme")).contains("com.acme.member");
    }

    // (2) test sources

    @Test
    void anImportInATestSourceNamesThePackage() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("src/test/java/com/acme/desk/DeskTest.java",
                "package com.acme.desk;\nimport com.acme.loan.Loan;\nclass DeskTest { Loan loan; }\n");

        assertThat(SkeletonPlacement.packageFor("Loan", "symbol:   class Loan", sources, "com.acme.model"))
                .contains("com.acme.loan");
    }

    @Test
    void anUnqualifiedUseInATestSourceNamesItsPackage() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("src/test/java/com/acme/desk/DeskTest.java",
                "package com.acme.desk;\nclass DeskTest { Loan loan; }\n");

        assertThat(SkeletonPlacement.packageFor("Loan", "symbol:   class Loan", sources, "com.acme.model"))
                .contains("com.acme.desk");
    }

    @Test
    void importsInMainAndTestSourcesAreCountedTogether() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("src/main/java/com/acme/a/A.java", "package com.acme.a;\nimport com.acme.x.Loan;\nclass A {}\n");
        sources.put("src/test/java/com/acme/b/BTest.java", "package com.acme.b;\nimport com.acme.y.Loan;\nclass BTest {}\n");
        sources.put("src/test/java/com/acme/c/CTest.java", "package com.acme.c;\nimport com.acme.y.Loan;\nclass CTest {}\n");

        assertThat(SkeletonPlacement.packageFor("Loan", "symbol:   class Loan", sources, "com.acme"))
                .contains("com.acme.y");
    }

    @Test
    void anImportInATestSourceStillBeatsAnUnqualifiedUseInMainCode() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("src/main/java/com/acme/member/MemberService.java",
                "package com.acme.member;\nclass MemberService { Loan a; Loan b; }\n");
        sources.put("src/test/java/com/acme/desk/DeskTest.java",
                "package com.acme.desk;\nimport com.acme.loan.Loan;\nclass DeskTest { Loan loan; }\n");

        assertThat(SkeletonPlacement.packageFor("Loan", "symbol:   class Loan", sources, "com.acme"))
                .contains("com.acme.loan");
    }

    // what stays

    @Test
    void withNoReferenceTheConventionPackageIsStillUsed() {
        assertThat(SkeletonPlacement.packageFor("Loan", "symbol:   class Loan", Map.of(), "com.acme.model"))
                .contains("com.acme.model");
    }

    @Test
    void aTestFileNamedAfterTheMissingClassIsNoReferenceToIt() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("src/test/java/com/acme/stale/Loan.java", "package com.acme.stale;\nclass Loan { Loan next; }\n");

        assertThat(SkeletonPlacement.packageFor("Loan", "symbol:   class Loan", sources, "com.acme.model"))
                .contains("com.acme.model");
    }
}
