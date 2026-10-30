package vn.fonesi.rebuild.catalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import vn.fonesi.rebuild.model.ManagerModel;

/** Executes the same bounded UTF8 decoder used by the native reader. No Android runtime claim. */
public final class CatalogPageTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    private static void rejects(byte[] bytes,int max) throws Exception {
        checks++;
        try { CatalogPage.decodeText(bytes,max); } catch (IOException expected) { return; }
        throw new AssertionError("expected strict rejection");
    }
    public static void main(String[] args) throws Exception {
        for (String exact : new String[]{"", " ", "\u0000", "A\u0000B", "é", "e\u0301", "😀", "\uFEFF", "engine:opaque/id", "\n\t"}) {
            byte[] input=exact.getBytes(StandardCharsets.UTF_8);
            String result=CatalogPage.decodeText(input,4096);
            check(result.equals(exact));
            check(Arrays.equals(input,result.getBytes(StandardCharsets.UTF_8)));
            Arrays.fill(input,(byte)42);
            check(result.equals(exact));
        }
        for (byte[] invalid : new byte[][]{{(byte)0x80},{(byte)0xc0,(byte)0x80},{(byte)0xc2},
                {(byte)0xe2,(byte)0x82},{(byte)0xed,(byte)0xa0,(byte)0x80},
                {(byte)0xf4,(byte)0x90,(byte)0x80,(byte)0x80},{(byte)0xff},
                {(byte)0xf0,(byte)0x9f,(byte)0x98}, {(byte)0xe2,0x28,(byte)0xa1}}) rejects(invalid,4096);
        rejects(null,4096); rejects(new byte[16385],4096); rejects(new byte[0],-1); rejects(new byte[0],4097);
        check(CatalogPage.decodeText(new byte[0],0).isEmpty());
        rejects(new byte[]{65},0);
        check(CatalogPage.decodeText(new byte[4096],4096).length()==4096);
        rejects(new byte[4097],4096);
        String supplementary="😀".repeat(2048);
        check(CatalogPage.decodeText(supplementary.getBytes(StandardCharsets.UTF_8),4096).equals(supplementary));
        rejects(supplementary.getBytes(StandardCharsets.UTF_8),4095);
        String wide="\u0800".repeat(4096);
        check(CatalogPage.decodeText(wide.getBytes(StandardCharsets.UTF_8),4096).equals(wide));
        rejects((wide+"x").getBytes(StandardCharsets.UTF_8),4096);
        CatalogPage.Position cursor=new CatalogPage.Position("opaque catalog",Long.MAX_VALUE,Long.MIN_VALUE);
        check(cursor.catalogId.equals("opaque catalog") && cursor.revision==Long.MAX_VALUE && cursor.lastRowId==Long.MIN_VALUE);
        for (long row : new long[]{Long.MIN_VALUE,-1,0,1,Long.MAX_VALUE}) check(new CatalogPage.Position("id",0,row).lastRowId==row);
        checks++; try { new CatalogPage.Position(null,0,0); throw new AssertionError(); } catch (IllegalArgumentException expected) { }
        checks++; try { new CatalogPage.Position("id",-1,0); throw new AssertionError(); } catch (IllegalArgumentException expected) { }
        List<ManagerModel.Entry> original=new ArrayList<>();
        ManagerModel.Entry entry=new ManagerModel.Entry(new ManagerModel.CatalogKey("", " "),"example.app","Name","",ManagerModel.Status.UNVERIFIED);
        original.add(entry);
        CatalogPage page=new CatalogPage(original,"id",0,cursor);
        original.clear();
        check(page.entries.size()==1 && page.entries.get(0)==entry && page.next==cursor);
        checks++; try { page.entries.clear(); throw new AssertionError(); } catch (UnsupportedOperationException expected) { }
        check(page.catalogId.equals("id") && page.revision==0);
        if (checks<60) throw new AssertionError("missing checks");
        System.out.println("CatalogPageTest: "+checks+" checks passed");
    }
}
