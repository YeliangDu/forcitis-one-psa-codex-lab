package com.forcitis.one.platform.file.infrastructure.content;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.channels.FileChannel;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 按内容（魔数与结构）判定媒体类型，并补充 clamd 覆盖不到的加密结构与含宏识别（ADR-0070 决定 4 第 3 条、决定 5 第 1–2 条）。
 *
 * <p>只读、不执行内容；XML 只做子串或正则扫描，不用解析器，因此不解析外部实体。
 * 任何解析异常、超限一律 containsMacros=true；OLE 结构读不出时另按 encrypted=true（无法确认未加密），媒体类型能判就判。
 *
 * <p>PDF 只在空用户口令打不开时算加密（只设权限口令的照常扫描）；7z 按头部是否出现 AES 编码器判加密，压缩头里看不到时由引擎判。
 */
final class ContentInspector {

    record Inspection(String mediaType, boolean encrypted, boolean containsMacros) {}

    static final int MAX_ENTRIES = 10_000;
    static final int MAX_ENTRY_BYTES = 8 * 1024 * 1024;
    static final int MAX_TOTAL_BYTES = 32 * 1024 * 1024;
    static final int MAX_CENTRAL_DIRECTORY_BYTES = 16 * 1024 * 1024;
    // At least the largest scannable upload (2 GB): an OLE file the parser refuses could hide an EncryptedPackage.
    static final long MAX_OLE_BYTES = 2L * 1024 * 1024 * 1024;
    static final int MAX_CHAIN_SECTORS = 1 << 17;
    static final int MAX_STREAM_BYTES = 32 * 1024 * 1024;

    private static final int PDF_SCAN_BYTES = 1024 * 1024;
    static final long MAX_PDF_BYTES = 128L * 1024 * 1024;
    private static final byte[] AES_CODER = {0x06, (byte) 0xF1, 0x07, 0x01};
    private static final int HEAD_BYTES = 1024;
    private static final int OLE_DIRECTORY_ENTRY_BYTES = 128;
    private static final int OLE_MINI_STREAM_CUTOFF = 4096;
    private static final int OLE_END_OF_CHAIN = -2;
    private static final int BIFF_BOUNDSHEET = 0x0085;
    private static final int BIFF_SHEET_TYPE_MACRO = 0x01;
    private static final int BIFF_SHEET_TYPE_VBA_MODULE = 0x06;

    private static final String OCTET = "application/octet-stream";
    private static final String ZIP = "application/zip";
    private static final String OLE_STORAGE = "application/x-ole-storage";
    private static final String OOXML_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String OOXML_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String OOXML_PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    private static final String OOXML_DOCM = "application/vnd.ms-word.document.macroEnabled.12";
    private static final String OOXML_XLSM = "application/vnd.ms-excel.sheet.macroEnabled.12";
    private static final String OOXML_PPTM = "application/vnd.ms-powerpoint.presentation.macroEnabled.12";
    private static final String OOXML_XLSB = "application/vnd.ms-excel.sheet.binary.macroEnabled.12";

