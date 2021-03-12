package biblemulticonverter.format.paratext.utilities;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import com.sun.xml.txw2.output.XMLWriter;

/**
 * Simple XMLWriter that skips most formatting and indention to make sure no significant whitespace is
 * accidentally added to the resulting Bible. But still allows for easy human reading due to the formatting that is
 * still there, eventhough it is not perfect
 */
public class UnifiedScriptureXMLWriter extends XMLWriter {

	/**
	 * List of elements that only allow other elements as content (non-mixed-content).
	 */
	private final Set<String> noMixedContentElements = new HashSet<>();
	private final Stack<String> openTags = new Stack<>();
	private final Writer writer;

	public UnifiedScriptureXMLWriter(Writer writer, String encoding) {
		super(writer, encoding);
		this.noMixedContentElements.add("usx");
		this.writer = writer;
	}

	@Override
	public void startElement(String s, String s1, String qName, Attributes attributes) throws SAXException {
		super.startElement(s, s1, qName, attributes);
		openTags.push(qName);
	}

	@Override
	public void endElement(String s, String s1, String qName) throws SAXException {
		super.endElement(s, s1, qName);
		if (openTags.isEmpty() || !openTags.pop().equals(qName)) {
			throw new SAXException("End element '" + qName + "' found, while no matching start element was given.");
		}
		final String parent;
		if (!openTags.isEmpty()) {
			parent = openTags.peek();
		} else {
			parent = null;
		}

		if (noMixedContentElements.contains(parent)) {
			addNewLine();
			addIndention(openTags.size());
		}
	}

	private void addNewLine() throws SAXException {
		try {
			writer.write("\n");
		} catch (IOException e) {
			throw new SAXException(e);
		}
	}

	private void addIndention(int depth) throws SAXException {
		try {
			final char[] buffer = new char[depth * 2];
			Arrays.fill(buffer, ' ');
			writer.write(buffer);
		} catch (IOException e) {
			throw new SAXException(e);
		}
	}
}