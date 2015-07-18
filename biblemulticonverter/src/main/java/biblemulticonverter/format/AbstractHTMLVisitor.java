package biblemulticonverter.format;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.FormattedText.Visitor;

/**
 * Helper class for converting {@link FormattedText} to HTML. Inline elements
 * are implemented, block level elements (like headlines, footnotes or line
 * breaks) have to be implemented by subclasses.
 */
public abstract class AbstractHTMLVisitor implements Visitor<IOException> {

	protected final Writer writer;
	protected final List<String> suffixStack = new ArrayList<String>();

	protected AbstractHTMLVisitor(Writer writer, String suffix) {
		this.writer = writer;
		pushSuffix(suffix);
	}

	protected void pushSuffix(String suffix) {
		suffixStack.add(suffix);
	}

	protected String getNextFootnoteTarget() {
		return null;
	}

	@Override
	public void visitVerseSeparator() throws IOException {
		writer.write("<font color=\"#808080\">/</font>");
	}

	@Override
	public int visitElementTypes(String elementTypes) throws IOException {
		return 0;
	}

	@Override
	public void visitStart() throws IOException {
	}

	@Override
	public void visitText(String text) throws IOException {
		writer.write(text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"));
	}

	@Override
	public FormattedText.Visitor<IOException> visitFormattingInstruction(FormattingInstructionKind kind)
			throws IOException {
		String startTag, endTag;
		if (kind.getHtmlTag() != null) {
			startTag = "<" + kind.getHtmlTag() + ">";
			endTag = "</" + kind.getHtmlTag() + ">";
		} else {
			startTag = createFormattingInstructionStartTag(kind);
			endTag = "</span>";
		}
		if (kind == FormattingInstructionKind.FOOTNOTE_LINK) {
			String target = getNextFootnoteTarget();
			if (target != null) {
				startTag = "<a class=\"footnote-link\" href=\"" + target + "\">";
				endTag = "</a>";
			}
		}
		writer.write(startTag);
		pushSuffix(endTag);
		return this;
	}

	protected String createFormattingInstructionStartTag(FormattingInstructionKind kind) {
		return "<span style=\"" + kind.getCss() + "\">";
	}

	@Override
	public Visitor<IOException> visitCSSFormatting(String css) throws IOException {
		writer.write("<span class=\"css\" style=\"" + css + "\">");
		pushSuffix("</span>");
		return this;
	}

	@Override
	public void visitRawHTML(RawHTMLMode mode, String raw) throws IOException {
		if (!mode.equals(Boolean.getBoolean("rawhtml.online") ? RawHTMLMode.OFFLINE : RawHTMLMode.ONLINE)) {
			writer.write(raw);
		}
	}

	@Override
	public Visitor<IOException> visitVariationText(String[] variations) throws IOException {
		throw new UnsupportedOperationException("Variation text not supported");
	}

	@Override
	public Visitor<IOException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws IOException {
		Visitor<IOException> next = prio.handleVisitor(category, this);
		if (next != null)
			pushSuffix("");
		return next;
	}

	@Override
	public boolean visitEnd() throws IOException {
		writer.write(suffixStack.remove(suffixStack.size() - 1));
		return false;
	}
}
