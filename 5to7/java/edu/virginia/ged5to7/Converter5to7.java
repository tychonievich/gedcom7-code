package edu.virginia.ged5to7;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.Stack;
import java.util.LinkedList;
import java.util.List;
import java.util.Collection;
import java.util.TreeMap;
import java.util.Map;

import edu.virginia.ged5to7.pipeline.*;

public class Converter5to7 {
    private int lastID;
    private final int ID_BASE;
    private final int ID_TO_SKIP;
    private Collection<GedStruct> records;
    private List<String> log;
    private static String TSV_DIR = "../../GEDCOM/extracted-files";
    
    
    final static java.nio.charset.Charset UTF8 = java.nio.charset.Charset.forName("UTF-8");
    
    
    static TwoKeyMap<String> substructures; // .get(superstructure URI, tag) -> URI
    static Map<String, String> payloads;    // .get(URI) -> payload type
    static TwoKeyMap<String> enumerations;  // .get(structure URI, payload) -> URI
    static TwoKeyMap<String> uri2tag;       // .get(container URI, URI) -> tag/payload
    
    private static boolean setExtractedFilesDirectory(String path) {
        if (!Files.isDirectory(Paths.get(path))) return false;
        boolean something = false;
        if (Files.exists(Paths.get(path, "substructures.tsv"))) {
            if (substructures == null) substructures = new TwoKeyMap<String>();
            if (uri2tag == null) uri2tag = new TwoKeyMap<String>();
            try {
                Files.lines(Paths.get(path, "substructures.tsv")).forEach(line -> {
                    String[] bits = line.split("\t");
                    if (bits.length == 3) {
                        substructures.put(bits[0], bits[1], bits[2]);
                        uri2tag.put(bits[0], bits[2], bits[1]);
                    }
                });
                something = true;
            } catch (IOException ex) { System.err.println(ex.toString()); }
        }
        if (Files.exists(Paths.get(path, "enumerations.tsv"))) {
            if (enumerations == null) enumerations = new TwoKeyMap<String>();
            if (uri2tag == null) uri2tag = new TwoKeyMap<String>();
            try {
                Files.lines(Paths.get(path, "enumerations.tsv")).forEach(line -> {
                    String[] bits = line.split("\t");
                    if (bits.length == 3) {
                        enumerations.put(bits[0], bits[1], bits[2]);
                        uri2tag.put(bits[0], bits[2], bits[1]);
                    }
                });
                something = true;
            } catch (IOException ex) { System.err.println(ex.toString()); }
        }
        if (Files.exists(Paths.get(path, "payloads.tsv"))) {
            if (payloads == null) payloads = new TreeMap<String,String>();
            try {
                Files.lines(Paths.get(path, "payloads.tsv")).forEach(line -> {
                    String[] bits = line.split("\t");
                    if (bits.length == 2) {
                        payloads.put(bits[0], bits[1]);
                    }
                });
                something = true;
            } catch (IOException ex) { System.err.println(ex.toString()); }
        }
        return something;
    }
    
    
    /**
     * Parses file using error-tolerant algorithm and performs full 5to7 conversion.
     */
    public Converter5to7(String filename) {
        this(filename, 10);
    }

    /**
     * Parses file using error-tolerant algorithm and performs full 5to7 conversion.
     * Record IDs are assigned as sequential base-<code>id_base</code> integers.
     */
    public Converter5to7(String filename, int id_base) {
        if (id_base < 2 || id_base > 36) throw new IllegalArgumentException("id_base must be between 2 and 36");
        ID_BASE = id_base;
        if (ID_BASE > 'V'-'A'+10) ID_TO_SKIP = Integer.parseInt("VOID", ID_BASE);
        else ID_TO_SKIP = -1;
        
        records = new LinkedList<GedStruct>();
        log = new LinkedList<String>();
        lastID = -1;
        
        fuzzyParse(filename);
        for(GedStruct s : records) s.tag2uri();
        
        Filter[] filters = {
            new AgeDateFilter(),
            new VersionFilter(),
            new SourceFilter(),
        };
        for(Filter f : filters) {
            java.util.LinkedList<GedStruct> created = new java.util.LinkedList<GedStruct>();
            for(GedStruct s : records) {
                java.util.Collection<GedStruct> r = f.update(s);
                if (r != null) created.addAll(r);
            }
            records.addAll(created);
        }

        for(GedStruct s : records) s.uri2tag();
        
        reID();
    }
    
    /**
     * Parses a file, logging but permitting errors, and converts cross-references to pointers.
     */
    private void fuzzyParse(String filename) {
        Stack<GedStruct> stack = new Stack<GedStruct>();
        Map<String,GedStruct> xref = new TreeMap<String,GedStruct>();
        try {
            Files.lines(Paths.get(filename), CharsetDetector.detect(filename)).forEach(line -> {
                try {
                    GedStruct got = new GedStruct(line);
                    while(got.level < stack.size()) stack.pop();
                    if (stack.empty()) records.add(got);
                    else stack.peek().addSubstructure(got);
                    stack.push(got);
                    if (got.id != null) xref.put(got.id, got);
                } catch (IllegalArgumentException ex) {
                    log.add(ex.toString());
                }
            });
            stack.clear();
            
            for(GedStruct record: records) record.convertPointers(xref, true, LinkedList.class);

        } catch(Exception ex) {
            log.add(ex.toString());
        }
    }
    
    /**
     * Outputs a parsed dataset as a GEDCOM file.
     */
    public void dumpTo(java.io.OutputStream out) throws IOException {
        out.write("\uFEFF".getBytes(UTF8));
        for(GedStruct rec : records) out.write(rec.toString().getBytes(UTF8));
    }
    
    /**
     * Allocates and returns the next available record ID.
     */
    private String nextID() {
        if (lastID == ID_TO_SKIP) lastID += 1;
        return "@" + Integer.toString(lastID++, ID_BASE).toUpperCase() + "@";
    }
    
    /**
     * Finds which anchors are actually used, renames those, and scraps unused anchors.
     * Unnecessary by itself, but useful before NOTE/SNOTE heuristic and after adding new records.
     */
    private void reID() {
        for(GedStruct record: records)
            if (record.incoming != null && !record.incoming.isEmpty()) {
                record.id = nextID();
            } else {
                record.id = null;
            }
    }
    
    public static void main(String[] args) {
        System.err.println();
        for(String path : args) {
            System.err.println("path "+path);
            if (Files.isDirectory(Paths.get(path))) {
                if (!setExtractedFilesDirectory(path)) {
                    System.err.println(path+" is a directory");
                }
                System.err.println("Parsed " + substructures.size() + " substructure rules");
                System.err.println("Parsed " + enumerations.size() + " enumeration rules");
                System.err.println("Parsed " + payloads.size() + " payload type rules");
                continue;
            }
            System.err.println("\nProcessing "+path+" ...");
            Converter5to7 conv = new Converter5to7(path);
            try { conv.dumpTo(System.out); } catch (IOException ex) { ex.printStackTrace(); }
            for(String err : conv.log) System.err.println("** "+err);
            System.err.println("    ... done with " + path+"\n");
        }
    }
}
