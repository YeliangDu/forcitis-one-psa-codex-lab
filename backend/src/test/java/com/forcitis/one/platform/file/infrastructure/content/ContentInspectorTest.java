package com.forcitis.one.platform.file.infrastructure.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.file.infrastructure.content.ContentInspector.Inspection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentInspectorTest {

    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    private static final String CONTENT_TYPES = "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>";
    private static final String RELS_OPEN = "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">";

    @TempDir
    Path dir;

    private final ContentInspector inspector = new ContentInspector();

    private Inspection inspect(String name, byte[] bytes) throws IOException {
        Path file = dir.resolve(name);
        Files.write(file, bytes);
        return inspector.inspect(file);
    }

    // ---- 简单格式 ----

    @Test
    void pdfWithoutEncryptMarkIsPlain() throws IOException {
        byte[] pdf = "%PDF-1.7\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(inspect("a.pdf", pdf)).isEqualTo(new Inspection("application/pdf", false, false));
    }

    @Test
    void pdfWithEncryptMarkIsEncryptedEvenWhenNamedTxt() throws IOException {
        byte[] pdf = "%PDF-1.7\ntrailer<</Root 1 0 R/Encrypt 5 0 R>>\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(inspect("a.txt", pdf)).isEqualTo(new Inspection("application/pdf", true, false));
    }

    @Test
    void pdfEncryptMarkInHeadAndTailOfLargeFileIsFound() throws IOException {
        int pad = 3 * 1024 * 1024;
        byte[] mark = "/Encrypt".getBytes(StandardCharsets.ISO_8859_1);
        byte[] header = "%PDF-1.7\n".getBytes(StandardCharsets.ISO_8859_1);

        byte[] tail = new byte[pad];
        Arrays.fill(tail, (byte) ' ');
        System.arraycopy(header, 0, tail, 0, header.length);
        System.arraycopy(mark, 0, tail, pad - 100, mark.length);
        assertThat(inspect("tail.pdf", tail).encrypted()).isTrue();

        byte[] head = new byte[pad];
        Arrays.fill(head, (byte) ' ');
        System.arraycopy(header, 0, head, 0, header.length);
        System.arraycopy(mark, 0, head, 1000, mark.length);
        assertThat(inspect("head.pdf", head).encrypted()).isTrue();

        byte[] middle = new byte[pad];
        Arrays.fill(middle, (byte) ' ');
        System.arraycopy(header, 0, middle, 0, header.length);
        System.arraycopy(mark, 0, middle, pad / 2, mark.length);
        assertThat(inspect("middle.pdf", middle).encrypted()).isFalse();
    }

    @Test
    void imagesAreRecognizedByMagicNotExtension() throws IOException {
        assertThat(inspect("x.bin", new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}).mediaType())
                .isEqualTo("image/png");
        assertThat(inspect("x.png", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16}).mediaType())
                .isEqualTo("image/jpeg");
        assertThat(inspect("x.jpg", "GIF89a....".getBytes(StandardCharsets.ISO_8859_1)))
                .isEqualTo(new Inspection("image/gif", false, false));
    }

    @Test
    void textAndBinaryAreSeparated() throws IOException {
        assertThat(inspect("a.dat", "你好, hello\nworld".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(new Inspection("text/plain", false, false));
        assertThat(inspect("b.txt", new byte[] {'a', 0, 'b'}).mediaType()).isEqualTo("application/octet-stream");
        assertThat(inspect("c.txt", new byte[] {'a', (byte) 0xC3, 0x28}).mediaType()).isEqualTo("application/octet-stream");
        assertThat(inspect("d.txt", new byte[0]).mediaType()).isEqualTo("application/octet-stream");
    }

    @Test
    void sevenZipIsAlwaysUnconfirmedAndEncrypted() throws IOException {
        byte[] sevenZip = {0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C, 0, 4, 0, 0, 0, 0};
        assertThat(inspect("a.7z", sevenZip)).isEqualTo(new Inspection("application/x-7z-compressed", true, false));
    }

    // ---- zip ----

    @Test
    void plainZipIsNotEncrypted() throws IOException {
        Inspection result = inspect("a.docx", zip(Map.of("readme.txt", "hi")));
        assertThat(result).isEqualTo(new Inspection("application/zip", false, false));
    }

    @Test
    void zipEntryWithEncryptionFlagIsEncrypted() throws IOException {
        byte[] bytes = setEncryptionFlag(zip(Map.of("a.txt", "x", "b.txt", "y")));
        assertThat(inspect("a.zip", bytes)).isEqualTo(new Inspection("application/zip", true, false));
    }

    @Test
    void ooxmlTypesAreChosenByDirectory() throws IOException {
        assertThat(inspect("a.bin", ooxml("word/document.xml")))
                .isEqualTo(new Inspection(DOCX, false, false));
        assertThat(inspect("b.bin", ooxml("xl/workbook.xml")))
                .isEqualTo(new Inspection(XLSX, false, false));
        assertThat(inspect("c.docx", ooxml("ppt/presentation.xml")))
                .isEqualTo(new Inspection(PPTX, false, false));
    }

    @Test
    void ooxmlWithVbaProjectUsesMacroEnabledTypes() throws IOException {
        assertThat(inspect("a.docx", ooxml("word/document.xml", "word/vbaProject.bin")))
                .isEqualTo(new Inspection("application/vnd.ms-word.document.macroEnabled.12", false, true));
        assertThat(inspect("b.xlsx", ooxml("xl/workbook.xml", "xl/vbaProject.bin")))
                .isEqualTo(new Inspection("application/vnd.ms-excel.sheet.macroEnabled.12", false, true));
        assertThat(inspect("c.pptx", ooxml("ppt/presentation.xml", "ppt/vbaProject.bin")))
                .isEqualTo(new Inspection("application/vnd.ms-powerpoint.presentation.macroEnabled.12", false, true));
    }

    @Test
    void ooxmlMacroEnabledContentTypeCountsEvenWithoutVbaPart() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", "<Override ContentType=\"application/vnd.ms-excel.sheet.macroEnabled.main+xml\"/>");
        entries.put("xl/workbook.xml", "<workbook/>");
        assertThat(inspect("a.xlsx", zip(entries)))
                .isEqualTo(new Inspection("application/vnd.ms-excel.sheet.macroEnabled.12", false, true));
    }

    @Test
    void excel4MacroSheetAndBinaryWorkbookCountAsMacros() throws IOException {
        assertThat(inspect("a.xlsx", ooxml("xl/workbook.xml", "xl/macrosheets/sheet1.xml")).containsMacros()).isTrue();
        Inspection binary = inspect("b.xlsx", ooxml("xl/workbook.bin"));
        assertThat(binary.mediaType()).isEqualTo("application/vnd.ms-excel.sheet.binary.macroEnabled.12");
        assertThat(binary.containsMacros()).isTrue();
    }

    @Test
    void remoteTemplateRelationshipCountsButHyperlinkDoesNot() throws IOException {
        String template = RELS_OPEN
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/attachedTemplate\""
                + " Target=\"http://evil.example/t.dotm\" TargetMode=\"External\"/></Relationships>";
        assertThat(inspect("a.docx", ooxmlWith("word/document.xml", "word/_rels/document.xml.rels", template))
                .containsMacros()).isTrue();

        String hyperlink = RELS_OPEN
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink\""
                + " Target=\"https://example.com/a>b\" TargetMode=\"External\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://x/styles\" Target=\"styles.xml\"/></Relationships>";
        assertThat(inspect("b.docx", ooxmlWith("word/document.xml", "word/_rels/document.xml.rels", hyperlink))
                .containsMacros()).isFalse();

        String hidden = RELS_OPEN
                + "<Relationship Id=\"rId1\" Target=\"a>b\" Type=\"http://x/oleObject\" TargetMode = 'External'/></Relationships>";
        assertThat(inspect("c.docx", ooxmlWith("word/document.xml", "word/_rels/document.xml.rels", hidden))
                .containsMacros()).isTrue();
    }

    @Test
    void encryptedOoxmlEntriesAreFlagged() throws IOException {
        byte[] bytes = setEncryptionFlag(ooxml("word/document.xml"));
        Inspection result = inspect("a.docx", bytes);
        assertThat(result.encrypted()).isTrue();
        assertThat(result.mediaType()).isEqualTo(DOCX);
    }

    // ---- ODF ----

    @Test
    void odfPlainAndWithBasicOrScript() throws IOException {
        Map<String, String> plain = new LinkedHashMap<>();
        plain.put("mimetype", "application/vnd.oasis.opendocument.text");
        plain.put("content.xml", "<office:document-content/>");
        plain.put("META-INF/manifest.xml", "<manifest:manifest><manifest:file-entry manifest:full-path=\"content.xml\"/></manifest:manifest>");
        assertThat(inspect("a.odt", zip(plain)))
                .isEqualTo(new Inspection("application/vnd.oasis.opendocument.text", false, false));

        Map<String, String> basic = new LinkedHashMap<>(plain);
        basic.put("Basic/Standard/Module1.xml", "<script/>");
        assertThat(inspect("b.odt", zip(basic)).containsMacros()).isTrue();

        Map<String, String> scripted = new LinkedHashMap<>(plain);
        scripted.put("META-INF/manifest.xml",
                "<manifest:file-entry manifest:full-path=\"x\" manifest:media-type=\"application/vnd.sun.star.script\"/>");
        assertThat(inspect("c.odt", zip(scripted)).containsMacros()).isTrue();
    }

    // ---- OLE2 ----

    @Test
    void ole2TypesAreChosenByDirectoryEntries() throws IOException {
        assertThat(inspect("a.bin", cfb(9, stream("WordDocument", new byte[100]))))
                .isEqualTo(new Inspection("application/msword", false, false));
        assertThat(inspect("b.bin", cfb(9, stream("PowerPoint Document", new byte[100]))))
                .isEqualTo(new Inspection("application/vnd.ms-powerpoint", false, false));
        assertThat(inspect("c.doc", cfb(9, stream("Other", new byte[100]))))
                .isEqualTo(new Inspection("application/x-ole-storage", false, false));
    }

    @Test
    void ole2VbaStorageCountsAsMacros() throws IOException {
        byte[] bytes = cfb(9, stream("WordDocument", new byte[100]), storage("Macros"));
        assertThat(inspect("a.doc", bytes)).isEqualTo(new Inspection("application/msword", false, true));
        assertThat(inspect("b.doc", cfb(9, stream("WordDocument", new byte[10]), storage("VBA"))).containsMacros()).isTrue();
        assertThat(inspect("c.doc", cfb(9, stream("Workbook", workbook(false)), storage("_VBA_PROJECT_CUR"))).containsMacros())
                .isTrue();
    }

    @Test
    void ole2EncryptedPackageIsEncrypted() throws IOException {
        byte[] bytes = cfb(9, stream("EncryptionInfo", new byte[40]), stream("EncryptedPackage", new byte[60]));
        assertThat(inspect("a.xlsx", bytes)).isEqualTo(new Inspection("application/x-ole-storage", true, false));
        byte[] onlyInfo = cfb(9, stream("EncryptionInfo", new byte[40]));
        assertThat(inspect("b.xlsx", onlyInfo).encrypted()).isFalse();
    }

    @Test
    void workbookWithMacroSheetIsMacrosAndPlainWorkbookIsNot() throws IOException {
        assertThat(inspect("a.xls", cfb(9, stream("Workbook", workbook(true)))))
                .isEqualTo(new Inspection("application/vnd.ms-excel", false, true));
        assertThat(inspect("b.xls", cfb(9, stream("Workbook", workbook(false)))))
                .isEqualTo(new Inspection("application/vnd.ms-excel", false, false));
    }

    @Test
    void workbookInLargeSectorsIsReadToo() throws IOException {
        assertThat(inspect("a.xls", cfb(12, stream("Workbook", workbook(true)))).containsMacros()).isTrue();
        assertThat(inspect("b.xls", cfb(12, stream("Workbook", workbook(false)))))
                .isEqualTo(new Inspection("application/vnd.ms-excel", false, false));
    }

    @Test
    void workbookInMiniStreamIsTreatedAsMacros() throws IOException {
        byte[] small = Arrays.copyOf(workbook(false), 600);
        Inspection result = inspect("a.xls", cfb(9, stream("Workbook", small)));
        assertThat(result).isEqualTo(new Inspection("application/vnd.ms-excel", false, true));
    }

    // ---- 限制与损坏 ----

    @Test
    void zipWithTooManyEntriesIsMacros() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        for (int i = 0; i <= ContentInspector.MAX_ENTRIES; i++) {
            entries.put("e" + i, "");
        }
        assertThat(inspect("a.zip", zip(entries))).isEqualTo(new Inspection("application/zip", false, true));
    }

    @Test
    void oversizedScannedEntryIsMacros() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", new byte[ContentInspector.MAX_ENTRY_BYTES + 1]);
        entries.put("word/document.xml", new byte[0]);
        Inspection result = inspect("a.docx", zipBytes(entries));
        assertThat(result.mediaType()).isEqualTo(DOCX);
        assertThat(result.containsMacros()).isTrue();
    }

    @Test
    void cumulativeScanBudgetIsEnforced() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", CONTENT_TYPES.getBytes(StandardCharsets.UTF_8));
        entries.put("word/document.xml", new byte[0]);
        for (int i = 0; i < 5; i++) {
            entries.put("word/_rels/p" + i + ".xml.rels", new byte[ContentInspector.MAX_ENTRY_BYTES - 1]);
        }
        assertThat(inspect("a.docx", zipBytes(entries)).containsMacros()).isTrue();
    }

    @Test
    void truncatedZipAndGarbageOleAreMacros() throws IOException {
        byte[] full = ooxml("word/document.xml");
        Inspection truncated = inspect("a.docx", Arrays.copyOf(full, full.length / 2));
        assertThat(truncated).isEqualTo(new Inspection("application/zip", false, true));

        byte[] garbage = new byte[2048];
        System.arraycopy(new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A,
                (byte) 0xE1}, 0, garbage, 0, 8);
        assertThat(inspect("b.doc", garbage)).isEqualTo(new Inspection("application/x-ole-storage", true, true));
    }

    @Test
    void ole2ChainCycleIsMacros() throws IOException {
        byte[] bytes = cfb(9, stream("WordDocument", new byte[100]));
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(512 + 4, 1);
        assertThat(inspect("a.doc", bytes)).isEqualTo(new Inspection("application/x-ole-storage", true, true));
    }

    @Test
    void aPdfMarkInsideAZipOrOleHeadDoesNotMakeItPdf() throws IOException {
        assertThat(inspect("a.pdf", zipBytes(java.util.Map.of("%PDF-1.7.xml", new byte[1], "word/vbaProject.bin", new byte[1]))).mediaType())
                .isEqualTo("application/zip");
        byte[] ole = cfb(9, storage("VBA"), stream("WordDocument", new byte[100]));
        System.arraycopy("%PDF-".getBytes(), 0, ole, 100, 5);
        assertThat(inspect("b.pdf", ole).containsMacros()).isTrue();
        assertThat(inspect("b.pdf", ole).mediaType()).isNotEqualTo("application/pdf");
    }

    @Test
    void prefixedOrEscapedExternalRelationshipsCount() throws IOException {
        String prefixed = "<x:Relationships xmlns:x=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<x:Relationship Id=\"rId1\" Type=\"http://x/attachedTemplate\" Target=\"http://evil.example/t.dotm\" TargetMode=\"External\"/></x:Relationships>";
        assertThat(inspect("p.docx", ooxmlWith("word/document.xml", "word/_rels/document.xml.rels", prefixed)).containsMacros()).isTrue();
        String escaped = RELS_OPEN
                + "<Relationship Id=\"rId1\" Type=\"http://x/attachedTemplate\" Target=\"http://evil.example/t.dotm\" TargetMode=\"&#69;xternal\"/></Relationships>";
        assertThat(inspect("e.docx", ooxmlWith("word/document.xml", "word/_rels/document.xml.rels", escaped)).containsMacros()).isTrue();
        String internal = RELS_OPEN + "<Relationship Id=\"rId1\" Type=\"http://x/styles\" Target=\"styles.xml\" TargetMode=\"Internal\"/></Relationships>";
        assertThat(inspect("i.docx", ooxmlWith("word/document.xml", "word/_rels/document.xml.rels", internal)).containsMacros()).isFalse();
    }

    @Test
    void missingFileIsMacros() {
        assertThat(inspector.inspect(dir.resolve("nope.bin")))
                .isEqualTo(new Inspection("application/octet-stream", false, true));
    }

    // ---- 样本构造 ----

    private static byte[] zip(Map<String, String> entries) throws IOException {
        Map<String, byte[]> bytes = new LinkedHashMap<>();
        entries.forEach((name, text) -> bytes.put(name, text.getBytes(StandardCharsets.UTF_8)));
        return zipBytes(bytes);
    }

    private static byte[] zipBytes(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static byte[] ooxml(String... parts) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", CONTENT_TYPES);
        for (String part : parts) {
            entries.put(part, "x");
        }
        return zip(entries);
    }

    private static byte[] ooxmlWith(String part, String relsName, String rels) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", CONTENT_TYPES);
        entries.put(part, "x");
        entries.put(relsName, rels);
        return zip(entries);
    }

    private static byte[] setEncryptionFlag(byte[] zip) {
        byte[] copy = zip.clone();
        for (int i = 0; i + 8 < copy.length; i++) {
            boolean central = copy[i] == 'P' && copy[i + 1] == 'K' && copy[i + 2] == 1 && copy[i + 3] == 2;
            boolean local = copy[i] == 'P' && copy[i + 1] == 'K' && copy[i + 2] == 3 && copy[i + 3] == 4;
            if (central) {
                copy[i + 8] |= 1;
            } else if (local) {
                copy[i + 6] |= 1;
            }
        }
        return copy;
    }

    private record Item(String name, int type, byte[] data) {}

    private static Item stream(String name, byte[] data) {
        return new Item(name, 2, data);
    }

    private static Item storage(String name) {
        return new Item(name, 1, new byte[0]);
    }

    private static byte[] record(int id, byte[] data) {
        ByteBuffer buffer = ByteBuffer.allocate(4 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) id).putShort((short) data.length).put(data);
        return buffer.array();
    }

    private static byte[] boundSheet(int sheetType) {
        ByteBuffer data = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(0).put((byte) 0).put((byte) sheetType).put((byte) 2).put((byte) 0).put((byte) 'S').put((byte) '1');
        return record(0x0085, data.array());
    }

    private static byte[] workbook(boolean macroSheet) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(record(0x0809, new byte[16]));
        out.writeBytes(boundSheet(0));
        if (macroSheet) {
            out.writeBytes(boundSheet(1));
        }
        out.writeBytes(record(0x000A, new byte[0]));
        while (out.size() < 5000) {
            out.writeBytes(record(0x1234, new byte[1000]));
        }
        return out.toByteArray();
    }

    // 单个 FAT 扇区、无 mini stream 的最小复合文档：扇区 0 为 FAT，随后目录，再后各流
    private static byte[] cfb(int shift, Item... items) {
        int sectorSize = 1 << shift;
        int entryCount = 1 + items.length;
        int directorySectors = (entryCount * 128 + sectorSize - 1) / sectorSize;
        List<Integer> chainLengths = new ArrayList<>();
        int total = 1 + directorySectors;
        for (Item item : items) {
            int length = (item.data().length + sectorSize - 1) / sectorSize;
            chainLengths.add(length);
            total += length;
        }
        int[] fat = new int[sectorSize / 4];
        Arrays.fill(fat, -1);
        fat[0] = -3;
        for (int i = 0; i < directorySectors; i++) {
            fat[1 + i] = i == directorySectors - 1 ? -2 : 2 + i;
        }
        int[] starts = new int[items.length];
        int next = 1 + directorySectors;
        for (int i = 0; i < items.length; i++) {
            int length = chainLengths.get(i);
            starts[i] = length == 0 ? -2 : next;
            for (int j = 0; j < length; j++) {
                fat[next + j] = j == length - 1 ? -2 : next + j + 1;
            }
            next += length;
        }

        ByteBuffer file = ByteBuffer.allocate((total + 1) * sectorSize).order(ByteOrder.LITTLE_ENDIAN);
        file.put(new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1});
        file.putShort(24, (short) 0x3E).putShort(26, (short) (shift == 9 ? 3 : 4)).putShort(28, (short) 0xFFFE)
                .putShort(30, (short) shift).putShort(32, (short) 6);
        file.putInt(44, 1).putInt(48, 1).putInt(56, 4096).putInt(60, -2).putInt(64, 0).putInt(68, -2).putInt(72, 0);
        for (int i = 0; i < 109; i++) {
            file.putInt(76 + 4 * i, i == 0 ? 0 : -1);
        }
        for (int i = 0; i < fat.length; i++) {
            file.putInt(sectorSize + 4 * i, fat[i]);
        }
        int directoryBase = 2 * sectorSize;
        writeEntry(file, directoryBase, "Root Entry", 5, -2, 0);
        for (int i = 0; i < items.length; i++) {
            writeEntry(file, directoryBase + (i + 1) * 128, items[i].name(), items[i].type(), starts[i],
                    items[i].data().length);
        }
        int dataBase = (2 + directorySectors) * sectorSize;
        int offset = dataBase;
        for (int i = 0; i < items.length; i++) {
            file.put(offset, items[i].data());
            offset += chainLengths.get(i) * sectorSize;
        }
        return file.array();
    }

    private static void writeEntry(ByteBuffer file, int position, String name, int type, int start, long size) {
        byte[] encoded = name.getBytes(StandardCharsets.UTF_16LE);
        file.put(position, encoded);
        file.putShort(position + 64, (short) (encoded.length + 2));
        file.put(position + 66, (byte) type);
        file.put(position + 67, (byte) 1);
        file.putInt(position + 68, -1).putInt(position + 72, -1).putInt(position + 76, -1);
        file.putInt(position + 116, start);
        file.putLong(position + 120, size);
    }
}