    private static final byte[] ENCRYPT_MARK = "/Encrypt".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] PDF_MARK = "%PDF-".getBytes(StandardCharsets.ISO_8859_1);
    private static final Pattern RELATIONSHIP_ELEMENT = Pattern.compile("<(?:[\\w.-]+:)?Relationship\\s(?:[^>\"']++|\"[^\"]*+\"|'[^']*+')*+>");
    private static final Pattern TARGET_MODE = Pattern.compile("TargetMode\\s*=\\s*[\"']([^\"']*)[\"']");
    private static final Pattern CHARACTER_REFERENCE = Pattern.compile("&#(?:[xX]([0-9a-fA-F]{1,6})|([0-9]{1,7}));");
    private static final Pattern HYPERLINK_TYPE = Pattern.compile("Type\\s*=\\s*[\"'][^\"']*/hyperlink[\"']");

    private enum Kind {
        PDF("application/pdf"), PNG("image/png"), JPEG("image/jpeg"), GIF("image/gif"), ZIP(ContentInspector.ZIP),
        OLE(OLE_STORAGE), SEVEN_ZIP("application/x-7z-compressed"), TEXT("text/plain"), OTHER(OCTET);

        private final String mediaType;

        Kind(String mediaType) {
            this.mediaType = mediaType;
        }
    }

    Inspection inspect(Path file) {
        Kind kind;
        try {
            kind = sniff(file);
        } catch (IOException | RuntimeException e) {
            return new Inspection(OCTET, false, true);
        }
        try {
            return switch (kind) {
                case PDF -> inspectPdf(file);
                case ZIP -> inspectZip(file);
                case OLE -> inspectOle(file);
                case SEVEN_ZIP -> inspectSevenZip(file);
                default -> new Inspection(kind.mediaType, false, false);
            };
        } catch (IOException | RuntimeException | StackOverflowError e) {
            // An OLE, 7z or PDF file whose structure cannot be read may hide encryption: refused as unconfirmable, never passed.
            return new Inspection(kind.mediaType, kind == Kind.OLE || kind == Kind.SEVEN_ZIP || kind == Kind.PDF, true);
        }
    }

    private static Kind sniff(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long size = channel.size();
            byte[] head = readAt(channel, 0, (int) Math.min(size, HEAD_BYTES));
            if (startsWith(head, 0x89, 'P', 'N', 'G')) {
                return Kind.PNG;
            }
            if (startsWith(head, 0xFF, 0xD8, 0xFF)) {
                return Kind.JPEG;
            }
            if (startsWith(head, 'G', 'I', 'F', '8')) {
                return Kind.GIF;
            }
            if (startsWith(head, 'P', 'K')) {
                return Kind.ZIP;
            }
            if (startsWith(head, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) {
                return Kind.OLE;
            }
            if (startsWith(head, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C)) {
                return Kind.SEVEN_ZIP;
            }
            // Only after the offset-0 magic numbers: a ZIP or OLE file can carry %PDF- in its first kilobyte to pass as PDF.
            if (indexOf(head, PDF_MARK) >= 0) {
                return Kind.PDF;
            }
            return isUtf8Text(channel, size) ? Kind.TEXT : Kind.OTHER;
        }
    }

    private static boolean isUtf8Text(FileChannel channel, long size) throws IOException {
        if (size == 0) {
            return false;
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer in = ByteBuffer.allocate(64 * 1024);
        CharBuffer out = CharBuffer.allocate(64 * 1024);
        channel.position(0);
        boolean endOfInput = false;
        while (!endOfInput) {
            endOfInput = channel.read(in) < 0;
            in.flip();
            for (int i = in.position(); i < in.limit(); i++) {
                if (in.get(i) == 0) {
                    return false;
                }
            }
            CoderResult result = decoder.decode(in, out, endOfInput);
            if (result.isError()) {
                return false;
            }
            out.clear();
            in.compact();
        }
        return true;
    }

    /**
     * A PDF is encrypted for this check only when the empty user password does not open it: one with only a permissions password is
     * scanned like any other (ADR-0070 section 6 item 1; one-architecture ruling 2026-10-21).
     */
    private static Inspection inspectPdf(Path file) throws IOException {
        long size = Files.size(file);
        if (size > MAX_PDF_BYTES) {
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
                boolean marked = indexOf(readAt(channel, 0, PDF_SCAN_BYTES), ENCRYPT_MARK) >= 0
                        || indexOf(readAt(channel, size - PDF_SCAN_BYTES, PDF_SCAN_BYTES), ENCRYPT_MARK) >= 0;
                return new Inspection(Kind.PDF.mediaType, marked, false);
            }
        }
        Optional<Boolean> opens = PdfOpenPassword.opensWithoutPassword(Files.readAllBytes(file));
        return new Inspection(Kind.PDF.mediaType, opens.isPresent() && !opens.get(), false);
    }

    /**
     * Encrypted when the archive header names the AES coder (06F10701): an encoded header carries it when the header itself is
     * encrypted, a plain header when the content is. A header compressed without AES hides the content's coders; then the engine's own
     * encrypted alert decides (ADR-0070 section 4 item 3; one-architecture ruling 2026-10-21).
     */
    private static Inspection inspectSevenZip(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            ByteBuffer start = ByteBuffer.wrap(readAt(channel, 0, 32)).order(ByteOrder.LITTLE_ENDIAN);
            long offset = start.getLong(12);
            long length = start.getLong(20);
            if (offset < 0 || length <= 0 || length > MAX_STREAM_BYTES || offset > channel.size() - 32 - length) {
                return new Inspection(Kind.SEVEN_ZIP.mediaType, true, false);
            }
            byte[] header = readAt(channel, 32 + offset, (int) length);
            return new Inspection(Kind.SEVEN_ZIP.mediaType, indexOf(header, AES_CODER) >= 0, false);
        }
    }

    // ---- zip / OOXML / ODF ----

    private record Part(String name, int flags) {}

    private static final class Budget {
        private int remaining = MAX_TOTAL_BYTES;
    }

    private static Inspection inspectZip(Path file) throws IOException {
        List<Part> parts;
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            parts = readCentralDirectory(channel, channel.size());
        }
        boolean encrypted = false;
        Set<String> names = new HashSet<>();
        for (Part part : parts) {
            names.add(part.name());
            if ((part.flags() & 1) != 0) {
                encrypted = true;
            }
        }
        // ZipFile 打开含加密条目的包会直接抛异常；加密包的内容读出来也是密文，只按条目名判定
        try (ZipFile zip = encrypted ? null : new ZipFile(file.toFile())) {
            if (names.contains("[Content_Types].xml")) {
                return inspectOoxml(zip, names, encrypted);
            }
            if (names.contains("mimetype")) {
                return inspectOpenDocument(zip, names, encrypted);
            }
        }
        return new Inspection(ZIP, encrypted, false);
    }

    private static List<Part> readCentralDirectory(FileChannel channel, long size) throws IOException {
        int tail = (int) Math.min(size, 22 + 65535);
        byte[] end = readAt(channel, size - tail, tail);
        int eocd = -1;
        for (int i = tail - 22; i >= 0; i--) {
            if (u32(end, i) == 0x06054b50L) {
                eocd = i;
                break;
            }
        }
        if (eocd < 0) {
            throw new IllegalStateException("no end of central directory");
        }
        int count = u16(end, eocd + 10);
        long directorySize = u32(end, eocd + 12);
        long directoryOffset = u32(end, eocd + 16);
        if (count > MAX_ENTRIES || directorySize > MAX_CENTRAL_DIRECTORY_BYTES
                || directoryOffset + directorySize > size) {
            throw new IllegalStateException("limit exceeded");
        }
        byte[] directory = readAt(channel, directoryOffset, (int) directorySize);
        List<Part> parts = new ArrayList<>(count);
        int pos = 0;
        for (int i = 0; i < count; i++) {
            if (pos + 46 > directory.length || u32(directory, pos) != 0x02014b50L) {
                throw new IllegalStateException("bad central directory");
            }
            int flags = u16(directory, pos + 8);
            int nameLength = u16(directory, pos + 28);
            int extraLength = u16(directory, pos + 30);
            int commentLength = u16(directory, pos + 32);
            int next = pos + 46 + nameLength + extraLength + commentLength;
            if (next > directory.length) {
                throw new IllegalStateException("bad central directory");
            }
            parts.add(new Part(new String(directory, pos + 46, nameLength, StandardCharsets.UTF_8), flags));
            pos = next;
        }
        return parts;
    }

    private static Inspection inspectOoxml(ZipFile zip, Set<String> names, boolean encrypted) {
        boolean vbaPart = false;
        boolean macros = false;
        for (String name : names) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith("vbaproject.bin")) {
                vbaPart = true;
            }
            if (lower.contains("xl/macrosheets/")) {
                macros = true;
            }
        }
        boolean macroType = false;
        Budget budget = new Budget();
        try {
            Enumeration<? extends ZipEntry> entries = zip == null ? Collections.emptyEnumeration() : zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory()) {
                    continue;
                }
                if (name.equals("[Content_Types].xml")) {
                    macroType |= readText(zip, entry, budget).contains("macroEnabled");
                } else if (name.endsWith(".rels")) {
                    macros |= hasForeignExternalRelationship(readText(zip, entry, budget));
                }
            }
        } catch (IOException | RuntimeException e) {
            macros = true;
        }
        // xlsb 的宏表与加载项在二进制部件里，不解析 BIFF12，一律按含宏
        boolean binaryWorkbook = names.contains("xl/workbook.bin");
        boolean macroEnabled = vbaPart || macroType;
        String type = binaryWorkbook ? OOXML_XLSB : ooxmlType(names, macroEnabled);
        return new Inspection(type, encrypted, macros || macroEnabled || binaryWorkbook);
    }

    private static String ooxmlType(Set<String> names, boolean macroEnabled) {
        if (names.stream().anyMatch(n -> n.startsWith("word/"))) {
            return macroEnabled ? OOXML_DOCM : OOXML_DOCX;
        }
        if (names.stream().anyMatch(n -> n.startsWith("xl/"))) {
            return macroEnabled ? OOXML_XLSM : OOXML_XLSX;
        }
        if (names.stream().anyMatch(n -> n.startsWith("ppt/"))) {
            return macroEnabled ? OOXML_PPTM : OOXML_PPTX;
        }
        return ZIP;
    }

    // 超链接是普通外部关系，不是远程模板注入；其余类型的外部关系一律按注入处理
    private static boolean hasForeignExternalRelationship(String rawXml) {
        String xml = decodeCharacterReferences(rawXml);
        Matcher element = RELATIONSHIP_ELEMENT.matcher(xml);
        int modes = 0;
        while (element.find()) {
            Matcher mode = TARGET_MODE.matcher(element.group());
            if (!mode.find()) {
                continue;
            }
            modes++;
            if (!mode.group(1).equals("Internal") && !HYPERLINK_TYPE.matcher(element.group()).find()) {
                return true;
            }
        }
        // A TargetMode outside any element this reader recognises is not understood, so it counts as external.
        Matcher anywhere = TARGET_MODE.matcher(xml);
        int all = 0;
        while (anywhere.find()) {
            all++;
        }
        return all != modes;
    }

    private static String decodeCharacterReferences(String xml) {
        Matcher reference = CHARACTER_REFERENCE.matcher(xml);
        StringBuilder decoded = new StringBuilder();
        while (reference.find()) {
            int codePoint;
            try {
                codePoint = reference.group(1) != null ? Integer.parseInt(reference.group(1), 16) : Integer.parseInt(reference.group(2));
            } catch (NumberFormatException e) {
                codePoint = '?';
            }
            reference.appendReplacement(decoded, Matcher.quoteReplacement(new String(Character.toChars(Character.isValidCodePoint(codePoint) ? codePoint : '?'))));
        }
        reference.appendTail(decoded);
        return decoded.toString();
    }

    private static Inspection inspectOpenDocument(ZipFile zip, Set<String> names, boolean encrypted) {
        boolean macros = names.stream().anyMatch(n -> n.startsWith("Basic/") || n.contains("/Basic/")
                || n.startsWith("Scripts/"));
        String type = ZIP;
        Budget budget = new Budget();
        try {
            if (zip != null) {
                String mimetype = readNamed(zip, "mimetype", budget).trim();
                if (mimetype.startsWith("application/vnd.oasis.opendocument")) {
                    type = mimetype;
                }
            }
            if (zip != null && names.contains("META-INF/manifest.xml")) {
                macros |= readNamed(zip, "META-INF/manifest.xml", budget).toLowerCase(Locale.ROOT)
                        .contains("script");
            }
        } catch (IOException | RuntimeException e) {
            macros = true;
        }
        return new Inspection(type, encrypted, macros);
    }

    private static String readNamed(ZipFile zip, String name, Budget budget) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            throw new IOException("missing " + name);
        }
        return readText(zip, entry, budget);
    }

    private static String readText(ZipFile zip, ZipEntry entry, Budget budget) throws IOException {
        int cap = Math.min(MAX_ENTRY_BYTES, budget.remaining);
        try (InputStream in = zip.getInputStream(entry)) {
            byte[] bytes = in.readNBytes(cap + 1);
            if (bytes.length > cap) {
                throw new IllegalStateException("limit exceeded");
            }
            budget.remaining -= bytes.length;
            boolean utf16 = bytes.length >= 2 && ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE
                    || (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF);
            return new String(bytes, utf16 ? StandardCharsets.UTF_16 : StandardCharsets.ISO_8859_1);
        }
    }

    // ---- OLE2 ----

    private static Inspection inspectOle(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            CompoundFile compound = new CompoundFile(channel, channel.size());
            Set<String> names = new HashSet<>();
            CompoundFile.Entry workbook = null;
            for (CompoundFile.Entry entry : compound.entries) {
                String name = entry.name().toLowerCase(Locale.ROOT);
                names.add(name);
                if (name.equals("workbook") || name.equals("book")) {
                    workbook = entry;
                }
            }
            boolean encrypted = names.contains("encryptioninfo") && names.contains("encryptedpackage");
            String type = OLE_STORAGE;
            if (names.contains("worddocument")) {
                type = "application/msword";
            } else if (workbook != null) {
                type = "application/vnd.ms-excel";
            } else if (names.contains("powerpoint document")) {
                type = "application/vnd.ms-powerpoint";
            }
            boolean macros = names.stream().anyMatch(n -> n.equals("vba") || n.startsWith("_vba_project")
                    || n.equals("macros"));
            if (!macros && workbook != null) {
                try {
                    // 不支持 mini stream：小于 4096 字节的 Workbook 读不出来，按含宏
                    macros = hasMacroSheet(compound.readStream(workbook, MAX_STREAM_BYTES));
                } catch (IOException | RuntimeException e) {
                    macros = true;
                }
            }
            return new Inspection(type, encrypted, macros);
        }
    }

    private static boolean hasMacroSheet(byte[] stream) {
        int pos = 0;
        while (pos + 4 <= stream.length) {
            int id = u16(stream, pos);
            int length = u16(stream, pos + 2);
            int data = pos + 4;
            if (data + length > stream.length) {
                return false;
            }
            if (id == BIFF_BOUNDSHEET && length >= 6) {
                int sheetType = stream[data + 5] & 0xFF;
                if (sheetType == BIFF_SHEET_TYPE_MACRO || sheetType == BIFF_SHEET_TYPE_VBA_MODULE) {
                    return true;
                }
            }
            pos = data + length;
        }
        return false;
    }

    private static final class CompoundFile {

        record Entry(String name, int type, int start, long size) {}

        private final FileChannel channel;
        private final int sectorSize;
        private final long sectorCount;
        private final int[] fat;
        final List<Entry> entries = new ArrayList<>();

        CompoundFile(FileChannel channel, long fileSize) throws IOException {
            if (fileSize > MAX_OLE_BYTES || fileSize < 512) {
                throw new IllegalStateException("size");
            }
            this.channel = channel;
            ByteBuffer header = ByteBuffer.wrap(readAt(channel, 0, 512)).order(ByteOrder.LITTLE_ENDIAN);
            int shift = header.getShort(30) & 0xFFFF;
            if (header.getShort(28) != (short) 0xFFFE || (shift != 9 && shift != 12)) {
                throw new IllegalStateException("bad header");
            }
            sectorSize = 1 << shift;
            sectorCount = fileSize / sectorSize - 1;
            int fatCount = header.getInt(44);
            int firstDirectory = header.getInt(48);
            int difatStart = header.getInt(68);
            int difatCount = header.getInt(72);
            if (fatCount < 0 || fatCount > sectorCount) {
                throw new IllegalStateException("bad fat");
            }
            int[] fatSectors = new int[fatCount];
            int found = 0;
            for (int i = 0; i < 109 && found < fatCount; i++) {
                fatSectors[found++] = header.getInt(76 + 4 * i);
            }
            int next = difatStart;
            for (int d = 0; found < fatCount; d++) {
                if (d >= difatCount || !valid(next)) {
                    throw new IllegalStateException("bad difat");
                }
                ByteBuffer sector = sector(next);
                int perSector = sectorSize / 4 - 1;
                for (int j = 0; j < perSector && found < fatCount; j++) {
                    fatSectors[found++] = sector.getInt(4 * j);
                }
                next = sector.getInt(4 * perSector);
            }
            int perFat = sectorSize / 4;
            fat = new int[fatCount * perFat];
            for (int i = 0; i < fatCount; i++) {
                if (!valid(fatSectors[i])) {
                    throw new IllegalStateException("bad fat sector");
                }
                ByteBuffer sector = sector(fatSectors[i]);
                for (int j = 0; j < perFat; j++) {
                    fat[i * perFat + j] = sector.getInt(4 * j);
                }
            }
            readDirectory(firstDirectory);
        }

        private void readDirectory(int firstDirectory) throws IOException {
            byte[] directory = readChain(firstDirectory, Long.MAX_VALUE);
            ByteBuffer buffer = ByteBuffer.wrap(directory).order(ByteOrder.LITTLE_ENDIAN);
            for (int pos = 0; pos + OLE_DIRECTORY_ENTRY_BYTES <= directory.length; pos += OLE_DIRECTORY_ENTRY_BYTES) {
                int type = directory[pos + 66] & 0xFF;
                if (type == 0) {
                    continue;
                }
                int nameLength = buffer.getShort(pos + 64) & 0xFFFF;
                if (nameLength < 2 || nameLength > 64) {
                    throw new IllegalStateException("bad directory entry");
                }
                String name = new String(directory, pos, nameLength - 2, StandardCharsets.UTF_16LE);
                long size = buffer.getLong(pos + 120);
                if (sectorSize == 512) {
                    size &= 0xFFFFFFFFL;
                }
                entries.add(new Entry(name, type, buffer.getInt(pos + 116), size));
            }
        }

        byte[] readStream(Entry entry, int cap) throws IOException {
            if (entry.size() < OLE_MINI_STREAM_CUTOFF || entry.size() > cap) {
                throw new IllegalStateException("mini stream or too large");
            }
            byte[] data = readChain(entry.start(), entry.size());
            if (data.length < entry.size()) {
                throw new IllegalStateException("short stream");
            }
            return Arrays.copyOf(data, (int) entry.size());
        }

        private byte[] readChain(int start, long byteLimit) throws IOException {
            long wanted = byteLimit == Long.MAX_VALUE ? Long.MAX_VALUE : (byteLimit + sectorSize - 1) / sectorSize;
            BitSet seen = new BitSet();
            List<ByteBuffer> sectors = new ArrayList<>();
            int current = start;
            while (current != OLE_END_OF_CHAIN && sectors.size() < wanted) {
                if (!valid(current) || current >= fat.length || seen.get(current)
                        || sectors.size() >= MAX_CHAIN_SECTORS) {
                    throw new IllegalStateException("bad chain");
                }
                seen.set(current);
                sectors.add(sector(current));
                current = fat[current];
            }
            byte[] result = new byte[sectors.size() * sectorSize];
            for (int i = 0; i < sectors.size(); i++) {
                sectors.get(i).get(0, result, i * sectorSize, sectorSize);
            }
            return result;
        }

        private boolean valid(int id) {
            return id >= 0 && id < sectorCount;
        }

        private ByteBuffer sector(int id) throws IOException {
            return ByteBuffer.wrap(readAt(channel, (id + 1L) * sectorSize, sectorSize)).order(ByteOrder.LITTLE_ENDIAN);
        }
    }

    // ---- bytes ----

    private static byte[] readAt(FileChannel channel, long position, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        while (buffer.hasRemaining()) {
            int n = channel.read(buffer, position + buffer.position());
            if (n < 0) {
                throw new IOException("unexpected end of file");
            }
        }
        return buffer.array();
    }

    private static boolean startsWith(byte[] data, int... prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((data[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static int indexOf(byte[] data, byte[] pattern) {
        outer:
        for (int i = 0; i + pattern.length <= data.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static int u16(byte[] data, int pos) {
        return (data[pos] & 0xFF) | (data[pos + 1] & 0xFF) << 8;
    }

    private static long u32(byte[] data, int pos) {
        return (u16(data, pos) | (long) u16(data, pos + 2) << 16) & 0xFFFFFFFFL;
    }
}
