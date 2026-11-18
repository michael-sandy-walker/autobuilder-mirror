package com.walpro.autobuilder.core.build.inject;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SkeletonPlacementMemberAndTestSourceTest {

    private static final String MEMBER_DIAGNOSTIC = """
            [ERROR] /p/src/test/java/com/acme/stuck/StuckLedgerPairsTest.java:[25,25] cannot find symbol
            [ERROR]   symbol:   class AlternatingPair
            [ERROR]   location: class com.acme.stuck.StuckLedger
            """;

    private static final String UNQUALIFIED_DIAGNOSTIC = """
            [ERROR] /p/src/main/java/com/acme/member/MemberService.java:[12,9] cannot find symbol
            [ERROR]   symbol:   class Loan
            [ERROR]   location: class com.acme.member.MemberService
            """;

    @Test
    void aQualifiedUseThroughTheLocationClassMeansNoTopLevelSkeleton() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("src/main/java/com/acme/stuck/StuckLedger.java",
                "package com.acme.stuck;\nclass StuckLedger { AlternatingPair p; }\n");
        sources.put("src/test/java/com/acme/stuck/StuckLedgerPairsTest.java",
                "package com.acme.stuck;\nclass StuckLedgerPairsTest { StuckLedger.AlternatingPair p; }\n");

        assertThat(SkeletonPlacement.packageFor("AlternatingPair", MEMBER_DIAGNOSTIC, sources, "com.acme.model"))
                .isEmpty();
    }

    @Test
    void anUnqualifiedUseWithTheSameLocationStillYieldsTheLocationPackage() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("src/main/java/com/acme/stuck/StuckLedger.java",
                "package com.acme.stuck;\nclass StuckLedger { AlternatingPair p; }\n");

        assertThat(SkeletonPlacement.packageFor("AlternatingPair", MEMBER_DIAGNOSTIC, sources, "com.acme.model"))
                .contains("com.acme.stuck");
    }

    @Test
    void aTestSourceImportNamesThePackage() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("src/test/java/com/acme/stuck/StuckLedgerPairsTest.java",
                "package com.acme.stuck;\nimport com.acme.loan.Loan;\nclass StuckLedgerPairsTest { Loan l; }\n");

        assertThat(SkeletonPlacement.packageFor("Loan", UNQUALIFIED_DIAGNOSTIC, sources, "com.acme"))
                .contains("com.acme.loan");
    }

    @Test
    void aTestSourceUnqualifiedUseNamesItsOwnPackage() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("src/test/java/com/acme/stuck/StuckLedgerPairsTest.java",
                "package com.acme.stuck;\nclass StuckLedgerPairsTest { Loan l; }\n");

        assertThat(SkeletonPlacement.packageFor("Loan", UNQUALIFIED_DIAGNOSTIC, sources, "com.acme"))
                .contains("com.acme.stuck");
    }

    @Test
    void testSourceImportBeatsMainSourceUnqualifiedUse() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("src/main/java/com/acme/member/MemberService.java",
                "package com.acme.member;\nclass MemberService { Loan l; }\n");
        sources.put("src/test/java/com/acme/stuck/StuckLedgerPairsTest.java",
                "package com.acme.stuck;\nimport com.acme.loan.Loan;\nclass StuckLedgerPairsTest { Loan l; }\n");

        assertThat(SkeletonPlacement.packageFor("Loan", UNQUALIFIED_DIAGNOSTIC, sources, "com.acme"))
                .contains("com.acme.loan");
    }
}