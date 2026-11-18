package com.walpro.autobuilder.core.build.inject;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T1-GT's recorded case (stuck-alternation, 2026-11-17): the referencing test's own class name contains the
 * missing name, and it writes the name through its location class. Added at the port: the loop's version
 * required every occurrence in the file to be qualified and answered a package here. Also the hardening:
 * a symbol line is matched by the whole name, not a name containing it.
 */
class SkeletonPlacementRecordedCaseTest {

    @Test
    void aReferencingFileWhoseNameContainsTheMissingNameIsStillAMemberReference() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("src/main/java/com/acme/stuck/StuckLedger.java",
                "package com.acme.stuck;\npublic class StuckLedger {\n}\n");
        sources.put("src/test/java/com/acme/stuck/StuckLedgerAlternatingPairsTest.java",
                "package com.acme.stuck;\nclass StuckLedgerAlternatingPairsTest {\n"
                        + "  java.util.List<StuckLedger.AlternatingPair> pairs;\n}\n");
        String diagnostic = """
                [ERROR] /p/src/test/java/com/acme/stuck/StuckLedgerAlternatingPairsTest.java:[3,35] cannot find symbol
                [ERROR]   symbol:   class AlternatingPair
                [ERROR]   location: class com.acme.stuck.StuckLedger
                """;

        assertThat(SkeletonPlacement.packageFor("AlternatingPair", diagnostic, sources, "com.acme")).isEmpty();
    }

    @Test
    void aSymbolWhoseNameContainsTheMissingNameDoesNotLendItsLocation() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("src/main/java/com/acme/stuck/Report.java",
                "package com.acme.stuck;\nclass Report { StuckLedger.AlternatingPairs all; StuckLedger.AlternatingPair one; }\n");
        sources.put("src/main/java/com/acme/member/MemberService.java",
                "package com.acme.member;\nclass MemberService { AlternatingPair pair; }\n");
        sources.put("src/main/java/com/acme/member/MemberDesk.java",
                "package com.acme.member;\nclass MemberDesk { AlternatingPair pair; }\n");
        String diagnostic = """
                [ERROR] /p/src/main/java/com/acme/stuck/Report.java:[2,27] cannot find symbol
                [ERROR]   symbol:   class AlternatingPairs
                [ERROR]   location: class com.acme.stuck.StuckLedger
                [ERROR] /p/src/main/java/com/acme/member/MemberService.java:[2,23] cannot find symbol
                [ERROR]   symbol:   class AlternatingPair
                [ERROR]   location: class com.acme.member.MemberService
                """;

        assertThat(SkeletonPlacement.packageFor("AlternatingPair", diagnostic, sources, "com.acme"))
                .contains("com.acme.member");
    }
}
