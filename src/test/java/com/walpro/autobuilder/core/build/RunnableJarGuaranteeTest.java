package com.walpro.autobuilder.core.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T4-DO. The guarantee asks the BUILT JAR whether it can run, because that is the same question
 * {@code java -jar} asks and it needs no list of which build plugins produce executable artifacts —
 * such a list would be the ecosystem table T4-DI removed, and would be wrong anyway for a plugin
 * that auto-detects its entry point.
 *
 * <p>Verified end-to-end before these tests were written: a real project with a real dependency,
 * built with Maven, failed `java -jar` with "no main manifest attribute"; after the repair it
 * printed its expected output with the dependency resolved from {@code lib/}.
 */
class RunnableJarGuaranteeTest {

    @TempDir
    Path root;

    private static final Map<String, String> ONE_ENTRY_POINT = Map.of(
            "src/main/java/com/demo/Main.java",
            "package com.demo;\npublic class Main { public static void main(String[] args) {} }\n");

    private static final String POM_WITH_DEPENDENCY = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <artifactId>demo</artifactId>
                <dependencies>
                    <dependency><groupId>org.apache.commons</groupId>
                    <artifactId>commons-lang3</artifactId><version>3.14.0</version></dependency>
                </dependencies>
            </project>
            """;

    private void writeJar(String name, String mainClass) throws IOException {
        writeJar(root, name, mainClass, true);
    }

    /** A jar whose manifest starts {@code mainClass}; {@code carriesIt} false is a merge's leftover: the class is gone. */
    private static void writeJar(Path dir, String name, String mainClass, boolean carriesIt) throws IOException {
        Path target = Files.createDirectories(dir.resolve("target"));
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (mainClass != null) manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(target.resolve(name)), manifest)) {
            if (mainClass != null && carriesIt) {
                out.putNextEntry(new java.util.jar.JarEntry(mainClass.replace('.', '/') + ".class"));
                out.closeEntry();
            }
            out.flush();
        }
    }

    @Test
    void aJarThatCannotRun_getsItsEntryPointDeclared() throws IOException {
        writeJar("demo.jar", null);

        Optional<String> repaired = RunnableJarGuarantee.repairedPom(root, POM_WITH_DEPENDENCY, ONE_ENTRY_POINT);

        assertThat(repaired).isPresent();
        assertThat(repaired.get()).contains("<mainClass>com.demo.Main</mainClass>");
    }

    @Test
    void aJarThatAlreadyDeclaresAnEntryPoint_isLeftAlone() throws IOException {
        // The project answered the question itself — however it did so, and whichever plugin it
        // used. Nothing here needs to know which.
        writeJar("demo.jar", "com.demo.Main");

        assertThat(RunnableJarGuarantee.repairedPom(root, POM_WITH_DEPENDENCY, ONE_ENTRY_POINT)).isEmpty();
    }

    @Test
    void sourcesNamingNoSingleEntryPoint_yieldNoOpinion() throws IOException {
        writeJar("demo.jar", null);
        Map<String, String> library = Map.of(
                "src/main/java/com/demo/Util.java", "package com.demo;\npublic class Util {}\n");

        // A library owes no entry point; two mains would be a choice this must not make.
        assertThat(RunnableJarGuarantee.repairedPom(root, POM_WITH_DEPENDENCY, library)).isEmpty();
    }

    @Test
    void noBuiltJarToInspect_yieldsNoOpinion() {
        assertThat(RunnableJarGuarantee.repairedPom(root, POM_WITH_DEPENDENCY, ONE_ENTRY_POINT)).isEmpty();
    }

    @Test
    void declaredDependenciesArePlacedBesideTheJar_soAPlainJarCanStillFindThem() throws IOException {
        writeJar("demo.jar", null);

        String repaired = RunnableJarGuarantee.repairedPom(root, POM_WITH_DEPENDENCY, ONE_ENTRY_POINT).orElseThrow();

        assertThat(repaired).contains("maven-dependency-plugin").contains("copy-dependencies");
        assertThat(repaired).contains("<addClasspath>true</addClasspath>");
    }

    @Test
    void aProjectWithNoDependencies_needsNoBundling() throws IOException {
        writeJar("demo.jar", null);
        String bare = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <artifactId>demo</artifactId>
                </project>
                """;

        String repaired = RunnableJarGuarantee.repairedPom(root, bare, ONE_ENTRY_POINT).orElseThrow();

