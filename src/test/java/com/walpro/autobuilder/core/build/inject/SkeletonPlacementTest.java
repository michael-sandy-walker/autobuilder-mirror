package com.walpro.autobuilder.core.build.inject;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SkeletonPlacementTest {

    private static final String UNQUALIFIED_DIAGNOSTIC = """
            [ERROR] /p/src/main/java/com/acme/member/MemberService.java:[12,9] cannot find symbol
            [ERROR]   symbol:   class Loan
            [ERROR]   location: class com.acme.member.MemberService
            """;

    @Test
    void anImportNamesThePackage() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("src/main/java/com/acme/member/MemberService.java",
                "package com.acme.member;\nimport com.acme.loan.Loan;\nclass MemberService { Loan l; }\n");

        assertThat(SkeletonPlacement.packageFor("Loan", UNQUALIFIED_DIAGNOSTIC, sources, "com.acme"))
                .contains("com.acme.loan");
    }

    @Test
    void anImportBeatsAnUnqualifiedUse() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("src/main/java/com/acme/member/MemberService.java",
                "package com.acme.member;\nclass MemberService { Loan a; Loan b; }\n");
        sources.put("src/main/java/com/acme/desk/Desk.java",
                "package com.acme.desk;\nimport com.acme.loan.Loan;\nclass Desk { Loan l; }\n");

        assertThat(SkeletonPlacement.packageFor("Loan", UNQUALIFIED_DIAGNOSTIC, sources, "com.acme"))
                .contains("com.acme.loan");
    }

    @Test
    void theMostCommonImportWins() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("src/main/java/com/acme/a/A.java", "package com.acme.a;\nimport com.acme.x.Loan;\nclass A {}\n");
        sources.put("src/main/java/com/acme/b/B.java", "package com.acme.b;\nimport com.acme.y.Loan;\nclass B {}\n");
        sources.put("src/main/java/com/acme/c/C.java", "package com.acme.c;\nimport com.acme.y.Loan;\nclass C {}\n");

        assertThat(SkeletonPlacement.packageFor("Loan", UNQUALIFIED_DIAGNOSTIC, sources, "com.acme"))
                .contains("com.acme.y");
    }

    @Test
    void anUnqualifiedUseNamesItsOwnPackage() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("src/main/java/com/acme/member/MemberService.java",
                "package com.acme.member;\nclass MemberService { Loan l; }\n");

        assertThat(SkeletonPlacement.packageFor("Loan", UNQUALIFIED_DIAGNOSTIC, sources, "com.acme"))
                .contains("com.acme.member");
    }

    @Test
    void withNoReferenceTheConventionPackageIsUsed() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("src/main/java/com/acme/other/Other.java", "package com.acme.other;\nclass Other {}\n");

        assertThat(SkeletonPlacement.packageFor("Loan", UNQUALIFIED_DIAGNOSTIC, sources, "com.acme.model"))
                .contains("com.acme.model");
        assertThat(SkeletonPlacement.packageFor("Loan", UNQUALIFIED_DIAGNOSTIC, Map.of(), "com.acme.model"))
                .contains("com.acme.model");
    }

    @Test
    void aFileNamedAfterTheMissingClassIsNoReferenceToIt() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("src/main/java/com/acme/stale/Loan.java", "package com.acme.stale;\nclass Loan { Loan next; }\n");

        assertThat(SkeletonPlacement.packageFor("Loan", UNQUALIFIED_DIAGNOSTIC, sources, "com.acme.model"))
                .contains("com.acme.model");
    }

    @Test
    void aWordContainingTheNameIsNoUse() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("src/main/java/com/acme/member/MemberService.java",
                "package com.acme.member;\nclass MemberService { LoanBook b; }\n");

        assertThat(SkeletonPlacement.packageFor("Loan", UNQUALIFIED_DIAGNOSTIC, sources, "com.acme.model"))
                .contains("com.acme.model");
    }
}
