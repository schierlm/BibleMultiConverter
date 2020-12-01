package biblemulticonverter.format.paratext.utilities;

import java.util.regex.Pattern;

public class UsxTextUtilities {

    private static final Pattern UNINTENDED_XML_TEXT_MATCHER = Pattern.compile("[\\n\\r]+\\s*$");

    /**
     * When reading from an USX XML file a new line followed by indention whitespace (tabs and spaces) within an element
     * is sometimes considered as actual "text", but it was very likely never indented as actual text, since new lines
     * are ignored by USFM/XML. So where does this whitespace come from? This is probably caused by malformed USX XML.
     * <p>
     * Take for example this piece of XML where the {@code </char>} is not closed on the same indention level as it was
     * opened making the XML reader correctly read the following new-line and tab as "text", even though this is
     * obviously not intended to be part of the actual scripture.
     * <pre>
     * {@code
     *   <para style="p">
     *     <verse number="1" style="v" sid="GEN 1:1" />
     *     <note caller="-" style="x">
     *       <char style="xo" closed="false">1:1 </char>
     *       <char style="xt" closed="false">
     *         <ref loc="JOB 38:4">Job 38:4</ref></char>
     *     </note>In het begin schiep God de hemel en de aarde.<verse eid="GEN 1:1" /></para>
     * }
     * </pre>
     * <p>
     * Without any normalization or filtering new exports to other formats will have these "bugs" as well.
     * <p>
     * <pre>
     * {@code
     * \p
     * \v 1 \x - \xo 1:1 \xo*\xt Job 38:4\xt*
     *     \x*In het begin schiep God de hemel en de aarde.
     * }
     * </pre>
     * <p>
     * This method tries to detect these "unintended" new lines and whitespace at the end of pieces of text and removes
     * them if necessary.
     */
    public static String normalizeText(String text) {
        return UNINTENDED_XML_TEXT_MATCHER.matcher(text).replaceAll("");
    }
}
