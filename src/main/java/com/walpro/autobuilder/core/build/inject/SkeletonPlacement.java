package com.walpro.autobuilder.core.build.inject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where a skeleton for a missing class goes: the package the project's references expect, read from
 * text only (no file system, no framework), so the decision can be tested and changed on its own.
 *
 * <p>T1-GT: empty when the reference is to a MEMBER -- the diagnostic places the missing class in a
 * class ({@code symbol: class X} followed by {@code location: class a.b.Outer}) and a source writes
 * {@code Outer.X}. No top-level file in any package satisfies that, and no skeleton is written
 * (recorded: a top-level {@code core/AlternatingPair.java} for {@code StuckLedger.AlternatingPair}).
 * A location alone is not enough: the class that uses a name unqualified is reported as its location too.
 *
 * <p>Otherwise, precedence (T3-BV): an explicit {@code import <pkg>.<name>;} in a main or test source
 * (the reference says exactly where it looks), then the declaring package of a source that uses the
 * name unqualified (same-package resolution), then the caller's convention package. A source file
 * named after the missing class itself is not a reference to it.
 *
 * <p>The member check and test sources were written by Autobuilder from the T1-GT ticket (T4-EZ's
 * forward case {@code skeleton-placement} v2); the whole-name matches were added at review.
 */
public final class SkeletonPlacement {

    private static final String MAIN_ROOT = "src/main/java/";
    private static final String TEST_ROOT = "src/test/java/";
    private static final Pattern PACKAGE_DECL = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");

    private SkeletonPlacement() {
    }

    /**
     * @param missingClass      the simple name the compiler could not find
     * @param diagnostic        the compiler output naming it
     * @param sources           the project's source files, keyed by path relative to the project root
     *                          (e.g. {@code src/main/java/a/b/C.java}, {@code src/test/java/a/b/CTest.java}),
     *                          valued by their text
     * @param conventionPackage the package to use when no reference says otherwise
     * @return the package for a top-level skeleton of {@code missingClass}, or empty when none should be written
     */
    public static Optional<String> packageFor(String missingClass, String diagnostic, Map<String, String> sources,
                                              String conventionPackage) {
        if (isMemberReference(missingClass, diagnostic, sources)) {
            return Optional.empty();
        }
        Optional<String> referenced = referenceDerivedPackage(missingClass, sources);
        return referenced.isPresent() ? referenced : Optional.ofNullable(conventionPackage);
    }

    private static boolean isMemberReference(String missingClass, String diagnostic, Map<String, String> sources) {
        if (diagnostic == null || sources == null || sources.isEmpty()) return false;
        String locationClass = extractLocationClass(diagnostic, missingClass);
        if (locationClass == null) return false;
        String locationSimple = locationClass.substring(locationClass.lastIndexOf('.') + 1);
        for (Map.Entry<String, String> source : sources.entrySet()) {
            String path = source.getKey();
            String content = source.getValue();
            if (path == null || content == null) continue;
            if (!path.endsWith(".java")) continue;
            if (path.endsWith("/" + missingClass + ".java")) continue;
            if (isQualifiedThroughLocation(missingClass, locationSimple, content)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isQualifiedThroughLocation(String missingClass, String locationSimple, String content) {
        return Pattern.compile("\\b" + Pattern.quote(locationSimple) + "\\s*\\.\\s*" + Pattern.quote(missingClass) + "\\b")
                .matcher(content).find();
    }

    private static String extractLocationClass(String diagnostic, String missingClass) {
        Pattern symbolLine = Pattern.compile("symbol:\\s*class\\s+" + Pattern.quote(missingClass) + "\\b");
        String[] lines = diagnostic.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (symbolLine.matcher(line).find()) {
                for (int j = i + 1; j < lines.length; j++) {
                    String next = lines[j].trim();
                    if (next.contains("location:")) {
                        String loc = next.substring(next.indexOf("location:") + "location:".length()).trim();
                        if (loc.startsWith("class ")) {
                            return loc.substring("class ".length()).trim();
                        }
                        return null;
                    }
                    if (next.contains("symbol:")) break;
                }
            }
        }
        return null;
    }

    private static Optional<String> referenceDerivedPackage(String token, Map<String, String> sources) {
        if (sources == null || sources.isEmpty()) return Optional.empty();
        Pattern importLine = Pattern.compile("(?m)^\\s*import\\s+([\\w.]+)\\." + Pattern.quote(token) + "\\s*;");
        Pattern usage = Pattern.compile("\\b" + Pattern.quote(token) + "\\b");

        Map<String, Integer> importPackages = new LinkedHashMap<>();
        Map<String, Integer> usagePackages = new LinkedHashMap<>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            String path = source.getKey();
            String content = source.getValue();
            if (path == null || content == null) continue;
            if (!path.startsWith(MAIN_ROOT) && !path.startsWith(TEST_ROOT)) continue;
            if (!path.endsWith(".java")) continue;
            if (path.endsWith("/" + token + ".java")) continue;
            Matcher imp = importLine.matcher(content);
            if (imp.find()) {
                importPackages.merge(imp.group(1), 1, Integer::sum);
            } else if (usage.matcher(content).find()) {
                Matcher pkg = PACKAGE_DECL.matcher(content);
                if (pkg.find()) usagePackages.merge(pkg.group(1), 1, Integer::sum);
            }
        }
        Optional<String> fromImports = mostCommon(importPackages);
        return fromImports.isPresent() ? fromImports : mostCommon(usagePackages);
    }

    private static Optional<String> mostCommon(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);
    }
}