        assertThat(repaired).contains("<mainClass>com.demo.Main</mainClass>");
        assertThat(repaired).doesNotContain("maven-dependency-plugin");
    }

    @Test
    void theRepairNamesNoFramework() throws IOException {
        writeJar("demo.jar", null);

        String repaired = RunnableJarGuarantee.repairedPom(root, POM_WITH_DEPENDENCY, ONE_ENTRY_POINT).orElseThrow();

        assertThat(repaired).doesNotContain("spring").doesNotContain("Spring")
                            .doesNotContain("starter").doesNotContain("shade");
    }

    @Test
    void existingBuildConfigurationSurvivesTheRepair() throws IOException {
        writeJar("demo.jar", null);
        String withBuild = POM_WITH_DEPENDENCY.replace("</project>",
                "    <build>\n        <plugins>\n            <plugin>"
                + "<artifactId>maven-surefire-plugin</artifactId></plugin>\n"
                + "        </plugins>\n    </build>\n</project>");

        String repaired = RunnableJarGuarantee.repairedPom(root, withBuild, ONE_ENTRY_POINT).orElseThrow();

        assertThat(repaired).contains("maven-surefire-plugin");   // the project's own config kept
        assertThat(repaired).contains("<mainClass>com.demo.Main</mainClass>");
    }

    /**
     * T4-DO (routing, 2026-09-21). Between writing the pom and the next package the JAR still
     * carries no {@code Main-Class} — the jar is the authority, and it has not been rebuilt yet —
     * so every caller in that window gets the same non-empty answer. At the closure edge that
     * never showed, because its one call site returned immediately. On the per-cycle route it
     * does: the guard fired FIVE times in run 2026-09-21_00-32-59, and freshness does not stop it
     * because {@code artifactOlderThanSources} walks {@code src/} and never reads {@code pom.xml}.
     * Without the idempotence check this appends the plugin block once per firing.
     */
    @Test
    void theRepairIsNotWrittenTwiceWhileTheJarAwaitsItsRebuild() throws IOException {
        writeJar("demo.jar", null);
        writeProjectOnDisk(POM_WITH_DEPENDENCY);

        assertThat(RunnableJarGuarantee.repairOnDisk(root)).isTrue();
        String afterFirst = Files.readString(root.resolve("pom.xml"));

        // The jar is untouched — exactly the state the next four firings of the gate would see.
        assertThat(RunnableJarGuarantee.repairOnDisk(root)).isFalse();

        assertThat(Files.readString(root.resolve("pom.xml"))).isEqualTo(afterFirst);
        assertThat(afterFirst.split("<mainClass>", -1)).hasSize(2);
        assertThat(afterFirst.split("maven-jar-plugin", -1)).hasSize(2);
    }

    /** The disk form decides nothing the in-memory form does not: a library is still left alone. */
    @Test
    void repairOnDisk_leavesAProjectThatOwesNoEntryPointAlone() throws IOException {
        writeJar("demo.jar", null);
        Path src = root.resolve("src/main/java/com/demo/Library.java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, "package com.demo;\npublic class Library { public int add(int a) { return a; } }\n");
        Files.writeString(root.resolve("pom.xml"), POM_WITH_DEPENDENCY);

        assertThat(RunnableJarGuarantee.repairOnDisk(root)).isFalse();
        assertThat(Files.readString(root.resolve("pom.xml"))).isEqualTo(POM_WITH_DEPENDENCY);
    }

    /** No pom is not a project this can repair, and it must not throw looking for one. */
    @Test
    void repairOnDisk_withNoPom_abstains() throws IOException {
        writeJar("demo.jar", null);
        writeProjectSources();

        assertThat(RunnableJarGuarantee.repairOnDisk(root)).isFalse();
    }

    private void writeProjectOnDisk(String pom) throws IOException {
        writeProjectSources();
        Files.writeString(root.resolve("pom.xml"), pom);
    }

    private void writeProjectSources() throws IOException {
        for (Map.Entry<String, String> e : ONE_ENTRY_POINT.entrySet()) {
            Path f = root.resolve(e.getKey());
            Files.createDirectories(f.getParent());
            Files.writeString(f, e.getValue());
        }
    }

    // ── T4-DW: evidence that retires the archive-entry-point item ────────────────────────────

    /** The item's own instruction is "declare the entry point the sources provide": done in the pom. */
    @Test
    void entryPointDeclared_whenThePomDeclaresTheOneTheSourcesName_evenBeforeARebuild() throws IOException {
        writeJar("demo.jar", null);
        writeProjectOnDisk(POM_WITH_DEPENDENCY);
        assertThat(RunnableJarGuarantee.entryPointDeclared(root)).isFalse();

        assertThat(RunnableJarGuarantee.repairOnDisk(root)).isTrue();

        // The jar still carries no Main-Class; the declaration alone answers the item.
        assertThat(RunnableJarGuarantee.entryPointDeclared(root)).isTrue();
    }

    @Test
    void entryPointDeclared_whenTheArchiveAlreadyNamesAMainClass() throws IOException {
        writeJar("demo.jar", "com.demo.Main");

        assertThat(RunnableJarGuarantee.entryPointDeclared(root)).isTrue();
    }

    /** A declaration of some OTHER class is not the instruction carried out. */
    @Test
    void entryPointNotDeclared_whenThePomNamesADifferentClass() throws IOException {
        writeJar("demo.jar", null);
        writeProjectOnDisk(POM_WITH_DEPENDENCY.replace("</project>",
                "<build><plugins><plugin><configuration><mainClass>com.demo.Other</mainClass>"
                        + "</configuration></plugin></plugins></build></project>"));

        assertThat(RunnableJarGuarantee.entryPointDeclared(root)).isFalse();
    }

    /** An abstention is not a pass: no pom, no archive, nothing to read answers false. */
    @Test
    void entryPointNotDeclared_whenNothingCanBeEstablished() {
        assertThat(RunnableJarGuarantee.entryPointDeclared(root)).isFalse();
        assertThat(RunnableJarGuarantee.entryPointDeclared(null)).isFalse();
    }

    // ---------------------------------------------------------------- T1-GV --

    /**
     * T1-GV, recorded (data/gradient-flower-field, 2026-11-18): a Bring together merged two applications into the flower
     * project's frame and deleted the gradient's; the build of the module that runs kept starting the deleted class,
     * and the jar it built could not start. The build now starts the one class that remains, in place.
     */
    @Test
    void aBuildStartingAClassTheMergeDeletedIsPointedAtTheOneThatRemains_inTheModuleThatRuns() throws IOException {
        Path app = Files.createDirectories(root.resolve("app"));
        Files.writeString(root.resolve("pom.xml"), "<project><modules><module>app</module></modules></project>");
        Files.writeString(root.resolve(".run-module"), "app\n");
        String pom = """
                <project>
                    <artifactId>app</artifactId>
                    <build><plugins><plugin>
                        <artifactId>maven-jar-plugin</artifactId>
                        <configuration><archive><manifest>
                            <mainClass>com.example.RainbowFrame</mainClass>
                        </manifest></archive></configuration>
                    </plugin></plugins></build>
                </project>
                """;
        Files.writeString(app.resolve("pom.xml"), pom);
        Path frame = app.resolve("src/main/java/com/example/screensaver/ScreensaverFrame.java");
        Files.createDirectories(frame.getParent());
        Files.writeString(frame, "package com.example.screensaver;\npublic class ScreensaverFrame {\n"
                + "    public static void main(String[] args) {}\n}\n");
        writeJar(app, "app-0.0.1-SNAPSHOT.jar", "com.example.RainbowFrame", false);

        assertThat(RunnableJarGuarantee.entryPointDeclared(root)).as("a start class the jar does not carry is no entry point")
                .isFalse();
        assertThat(RunnableJarGuarantee.repairOnDisk(root)).isTrue();

        String repaired = Files.readString(app.resolve("pom.xml"));
        assertThat(repaired).contains("<mainClass>com.example.screensaver.ScreensaverFrame</mainClass>")
                .doesNotContain("RainbowFrame");
        assertThat(repaired.split("maven-jar-plugin", -1)).as("pointed in place, not a second packaging block").hasSize(2);
        assertThat(RunnableJarGuarantee.entryPointDeclared(root)).as("declared in the build, before the rebuild").isTrue();
        assertThat(RunnableJarGuarantee.repairOnDisk(root)).as("written once").isFalse();
    }

    /** Known negative: a start class the sources still declare is the project's choice, left alone. */
    @Test
    void aStartClassTheSourcesStillDeclareIsLeftAlone() throws IOException {
        Map<String, String> sources = Map.of("src/main/java/com/demo/Main.java",
                "package com.demo;\npublic class Main { public static void main(String[] args) {} }\n");
        writeJar("demo.jar", "com.demo.Main");
        String pom = "<project><build><plugins><plugin><artifactId>maven-jar-plugin</artifactId><configuration><archive>"
                + "<manifest><mainClass>com.demo.Main</mainClass></manifest></archive></configuration></plugin></plugins>"
                + "</build></project>";

        assertThat(RunnableJarGuarantee.repairedPom(root, pom, sources)).isEmpty();
    }

    /** Two classes with a main: the check can say the jar will not start, not which one should -- it abstains. */
    @Test
    void aDeletedStartClassWithTwoCandidatesLeft_isNotGuessed() throws IOException {
        Map<String, String> sources = Map.of(
                "src/main/java/a/One.java", "package a;\npublic class One { public static void main(String[] args) {} }\n",
                "src/main/java/a/Two.java", "package a;\npublic class Two { public static void main(String[] args) {} }\n");
        writeJar(root, "demo.jar", "a.Gone", false);
        String pom = "<project><build><plugins><plugin><artifactId>maven-jar-plugin</artifactId><configuration><archive>"
                + "<manifest><mainClass>a.Gone</mainClass></manifest></archive></configuration></plugin></plugins>"
                + "</build></project>";

        assertThat(RunnableJarGuarantee.repairedPom(root, pom, sources)).isEmpty();
        assertThat(RunnableJarGuarantee.declaresEntryPoint(root.resolve("target/demo.jar"))).isFalse();
    }

    @Test
    void declaredBy_readsPackageAndName() {
        Map<String, String> sources = Map.of("x", "package com.demo;\nclass Main {}", "y", "class Top {}");
        assertThat(RunnableJarGuarantee.declaredBy("com.demo.Main", sources)).isTrue();
        assertThat(RunnableJarGuarantee.declaredBy("com.other.Main", sources)).isFalse();
        assertThat(RunnableJarGuarantee.declaredBy("Top", sources)).isTrue();
        assertThat(RunnableJarGuarantee.declaredBy("Main", sources)).as("Main is in a package").isFalse();
    }
}
