package vn.fonesi.rebuild.catalog;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import vn.fonesi.rebuild.model.ManagerModel;

/** Display-only immutable page; no provider or executable operation authority. */
public final class CatalogPage {
    /** Bounded owned input, strict scalar UTF8; no replacement or normalization. */
    public static String decodeText(byte[] input, int utf16Max) throws IOException {
        if (input == null || input.length > 16384 || utf16Max < 0 || utf16Max > 4096)
            throw new IOException("Catalog text outside read budget");
        byte[] owned = input.clone();
        try {
            String value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(owned)).toString();
            if (value.length() > utf16Max || !Arrays.equals(owned, value.getBytes(StandardCharsets.UTF_8)))
                throw new IOException("Catalog text outside read budget");
            return value;
        } catch (CharacterCodingException invalid) {
            throw new IOException("Catalog text is not strict UTF8");
        }
    }
    public static final class Position {
        public final String catalogId;
        public final long revision;
        public final long lastRowId;
        public Position(String catalogId, long revision, long lastRowId) {
            if (catalogId == null || revision < 0) throw new IllegalArgumentException("Invalid catalog position");
            this.catalogId = catalogId;
            this.revision = revision;
            this.lastRowId = lastRowId;
        }
    }
    public final List<ManagerModel.Entry> entries;
    public final String catalogId;
    public final long revision;
    public final Position next;
    CatalogPage(List<ManagerModel.Entry> entries, String catalogId, long revision, Position next) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        this.catalogId = catalogId;
        this.revision = revision;
        this.next = next;
    }
}
