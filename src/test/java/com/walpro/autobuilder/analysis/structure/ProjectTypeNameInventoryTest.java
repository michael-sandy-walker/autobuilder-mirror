package com.walpro.autobuilder.analysis.structure;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectTypeNameInventoryTest {

    @Test
    void nullOrEmptyInput_yieldsEmptyInventoryAndBlankBlock() {
        assertThat(ProjectTypeNameInventory.inventory(null).isEmpty()).isTrue();
        assertThat(ProjectTypeNameInventory.inventory(Map.of()).isEmpty()).isTrue();
        assertThat(ProjectTypeNameInventory.asPromptBlock(null)).isEmpty();
        assertThat(ProjectTypeNameInventory.asPromptBlock(Map.of())).isEmpty();
    }

    @Test
    void exceptionsAreDetectedByNameSuffix_andListedSeparately() {
        Map<String, String> files = Map.of(
                "src/main/java/app/MaxLoansExceededException.java",
                "package app; public class MaxLoansExceededException extends RuntimeException {}",
                "src/main/java/app/Loan.java",
                "package app; public class Loan {}");

        ProjectTypeNameInventory.Inventory inv = ProjectTypeNameInventory.inventory(files);

        assertThat(inv.exceptions()).containsExactly("MaxLoansExceededException");
        assertThat(inv.otherTypes()).containsExactly("Loan");
    }

    @Test
    void exceptionDetectedByExtendsClause_evenWithoutExceptionSuffixInName() {
        Map<String, String> files = Map.of(
                "src/main/java/app/LoanLimitBreach.java",
                "package app; public class LoanLimitBreach extends RuntimeException {}");

        ProjectTypeNameInventory.Inventory inv = ProjectTypeNameInventory.inventory(files);

        assertThat(inv.exceptions()).containsExactly("LoanLimitBreach");
        assertThat(inv.otherTypes()).isEmpty();
    }

    @Test
    void nestedTypesAreIncluded() {
        Map<String, String> files = Map.of(
                "src/main/java/app/Api.java",
                "package app; public class Api { public static class NotFoundException extends RuntimeException {} record Page() {} }");

        ProjectTypeNameInventory.Inventory inv = ProjectTypeNameInventory.inventory(files);

        assertThat(inv.exceptions()).contains("NotFoundException");
        assertThat(inv.otherTypes()).contains("Api", "Page");
    }

    @Test
    void unparseableFile_isSkippedWithoutThrowing() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("src/main/java/app/Broken.java", "this is not java {{{");
        files.put("src/main/java/app/Book.java", "package app; public class Book {}");

        ProjectTypeNameInventory.Inventory inv = ProjectTypeNameInventory.inventory(files);

        assertThat(inv.otherTypes()).containsExactly("Book");
    }

    @Test
    void nonJavaAndBlankFiles_areIgnored() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("pom.xml", "<project/>");
        files.put("src/main/java/app/Book.java", "");
        files.put("src/main/java/app/Service.java", "package app; public class Service {}");

        ProjectTypeNameInventory.Inventory inv = ProjectTypeNameInventory.inventory(files);

        assertThat(inv.otherTypes()).containsExactly("Service");
    }

    @Test
    void promptBlock_carriesReuseDirectiveAndBothCategories() {
        Map<String, String> files = Map.of(
                "src/main/java/app/BookNotFoundException.java",
                "package app; public class BookNotFoundException extends RuntimeException {}",
                "src/main/java/app/Book.java",
                "package app; public class Book {}");

        String block = ProjectTypeNameInventory.asPromptBlock(files);

        assertThat(block).contains("EXISTING PROJECT TYPES");
        assertThat(block).contains("reuse these EXACT names");
        assertThat(block).contains("Exceptions already defined: BookNotFoundException");
        assertThat(block).contains("Other types already defined: Book");
    }

    @Test
    void otherTypesList_isCappedWithOverflowMarker() {
        Map<String, String> files = IntStream.range(0, ProjectTypeNameInventory.MAX_OTHER_TYPES + 5)
                .boxed()
                .collect(Collectors.toMap(
                        i -> "src/main/java/app/T" + i + ".java",
                        i -> "package app; public class T" + i + " {}"));

        String block = ProjectTypeNameInventory.asPromptBlock(files);

        assertThat(block).contains("(+5 more)");
    }
}
