package edu.virginia.ged5to7;

import java.util.LinkedList;
import java.util.Map;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class GedStruct {
    public GedStruct sup;
    public LinkedList<GedStruct> sub;
    int level;
    public String tag, id, payload;
    public Collection<GedStruct> incoming;
    public GedStruct pointsTo;
    final static Pattern GED_LINE = Pattern.compile("﻿?\\s*([0-9]+)\\s+(?:(@[^@]+@)\\s+)?([A-Za-z0-9_]+)(?:\\s([^\\n\\r]*))?[\\n\\r]*");
    final static Pattern FUZZY_XREF = Pattern.compile("\\s*@[^#@][^@]*@\\s*");
    final static Pattern AT_DETECTOR = Pattern.compile(
        "^\\s*(@[^@#][^@]*@)\\s*$|" +
        "(@@)|" +
        "@#D([^@]*)@ ?|" +
        "@#([^D@][^@]*)@ ?"
        );
    
    /**
     * Parse a line from a GEDCOM file into a basic GedStruct
     * 
     * @throws@ IllegalArgumentException if the line cannot be parsed
     */
    public GedStruct(String line) {
        Matcher m = GED_LINE.matcher(line);
        if (!m.matches()) throw new IllegalArgumentException("No GEDCOM found in:\n"+line);

        sub = new LinkedList<GedStruct>();
        level = Integer.parseInt(m.group(1));
        if (m.group(2) != null) {
            id = m.group(2).toUpperCase();
            if (id == "@VOID@") throw new IllegalArgumentException("@VOID@ must not be used as a record identifier in:\n"+line);
        }
        tag = m.group(3).toUpperCase();
        payload = fixAtSign(m.group(4));
    }
    
    public GedStruct(GedStruct sup, String tag, String payload) {
        this.sub = new LinkedList<GedStruct>();
        this.tag = tag;
        this.payload = payload;
        if (sup != null) { sup.addSubstructure(this); this.level = sup.level+1; }
        else { this.sup = null; this.level = 0; }
    }
    public GedStruct(GedStruct sup, String tag, GedStruct payload) {
        this.sub = new LinkedList<GedStruct>();
        this.tag = tag;
        this.pointsTo = payload;
        if (payload == null) this.payload = "@VOID@";
        else if (payload.incoming != null) payload.incoming.add(this);
        else {
            payload.incoming = new LinkedList<GedStruct>();
            payload.incoming.add(this);
        }
        if (sup != null) { sup.addSubstructure(this); this.level = sup.level+1; }
        else { this.sup = null; this.level = 0; }
    }
    
    static private String fixAtSign(String payload) {
        if (payload == null || payload.length() == 0) return null;
        Matcher m = AT_DETECTOR.matcher(payload);
        String ans = "";
        int lastidx = 0;
        while(m.find()) {
            if (m.group(1) != null) return m.group(1); // pointer
            if (m.group(2) != null) { // @@
                ans += payload.substring(lastidx, m.start()+1);
            } else if (m.group(3) != null) { // date
                ans += payload.substring(lastidx, m.start());
                ans += m.group(3).replaceAll("\\s","_");
                ans += ' ';
            } else if (m.group(4) != null) {
                ans += payload.substring(lastidx, m.start());
                // skip non-date escape, which have been invalid since 5.3
            } else {
                throw new UnsupportedOperationException("Impossible: \""+m.group(0)+"\" matched the AT_DETECTOR");
            }
            lastidx = m.end();
        }
        ans += payload.substring(lastidx);
        return ans;
    }
    
    /**
     * Adds a substructure to this structure. If the substructure is
     * a CONT or CONC, this operation modifies the payload; otherwise
     * it adds bidirectional linking between the structures. Because
     * substructure order is significant, this method must be called
     * in the order substructures appear in the file.
     */
    public boolean addSubstructure(GedStruct substructure) {
        if (substructure.tag.equals("CONT")) {
            payload += "\n"+substructure.payload;
            return false;
        } else if (substructure.tag.equals("CONC")) {
            payload += substructure.payload;
            return false;
        } else {
            sub.add(substructure);
            substructure.sup = this;
            return true;
        }
    }
    
    /**
     * Given a map from xref_id strings to their parsed structures,
     * populates the <code>pointsTo</code> fields of this struct
     * and its substructures. If <code>cleanup</code> is 
     * <code>true</code>, also clears old xref_id payloads and
     * replaces dead pointers with <code"@VOID"</code>.
     */
    @SuppressWarnings({"unchecked"})
    public void convertPointers(Map<String,GedStruct> xref, boolean cleanup, Class<?> cachetype) {
        if (payload != null) {
            GedStruct to = xref.get(this.payload);
            if (to != null) {
                if (cachetype != null) {
                    try {
                        if (to.incoming == null) to.incoming = (Collection<GedStruct>)cachetype.getConstructor().newInstance();
                        to.incoming.add(this);
                    } catch (NoSuchMethodException ex) {}
                    catch (InstantiationException ex) {}
                    catch (IllegalAccessException ex) {}
                    catch (java.lang.reflect.InvocationTargetException ex) {}
                }
                pointsTo = to;
                if (cleanup) payload = null;
            } else if (cleanup && FUZZY_XREF.matcher(payload).matches()) {
                // TO DO: log this workaround
                payload = "@VOID@";
            }
        }
        for(GedStruct s : sub) s.convertPointers(xref, cleanup, cachetype);
    }
    
    // accumulate pointed-to-by
    // convert tag to URI
    // validate payload datatypes and pointed-to types
    
    /**
     * Helper method for toString
     */
    void serialize(StringBuilder sb) {
        if (sup != null) level = sup.level + 1;
        sb.append(level); sb.append(' ');
        // to do: add ID addition if no ID but pointed to
        if (level == 0 && id != null) {
            sb.append(id);
            sb.append(' ');
        }
        // to do: add URI-to-tag processing
        sb.append(tag);
        if (pointsTo != null) {
            // to do: add ID addition if point to non-ID struct
            sb.append(' ');
            sb.append(pointsTo.id);
        }
        else if (payload != null) {
            sb.append(' ');
            sb.append(payload.replaceAll("^@|\\n@|\\r@","$0@").replaceAll("\r\n?|\n", "\n"+(level+1)+" CONT "));
        }
        sb.append("\n");
        for(GedStruct s : sub) s.serialize(sb);
    }
    
    /**
     * Serializes as GEDCOM. Advanced data overrides primitive data:
     * for example, superstructure supersedes level, pointers
     * supersede payloads, etc.
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        serialize(sb);
        return sb.toString();
    }
}
