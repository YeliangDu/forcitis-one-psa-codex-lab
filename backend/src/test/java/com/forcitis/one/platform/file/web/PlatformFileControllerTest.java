package com.forcitis.one.platform.file.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.file.api.FileError;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class PlatformFileControllerTest {

    @Test
    @DisplayName("the name comes from filename* (UTF-8) or filename, reduced to its last path segment; a missing, empty or control-character name is refused")
    void theNameIsTheLastSegment() {
        assertThat(PlatformFileController.originalName("attachment; filename*=UTF-8''%E6%8A%A5%E5%91%8A.pdf")).isEqualTo("报告.pdf");
        assertThat(PlatformFileController.originalName("attachment; filename=\"a b.pdf\"")).isEqualTo("a b.pdf");
        assertThat(PlatformFileController.originalName("attachment; filename=\"C:\\\\docs\\\\x.pdf\"")).isEqualTo("x.pdf");
        assertThat(PlatformFileController.originalName("attachment; filename*=UTF-8''..%2F..%2Fetc%2Fpasswd")).isEqualTo("passwd");
        for (String bad : new String[] {"attachment", "inline", "attachment; filename=\"\"", "attachment; filename=\"dir/\"", "filename*=UTF-8''%00.pdf", "filename*=UTF-8''gpj.%E2%80%AEfdp",
                "attachment; filename*=NOPE''x.pdf", "attachment; filename=\"" + "a".repeat(256) + "\""}) {
            assertThatThrownBy(() -> PlatformFileController.originalName(bad)).as(bad).isInstanceOf(InvalidRequestException.class);
        }
        assertThat(PlatformFileController.originalName("attachment; filename=\"" + "a".repeat(255) + "\"")).hasSize(255);
        assertThat(PlatformFileController.originalName("attachment; filename=\"a\u200Db.pdf\"")).isEqualTo("a\u200Db.pdf");
    }

    @Test
    @DisplayName("direction marks, line breaks and control characters are refused at both ends of every range; joiners, variation selectors and emoji pass")
    void unsafeCharactersInTheName() {
        int[] refused = {0x061C, 0x200E, 0x200F, 0x202A, 0x202E, 0x2066, 0x2069, 0x2028, 0x2029, 0x0085, 0x009F, 0x000A, 0x0009};
        for (int c : refused) {
            String name = "a" + new String(Character.toChars(c)) + "b.pdf";
            assertThatThrownBy(() -> PlatformFileController.originalName("attachment; filename=\"" + name + "\"")).as("U+%04X", c)
                    .isInstanceOf(InvalidRequestException.class);
        }
        for (String ok : new String[] {"a\u200Cb.pdf", "a\u200Db.pdf", "a\uFE0Fb.pdf", "\uD83D\uDE00.pdf", "\uD83D\uDC68\u200D\uD83D\uDC69.pdf"}) {
            assertThat(PlatformFileController.originalName("attachment; filename=\"" + ok + "\"")).isEqualTo(ok);
        }
        assertThat(PlatformFileController.originalName("attachment; filename=\"" + "\uD83D\uDE00".repeat(255) + "\"")).hasSize(510);
        assertThatThrownBy(() -> PlatformFileController.originalName("attachment; filename=\"" + "\uD83D\uDE00".repeat(256) + "\"")).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("the declared media type is type/subtype without parameters; none means octet-stream; garbage is refused")
    void theDeclaredMediaType() {
        assertThat(PlatformFileController.declaredMediaType("application/pdf; version=1")).isEqualTo("application/pdf");
        assertThat(PlatformFileController.declaredMediaType(null)).isEqualTo("application/octet-stream");
        assertThat(PlatformFileController.declaredMediaType(" ")).isEqualTo("application/octet-stream");
        assertThat(PlatformFileController.declaredMediaType("application/pdf")).isEqualTo("application/pdf");
        assertThatThrownBy(() -> PlatformFileController.declaredMediaType("not a type")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> PlatformFileController.declaredMediaType("multipart/form-data; boundary=x")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> PlatformFileController.declaredMediaType("application/x-www-form-urlencoded")).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("with the file service switched off an upload is 'unavailable' and a status is 'not found': nothing is accepted without the malware gate")
    void offMeansNothingIsAccepted() {
        StaticListableBeanFactory none = new StaticListableBeanFactory();
        PlatformFileController off = new PlatformFileController(none.getBeanProvider(com.forcitis.one.platform.file.application.StoreContent.class),
                none.getBeanProvider(com.forcitis.one.platform.file.application.GetUploadStatus.class));

        assertThatThrownBy(() -> off.store(null, "attachment; filename=\"a.pdf\"", new UserId(UUID.randomUUID()))).isInstanceOfSatisfying(DomainException.class,
                refusal -> {
                    assertThat(refusal.errorCode()).isEqualTo(FileError.PLATFORM_FILE_SCAN_UNAVAILABLE);
                    assertThat(refusal.kind()).isEqualTo(ErrorKind.UNAVAILABLE);
                });
        assertThatThrownBy(() -> off.status(UUID.randomUUID(), new UserId(UUID.randomUUID()))).isInstanceOfSatisfying(DomainException.class,
                refusal -> assertThat(refusal.errorCode()).isEqualTo(FileError.PLATFORM_FILE_NOT_FOUND));
    }
}
