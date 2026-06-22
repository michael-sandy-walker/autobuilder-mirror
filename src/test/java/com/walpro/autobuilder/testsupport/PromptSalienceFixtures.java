package com.walpro.autobuilder.testsupport;

import java.util.List;
import java.util.Map;

/**
 * Fixtures for the prompt-salience adherence eval ({@code PromptCompactionAdherenceEvalTest}).
 *
 * <p>Each case reproduces the real "four names for one concept" failure shape (2026-06-17): a type
 * already exists on disk under a CANONICAL name, the evolution prompt's "EXISTING PROJECT TYPES"
 * diagnostic lists that canonical name, but the verbatim echo of the previous cycle's raw response
 * — which dominates the prompt (~64% of tokens, measured) — refers to the same concept by a WRONG
 * synonym. The question the eval answers: does the local model follow the salient-but-wrong echo, or
 * the diagnostic? The prompt-compaction fix caps that echo; the eval measures whether capping it
 * raises the rate at which the model reuses the canonical name.
 *
 * <p>Adherence is binary and machine-checkable: does the model's response reference the canonical
 * name (good) rather than only the synonym (bad). Each case's task forces the concept to appear
 * (implement the method that throws/uses the domain type), so silence is not an escape.
 *
 * <p>Test-only data: target-project class names live here freely (fixtures), never in framework code.
 */
public final class PromptSalienceFixtures {

    private PromptSalienceFixtures() {}

    /**
     * One case. {@code canonicalType} is what exists on disk and what the diagnostic lists;
     * {@code synonym} is the wrong name the bloated echo uses for the same concept. A response
     * adheres when it contains {@code canonicalType}.
     */
    public record Case(String name, String subjectGoal, String subGoal, List<String> criteria,
                       String canonicalType, String synonym,
                       Map<String, String> previousOutput, String previousRawResponse) {
    }

    public static List<Case> all() {
        return List.of(loanLimitException(), bookNotFoundException(), memberRepository(), alreadyReturnedException());
    }

    private static final String SUBJECT =
            "Evolve a Spring Boot library lending REST API (Books, Members, Loans) with in-memory storage.";

    /** A ~150-line echo of a plausible previous response that consistently uses {@code synonym}. */
    private static String bloatedEcho(String synonym, String serviceClass, String pkg) {
        StringBuilder sb = new StringBuilder();
        sb.append("```java [src/main/java/").append(pkg.replace('.', '/')).append("/").append(serviceClass).append(".java]\n");
        sb.append("package ").append(pkg).append(";\n\n");
        sb.append("import java.util.*;\nimport org.springframework.stereotype.Service;\n\n");
        sb.append("@Service\npublic class ").append(serviceClass).append(" {\n");
        sb.append("    private final Map<Long, Object> store = new HashMap<>();\n");
        sb.append("    private long seq = 0L;\n\n");
        // pad with plausible CRUD-ish methods so the echo dominates the prompt (>40 lines)
        for (int i = 1; i <= 18; i++) {
            sb.append("    public Object operation").append(i).append("(Long id, Object payload) {\n");
            sb.append("        if (id == null) {\n");
            sb.append("            throw new ").append(synonym).append("(\"id required for op ").append(i).append("\");\n");
            sb.append("        }\n");
            sb.append("        Object current = store.get(id);\n");
            sb.append("        if (current == null) {\n");
            sb.append("            throw new ").append(synonym).append("(\"not found in op ").append(i).append("\");\n");
            sb.append("        }\n");
            sb.append("        store.put(id, payload);\n");
            sb.append("        return current;\n");
            sb.append("    }\n\n");
        }
        sb.append("}\n```\n");
        return sb.toString();
    }

    private static Map<String, String> exceptionOnDisk(String canonical, String pkg) {
        return Map.of(
                "src/main/java/" + pkg.replace('.', '/') + "/" + canonical + ".java",
                "package " + pkg + ";\npublic class " + canonical + " extends RuntimeException {\n"
                        + "    public " + canonical + "(String message) { super(message); }\n}\n",
                "src/main/java/" + pkg.replace('.', '/') + "/Loan.java",
                "package " + pkg + ";\npublic class Loan {\n    private Long id;\n    private boolean returned;\n}\n");
    }

    public static Case loanLimitException() {
        String pkg = "com.example.library.loan";
        String canonical = "MaxActiveLoansException";
        String synonym = "LoanLimitExceededException";
        return new Case(
                "loan-limit-exception", SUBJECT,
                "Implement LoanService.createLoan so a member with 3 active loans is rejected by throwing the project's existing max-active-loans exception.",
                List.of("Creating a 4th active loan for a member throws the project's max-active-loans domain exception."),
                canonical, synonym,
                exceptionOnDisk(canonical, pkg),
                bloatedEcho(synonym, "LoanService", pkg));
    }

    public static Case bookNotFoundException() {
        String pkg = "com.example.library.book";
        String canonical = "BookNotFoundException";
        String synonym = "NoSuchBookException";
        return new Case(
                "book-not-found-exception", SUBJECT,
                "Implement BookService.getBook so a missing book id throws the project's existing book-not-found exception.",
                List.of("Fetching a non-existent book id throws the project's book-not-found domain exception."),
                canonical, synonym,
                exceptionOnDisk(canonical, pkg),
                bloatedEcho(synonym, "BookService", pkg));
    }

    public static Case memberRepository() {
        String pkg = "com.example.library.member";
        String canonical = "MemberStore";
        String synonym = "MemberRepository";
        Map<String, String> onDisk = Map.of(
                "src/main/java/" + pkg.replace('.', '/') + "/MemberStore.java",
                "package " + pkg + ";\nimport java.util.*;\npublic class MemberStore {\n"
                        + "    private final Map<Long, Member> byId = new HashMap<>();\n"
                        + "    public Member find(Long id) { return byId.get(id); }\n"
                        + "    public Member save(Member m) { return m; }\n}\n",
                "src/main/java/" + pkg.replace('.', '/') + "/Member.java",
                "package " + pkg + ";\npublic class Member {\n    private Long id;\n    private String email;\n}\n");
        return new Case(
                "member-store", SUBJECT,
                "Implement MemberService.register so it persists the member through the project's existing in-memory member storage class.",
                List.of("Registering a member stores it via the project's existing member storage type."),
                "MemberStore", "MemberRepository",
                onDisk,
                bloatedEcho(synonym, "MemberService", pkg));
    }

    public static Case alreadyReturnedException() {
        String pkg = "com.example.library.loan";
        String canonical = "LoanAlreadyReturnedException";
        String synonym = "ReturnedLoanException";
        return new Case(
                "already-returned-exception", SUBJECT,
                "Implement LoanService.returnLoan so returning an already-returned loan throws the project's existing already-returned exception.",
                List.of("Returning a loan that is already returned throws the project's already-returned domain exception."),
                canonical, synonym,
                exceptionOnDisk(canonical, pkg),
                bloatedEcho(synonym, "LoanReturnService", pkg));
    }
}
