package vn.fonesi.rebuild.logical;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static vn.fonesi.rebuild.logical.OperationProtocol.*;
import static vn.fonesi.rebuild.logical.CapabilityPolicy.*;

/** Independent byte recipes and invariants plus compact deterministic parity observations. */
public final class CodecContractTest {
    private CodecContractTest() {}
    private static final UUID OP=new UUID(0,1), A=new UUID(0,2);
    private static final String DIGEST="a".repeat(64);
    private static final List<UUID> PEERS=Arrays.asList(new UUID(Long.MIN_VALUE,0),new UUID(0,Long.MIN_VALUE),A);
    private static final String[] TEXT={"p","r","a.b","e","SYNTHETIC",DIGEST,"PREPARED"};
    private static final SortedMap<String,String> OUTPUT=new TreeMap<>();
    private static int checks;
    private interface Action { Object run() throws Exception; }
    private static void check(boolean ok) { checks++; if(!ok) throw new AssertionError("Codec check "+checks); }
    private static void put(String key,String value) { if(OUTPUT.put(key,value)!=null) throw new AssertionError("Duplicate key"); }
    private static String hex(byte[] bytes) {
        StringBuilder text=new StringBuilder(); for(byte value:bytes) text.append("0123456789abcdef".charAt((value&255)>>>4)).append("0123456789abcdef".charAt(value&15)); return text.toString();
    }
    private static String sha(byte[] bytes) throws Exception { return hex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static byte[] seal(byte[] body) throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream(); output.write(body,0,body.length);
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(body); output.write(digest,0,digest.length); return output.toByteArray();
    }
    private static byte[] utf(String text) throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); try(DataOutputStream out=new DataOutputStream(bytes)){out.writeUTF(text);} return Arrays.copyOfRange(bytes.toByteArray(),2,bytes.size());
    }
    private static void uuid(DataOutputStream out,UUID id) throws Exception { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); }
    private static byte[] envelope(Map<Integer,byte[]> replacements,List<UUID> peers,int count,int version,long revision,boolean trailing) throws Exception {
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(body)) {
            out.writeInt(0x464f4e32);out.writeInt(version);uuid(out,OP);
            for(int field=0;field<7;field++) {
                if(field==5) uuid(out,A);
                if(field==6) {out.writeInt(count);for(UUID peer:peers) uuid(out,peer);}
                byte[] bytes=replacements.containsKey(field)?replacements.get(field):utf(TEXT[field]);
                out.writeShort(bytes.length);out.write(bytes);
            }
            out.writeLong(revision);if(trailing)out.writeByte(0);
        } return seal(body.toByteArray());
    }
    private static Identity identity(String provider,String revision,String env,Set<UUID> peers) {
        return new Identity(OP,new Provider(provider,revision),"a.b",env,Scope.SYNTHETIC,A,DIGEST,peers);
    }
    private static void reject(String key,Action action,Class<? extends Exception> expected) throws Exception {
        Exception failure=null;
        try{action.run();}catch(Exception e){failure=e;}
        check(failure!=null && expected.isInstance(failure));
        put(key,failure.getClass().getSimpleName());
    }
    private static void invalid(String key,byte[] bytes) throws Exception {
        byte[] before=bytes.clone();reject(key,()->CodecAdapter.decode(bytes,OP),IOException.class);check(Arrays.equals(before,bytes));OUTPUT.put(key,OUTPUT.get(key)+":"+sha(bytes));
    }
    private static byte[] valid(String key,Operation value,byte[] known,boolean full) throws Exception {
        byte[] encoded=CodecAdapter.encode(value); if(known!=null)check(Arrays.equals(encoded,known));
        byte[] input=encoded.clone();Operation decoded=CodecAdapter.decode(input,OP);check(decoded.equals(value));
        check(Arrays.equals(encoded,CodecAdapter.encode(decoded)));Arrays.fill(input,(byte)0);
        check(Arrays.equals(encoded,CodecAdapter.encode(decoded)));
        byte[] modified=encoded.clone();Arrays.fill(modified,(byte)0);check(Arrays.equals(encoded,CodecAdapter.encode(value)));
        // Mutate the actual returned encoding, not only a clone, then require a fresh unaffected value.
        byte[] returned=CodecAdapter.encode(value);Arrays.fill(returned,(byte)0);check(Arrays.equals(encoded,CodecAdapter.encode(value)));
        try{decoded.identity().affectedClones().clear();throw new AssertionError("Mutable decoded peers");}catch(UnsupportedOperationException expected){checks++;}
        put(key+".bytes",Integer.toString(encoded.length));put(key+".sha256",sha(encoded));if(full)put(key+".hex",hex(encoded));return encoded;
    }
    public static void main(String[] args) throws Exception {
        check(hex(utf("p\ud83d\ude00")).equals("70eda0bdedb880"));
        check(PEERS.get(0).compareTo(PEERS.get(1))<0 && PEERS.get(1).compareTo(A)<0);
        Set<UUID> mutable=new HashSet<>(PEERS);Identity id=identity("p","r","e",mutable);mutable.clear();check(id.affectedClones().size()==3);
        byte[] ascii=valid("valid.ascii",Operation.prepare(id),envelope(Collections.emptyMap(),PEERS,3,2,0,false),true);
        Operation unicode=Operation.prepare(identity("p\ud83d\ude00","r\ud834\udd1e","e\ud83d\ude80",new HashSet<>(PEERS)));
        Map<Integer,byte[]> unicodeFields=new HashMap<>();unicodeFields.put(0,utf("p\ud83d\ude00"));unicodeFields.put(1,utf("r\ud834\udd1e"));unicodeFields.put(3,utf("e\ud83d\ude80"));
        valid("valid.unicode",unicode,envelope(unicodeFields,PEERS,3,2,0,false),true);
        for(State state:State.values()) {
            long revision=state==State.PREPARED?0:state==State.DISPATCHED||state==State.CANCELLED?1:2;
            valid("valid.state."+state,new Operation(id,state,revision),envelope(Collections.singletonMap(6,utf(state.name())),PEERS,3,2,revision,false),false);
        }
        valid("valid.maxIdentity",Operation.prepare(identity("\u0800".repeat(256),"\u0800".repeat(256),"\u0800".repeat(256),new HashSet<>(PEERS))),null,false);
        valid("valid.supplementary128",Operation.prepare(identity("\ud83d\ude00".repeat(128),"\ud83d\ude00".repeat(128),"\ud83d\ude00".repeat(128),new HashSet<>(PEERS))),null,false);
        for(int field:new int[]{0,1,3})for(String oversized:new String[]{"a".repeat(257),"\ud83d\ude00".repeat(129)})
            invalid("invalid.identityLength"+field+"."+oversized.length(),envelope(Collections.singletonMap(field,utf(oversized)),PEERS,3,2,0,false));
        Set<UUID> maximum=new HashSet<>();for(int n=0;n<4096;n++)maximum.add(new UUID(0,n));
        valid("valid.maxPeers",Operation.prepare(identity("p","r","e",maximum)),null,false);
        maximum.add(new UUID(0,4096));reject("invalid.encode4097",()->CodecAdapter.encode(Operation.prepare(identity("p","r","e",maximum))),IOException.class);
        for(Operation value:Arrays.asList(Operation.prepare(id),unicode)) {
            Identity ident=value.identity();String key=CodecAdapter.scope(ident),label=value==unicode?"scope.unicode":"scope.ascii";
            String preimage=ident.provider().id().length()+":"+ident.provider().id()+":"+ident.packageName();check(key.equals(sha(preimage.getBytes(StandardCharsets.UTF_8))));put(label,key);
            check(key.equals(CodecAdapter.scope(identity(ident.provider().id(),"different",ident.environment(),ident.affectedClones()))));
        }
        Set<String> uniqueKeys=new HashSet<>();
        String[] keyProviders={"a:b","1:p","\u00e9","e\u0301"};
        for(int n=0;n<keyProviders.length;n++) {
            String provider=keyProviders[n];Identity framedIdentity=identity(provider,"r","e",new HashSet<>(PEERS));
            String key=CodecAdapter.scope(framedIdentity);
            check(key.equals(sha((provider.length()+":"+provider+":a.b").getBytes(StandardCharsets.UTF_8))));
            check(uniqueKeys.add(key));put("scope.framing"+n,key);
        }
        for(int field=0;field<7;field++)for(int width:new int[]{2,3}) {
            byte[] original=utf(TEXT[field]);int first=original[0]&255;byte[] altered=new byte[original.length+width-1];
            if(width==2){altered[0]=(byte)(0xc0|first>>>6);altered[1]=(byte)(0x80|first&63);}
            else{altered[0]=(byte)0xe0;altered[1]=(byte)(0x80|first>>>6);altered[2]=(byte)(0x80|first&63);}
            System.arraycopy(original,1,altered,width,original.length-1);
            invalid("d4.overlong"+width+".field"+field,envelope(Collections.singletonMap(field,altered),PEERS,3,2,0,false));
        }
        List<UUID> reversed=new ArrayList<>(PEERS);Collections.reverse(reversed);invalid("d4.peerOrder",envelope(Collections.emptyMap(),reversed,3,2,0,false));
        List<byte[]> bad=Arrays.asList(new byte[]{0},new byte[]{(byte)0xc0,(byte)0x80},new byte[]{(byte)0xc2},new byte[]{(byte)0xc2,65},new byte[]{(byte)0x80},new byte[]{(byte)0xf0,(byte)0x9f,(byte)0x98,(byte)0x80},utf("p\ud800"),utf("p\udc00"),utf("\u00a0"),utf("a\u0001b"),utf("a "));
        for(int field:new int[]{0,1,3})for(int n=0;n<bad.size();n++)invalid("invalid.text"+field+"."+n,envelope(Collections.singletonMap(field,bad.get(n)),PEERS,3,2,0,false));
        for(int field:new int[]{2,4,5,6})invalid("invalid.field"+field,envelope(Collections.singletonMap(field,utf("INVALID VALUE")),PEERS,3,2,0,false));
        for(int version:new int[]{1,3})invalid("invalid.version"+version,envelope(Collections.emptyMap(),PEERS,3,version,0,false));
        for(int count:new int[]{-1,0,4097,Integer.MAX_VALUE})invalid("invalid.count"+count,envelope(Collections.emptyMap(),PEERS,count,2,0,false));
        invalid("invalid.feasibleCountExhausted",envelope(Collections.emptyMap(),PEERS,4096,2,0,false));
        invalid("invalid.duplicate",envelope(Collections.emptyMap(),Arrays.asList(A,A),2,2,0,false));
        invalid("invalid.missingClone",envelope(Collections.emptyMap(),Collections.singletonList(PEERS.get(0)),1,2,0,false));
        invalid("invalid.revision",envelope(Collections.emptyMap(),PEERS,3,2,1,false));invalid("invalid.trailing",envelope(Collections.emptyMap(),PEERS,3,2,0,true));
        byte[] magic=Arrays.copyOf(ascii,ascii.length-32);magic[0]^=1;invalid("invalid.magic",seal(magic));
        byte[] overflow=Arrays.copyOf(ascii,ascii.length-32);overflow[24]=(byte)255;overflow[25]=(byte)255;invalid("invalid.utfLength",seal(overflow));
        for(int length=0;length<ascii.length;length++)invalid("truncate."+String.format(Locale.ROOT,"%04d",length),Arrays.copyOf(ascii,length));
        for(int length:new int[]{35,36,262144,262145})invalid("bounds.sealed"+length,seal(Arrays.copyOf(ascii,length-32)));
        reject("invalid.uuid",()->CodecAdapter.decode(ascii,new UUID(0,99)),IOException.class);
        reject("null.input",()->CodecAdapter.decode(null,OP),NullPointerException.class);
        reject("null.expected",()->CodecAdapter.decode(ascii,null),NullPointerException.class);
        reject("null.encode",()->CodecAdapter.encode(null),NullPointerException.class);
        reject("null.scope",()->CodecAdapter.scope(null),NullPointerException.class);
        // Independently freeze representative subtype obligations, not only host/port agreement.
        for(String key:Arrays.asList("invalid.text0.2","invalid.text0.3","invalid.text0.4","invalid.text0.5"))
            check(OUTPUT.get(key).startsWith("UTFDataFormatException:"));
        for(String key:Arrays.asList("invalid.utfLength","bounds.sealed36","invalid.feasibleCountExhausted"))
            check(OUTPUT.get(key).startsWith("EOFException:"));
        for(int field=0;field<7;field++)for(int width:new int[]{2,3})check(OUTPUT.get("d4.overlong"+width+".field"+field).startsWith("IOException:"));
        for(String key:Arrays.asList("d4.peerOrder","invalid.version1","invalid.version3"))check(OUTPUT.get(key).startsWith("IOException:"));
        check(OUTPUT.get("invalid.uuid").equals("IOException"));
        StringBuilder output=new StringBuilder();OUTPUT.forEach((key,value)->output.append(key).append('=').append(value).append('\n'));check(output.length()<=32768);
        if(args.length==1&&args[0].equals("--emit-observations")){System.out.print(output);return;}
        check(args.length==0);System.out.println("PASS "+checks+" independent codec checks / "+OUTPUT.size()+" desired vectors");
    }
}
