package com.walpro.autobuilder.analysis.structure;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * T4-BQ consumer (4): cross-step naming / identity consistency — the <i>inbound</i> half.
 *
 * <p>Each evolution cycle is a local operation with no link to what sibling work already named, so a
 * less-capable local model invents a fresh name for a concept that already has one on disk. The
 * 2026-06-17 test-authoring run is the evidence: the evolver minted <b>four</b> names for one
 * exception ({@code MaxActiveLoansExceededException} / {@code LoanMaxActiveException} /
 * {@code MaxActiveLoansException} / {@code MaxLoansExceededException}) across rewrites, and the test
 * sources then failed to compile with {@code cannot find symbol}. This walker is the meta-layer that
 * routes the already-established names down to each local step: it reads the project's declared type
 * names from the current output and surfaces them with a "reuse the exact name, do not mint a
 * synonym" directive (rendered by {@link com.walpro.autobuilder.protocol.evolution.EvolutionProtocolContract}).
 *
 * <p>Exceptions are split out and listed first because they dominate the synonym-minting failure mix
 * — they are the category the model reaches for by recall ("throw the loans-exceeded exception")
 * rather than by reference.
 *
 * <p>Pure AST over the supplied file set — no classpath, no symbol-solver configuration, no network
 * (same approach as the sibling {@link WiringEvidenceAnalyzer}); unparseable files are skipped, since
 * the tree is mid-evolution and often does not compile. The "other types" list is bounded
 * ({@value #MAX_OTHER_TYPES}) so it never bloats the prompt; exceptions are left whole (the lever, and
 * naturally few). This is informational steering only — it surfaces facts the model could not
 * otherwise see; it never gates or rewrites a mutation. See
 * {@code [[project-introspection-as-north-star]]} (the <i>information</i> axis of the self-model) and
 * {@code [[feedback-no-target-domain-in-framework-strings]]} (every name here comes from the evolving
 * project, never a hardcoded literal).
 */
@Slf4j
public final class ProjectTypeNameInventory {

    private ProjectTypeNameInventory() {
    }

    static final int MAX_OTHER_TYPES = 40;

    private static final Pattern JAVA_FILE = Pattern.compile(".*\\.java$");

    /** Distinct declared type names in the output, split into exceptions vs. everything else. */
    public record Inventory(List<String> exceptions, List<String> otherTypes) {
        boolean isEmpty() {
            return exceptions.isEmpty() && otherTypes.isEmpty();
        }
    }

    /**
     * Collects the distinct simple names of every type declared across {@code outputFiles}
     * (top-level and nested), classifying each as an exception or an "other" type. Order is
     * declaration-encounter order; a name classified as an exception never also appears in
     * {@code otherTypes}.
     */
    public static Inventory inventory(Map<String, String> outputFiles) {
        if (outputFiles == null || outputFiles.isEmpty()) {
            return new Inventory(List.of(), List.of());
        }
        Set<String> exceptions = new LinkedHashSet<>();
        Set<String> others = new LinkedHashSet<>();

        for (Map.Entry<String, String> e : outputFiles.entrySet()) {
            if (e.getKey() == null || !JAVA_FILE.matcher(e.getKey()).matches()) continue;
            if (e.getValue() == null || e.getValue().isBlank()) continue;

            CompilationUnit cu;
            try {
                var result = new JavaParser().parse(e.getValue()).getResult();
                if (result.isEmpty()) continue;
                cu = result.get();
            } catch (RuntimeException ex) {
                log.debug("T4-BQ naming-inventory: skipping unparseable {}: {}", e.getKey(), ex.getMessage());
                continue;
            }

            // findAll captures nested types as well as top-level declarations.
            for (TypeDeclaration<?> type : cu.findAll(TypeDeclaration.class)) {
                String name = type.getNameAsString();
                if (isException(type)) {
                    exceptions.add(name);
                } else {
                    others.add(name);
                }
            }
        }

        // A name declared as an exception anywhere wins that classification everywhere.
        others.removeAll(exceptions);
        return new Inventory(new ArrayList<>(exceptions), new ArrayList<>(others));
    }

    /**
     * Renders the naming-consistency prompt block, or empty string when the project has no declared
     * types yet (nothing to anchor on). Callers append it verbatim.
     */
    public static String asPromptBlock(Map<String, String> outputFiles) {
        Inventory inv = inventory(outputFiles);
        if (inv.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("--- EXISTING PROJECT TYPES (reuse these EXACT names) ---\n");
        sb.append("These types are already declared in this project. When you reference a concept one of\n");
        sb.append("them already represents, use its name VERBATIM — do NOT introduce a second,\n");
        sb.append("differently-spelled type for the same concept. A synonym does not compile against the\n");
        sb.append("existing code (it surfaces as `cannot find symbol`).\n");

        if (!inv.exceptions().isEmpty()) {
            sb.append("Exceptions already defined: ").append(String.join(", ", inv.exceptions())).append("\n");
            sb.append("To throw or catch a domain failure, use one of the exception names above — do not coin a new one.\n");
        }
        if (!inv.otherTypes().isEmpty()) {
            List<String> shown = inv.otherTypes();
            String overflow = "";
            if (shown.size() > MAX_OTHER_TYPES) {
                overflow = ", … (+" + (shown.size() - MAX_OTHER_TYPES) + " more)";
                shown = shown.subList(0, MAX_OTHER_TYPES);
            }
            sb.append("Other types already defined: ").append(String.join(", ", shown)).append(overflow).append("\n");
        }
        return sb.toString();
    }

    /**
     * An exception is recognised by name suffix ({@code *Exception} / {@code *Error}) or by an
     * {@code extends} clause that names such a supertype (or {@code Throwable}). The receiver may be
     * mid-evolution, so this is a conservative textual judgement, not a resolved type hierarchy.
     */
    private static boolean isException(TypeDeclaration<?> type) {
        String name = type.getNameAsString();
        if (name.endsWith("Exception") || name.endsWith("Error")) return true;
        if (type instanceof ClassOrInterfaceDeclaration cid) {
            for (ClassOrInterfaceType ext : cid.getExtendedTypes()) {
                String sup = ext.getNameAsString();
                if (sup.endsWith("Exception") || sup.endsWith("Error") || sup.equals("Throwable")) return true;
            }
        }
        return false;
    }
}
