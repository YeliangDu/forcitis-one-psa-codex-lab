package com.forcitis.one.platform.integration.guard;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** ADR-0060 section 1 item 3 and ADR-0070 guard rule 1: the scanner and the bucket clients live in platform.integration only. */
class FileServicePortsIsolationTest {
    static final String ROOT = "com.forcitis.one";
    static final String SCAN = ROOT + ".platform.integration.infrastructure.scan..";
    static final String FILE_STORE = ROOT + ".platform.integration.infrastructure.filestore..";
    static final String ARCHIVE = ROOT + ".platform.integration.infrastructure.archive..";

    static final JavaClasses CLASSES = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages(ROOT);

    @Test
    void onlyTheScanAdapterOpensSockets() {
        noClasses().that().resideOutsideOfPackage(SCAN)
                .should().dependOnClassesThat().haveFullyQualifiedName("java.nio.channels.SocketChannel")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.net.UnixDomainSocketAddress")
                .because("clamd is reached from platform.integration.infrastructure.scan only (ADR-0070)")
                .check(CLASSES);
        noClasses().that().resideOutsideOfPackage(SCAN).should().dependOnClassesThat().resideInAPackage(SCAN).check(CLASSES);
    }

    @Test
    void onlyTheArchiveAndFileStoreAdaptersUseTheObjectStorageSdk() {
        noClasses().that().resideOutsideOfPackages(ARCHIVE, FILE_STORE)
                .should().dependOnClassesThat().resideInAPackage("com.aliyun.oss..")
                .because("object storage is reached through the ArchiveVault and BusinessFileStore ports (ADR-0060)")
                .check(CLASSES);
        noClasses().that().resideOutsideOfPackage(FILE_STORE).should().dependOnClassesThat().resideInAPackage(FILE_STORE).check(CLASSES);
    }

    @Test
    void noSourceStreamsFilesToClamd() throws IOException {
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            List<Path> streaming = sources.filter(p -> p.toString().endsWith(".java")).filter(p -> {
                try {
                    return Files.readString(p).contains("INSTREAM");
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }).toList();
            assertThat(streaming).as("INSTREAM has a stream length limit that tends to be configured as a silent skip (ADR-0070)").isEmpty();
        }
    }
}
