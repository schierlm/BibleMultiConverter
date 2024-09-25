package biblemulticonverter.format;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.ExtendedLineBreakKind;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.HyperlinkType;
import biblemulticonverter.data.FormattedText.LineBreakKind;
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
	public Visitor<IOException> visitHyperlink(HyperlinkType type, String target) throws IOException {
		if (type == HyperlinkType.ANCHOR) {
			writer.write("<a name=\"" + target + "\">");
		} else {
			writer.write("<a href=\"" + target + "\">");
		}
		pushSuffix("</a>");
		return this;
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

	/**
	 * Parse some HTML tags and append to given visitor. Try to avoid appending
	 * Raw HTML if possible. In case the HTML contains unbalanced known tags,
	 * may use and parse parts of the suffix text (if not empty).
	 *
	 * @param vv
	 *            Visitor to append
	 * @param hp
	 *            Hyperlink parser
	 * @param html
	 *            HTML text to parse
	 * @param suffix
	 *            Suffix text after the HTML text to parse
	 * @return Unused part of the suffix text
	 */
	public static <T extends Throwable> String parseHTML(Visitor<T> vv, HyperlinkParser<T> hp, String html, String suffix) throws T {
		int pos = html.indexOf('<');
		while (pos != -1) {
			parseHTMLEntities(vv, html.substring(0, pos));
			html = html.substring(pos);
			pos = html.indexOf('>');
			if (html.startsWith("<!--") && html.contains("-->"))
				pos = html.indexOf("-->") + 2;
			if (pos == -1)
				throw new RuntimeException(html);
			String tag = html.substring(0, pos + 1);
			html = html.substring(pos + 1);
			String cleanedTag = tag.replaceAll(" +class=('[^']*'|\"[^\"]*\")", "");
			if (cleanedTag.endsWith("/>") || cleanedTag.endsWith(" >"))
				cleanedTag = cleanedTag.substring(0, cleanedTag.length() - 2).trim() + ">";
			if ((cleanedTag.toLowerCase().startsWith("<a href=\"") && cleanedTag.endsWith("\">"))
					|| (cleanedTag.toLowerCase().startsWith("<a href='") && cleanedTag.endsWith("'>"))) {
				int endPos = html.indexOf("</" + cleanedTag.substring(1, 2) + ">");
				int copyFromSuffix = 0;
				if (endPos == -1) {
					int suffixPos = suffix.indexOf("</" + cleanedTag.substring(1, 2) + ">");
					if (suffixPos != -1) {
						endPos = suffixPos + html.length();
						copyFromSuffix = suffixPos + 4;
					}
				}
				if (endPos != -1) {
					Visitor<T> nextVisitor = hp == null ? null : hp.parseHyperlink(vv, cleanedTag.substring(9, cleanedTag.length() - 2));
					if (nextVisitor == null) {
						nextVisitor = vv.visitHyperlink(HyperlinkType.EXTERNAL_LINK	, cleanedTag.substring(9, cleanedTag.length() - 2));
					}
					if (nextVisitor != null) {
						if (copyFromSuffix > 0) {
							html = html + suffix.substring(0, copyFromSuffix);
							suffix = suffix.substring(copyFromSuffix);
						}
						parseHTML(nextVisitor, hp, html.substring(0, endPos), "");
						html = html.substring(endPos + 4);
					}
				} else {
					cleanedTag = null;
				}
			} else if (cleanedTag.toLowerCase().equals("<p>")) {
				vv.visitLineBreak(ExtendedLineBreakKind.PARAGRAPH, 0);
			} else if (cleanedTag.toLowerCase().equals("</p>")) {
				// ignore
			} else if (cleanedTag.toLowerCase().equals("<br>")) {
				vv.visitLineBreak(ExtendedLineBreakKind.NEWLINE, 0);
			} else if (cleanedTag.toLowerCase().matches("<(b|i|u|strong|em|sub|sup|h[1-6]|font +color=['\"]?red['\"]?)>")) {
				String endTag = "</" + cleanedTag.substring(1).replaceAll(" .*>", ">");
				int endPos = html.indexOf(endTag);
				if (endPos == -1) {
					int suffixPos = suffix.indexOf(endTag);
					if (suffixPos != -1) {
						endPos = suffixPos + html.length();
						html = html + suffix.substring(0, suffixPos + endTag.length());
						suffix = suffix.substring(suffixPos + endTag.length());
					}
				}
				if (endPos != -1) {
					Visitor<T> nextVisitor;
					if (cleanedTag.toLowerCase().startsWith("<h")) {
						nextVisitor = vv.visitHeadline(cleanedTag.charAt(2) - '0');
					} else if (cleanedTag.toLowerCase().startsWith("<font")) {
						nextVisitor = vv.visitFormattingInstruction(FormattingInstructionKind.WORDS_OF_JESUS);
					} else if (cleanedTag.equalsIgnoreCase("<b>") || cleanedTag.equalsIgnoreCase("<strong>")) {
						nextVisitor = vv.visitFormattingInstruction(FormattingInstructionKind.BOLD);
					} else if (cleanedTag.equalsIgnoreCase("<i>") || cleanedTag.equalsIgnoreCase("<em>")) {
						nextVisitor = vv.visitFormattingInstruction(FormattingInstructionKind.ITALIC);
					} else if (cleanedTag.equalsIgnoreCase("<u>")) {
						nextVisitor = vv.visitFormattingInstruction(FormattingInstructionKind.UNDERLINE);
					} else if (cleanedTag.equalsIgnoreCase("<sub>")) {
						nextVisitor = vv.visitFormattingInstruction(FormattingInstructionKind.SUBSCRIPT);
					} else if (cleanedTag.equalsIgnoreCase("<sup>")) {
						nextVisitor = vv.visitFormattingInstruction(FormattingInstructionKind.SUPERSCRIPT);
					} else {
						throw new IllegalStateException("Unsupported formatting tag: " + cleanedTag);
					}
					parseHTML(nextVisitor, hp, html.substring(0, endPos), "");
					html = html.substring(endPos + endTag.length());
				} else {
					cleanedTag = null;
				}
			} else {
				cleanedTag = null;
			}
			if (cleanedTag == null) {
				vv.visitRawHTML(RawHTMLMode.BOTH, tag.replaceAll("  +", " "));
			}
			pos = html.indexOf('<');
		}
		parseHTMLEntities(vv, html);
		return suffix;
	}

	private static <T extends Throwable> void parseHTMLEntities(Visitor<T> vv, String text) throws T {
		if (text.contains("\1") || text.contains("\2") || text.contains("<") || text.contains(">"))
			throw new RuntimeException(text);
		text = text.replace("& ", "&amp; ").replace("&amp;", "\1").replace("&lt;", "<").replace("&gt;", ">");
		text = text.replace("&quot;", "\"").replace("&apos;", "'").replace("&#146;", "’").replace("&#147;", "“");
		text = text.replace("&#148;", "”").replace("&nbsp;", "\u00A0").replace("&copy;", "©");
		text = text.replace("&", "\2").replace("\1", "&").replace('\t', ' ').replaceAll("  +", " ");
		while (text.contains("\2")) {
			int pos = text.indexOf('\2');
			if (pos != -1) {
				int posEnd = text.indexOf(";", pos);
				if (posEnd == -1) {
					System.out.println("WARNING: Unclosed HTML entity in " + text.substring(pos).replace("\2", "&"));
					posEnd = pos;
				}
				vv.visitText(text.substring(0, pos).trim());
				String entity = text.substring(pos, posEnd + 1).replace('\2', '&');
				if (entity.matches("&#[0-9]+;")) {
					vv.visitText("" + (char) Integer.parseInt(entity.substring(2, entity.length() - 1)));
				} else if (entity.matches("&#x[0-9a-fA-F]+;")) {
					vv.visitText("" + (char) Integer.parseInt(entity.substring(3, entity.length() - 1), 16));
				} else {
					// TODO named entities?
					vv.visitRawHTML(RawHTMLMode.BOTH, entity);
				}
				text = text.substring(posEnd + 1).trim();
			}
		}
		vv.visitText(text);
	}

	public static interface HyperlinkParser<T extends Throwable> {
		public Visitor<T> parseHyperlink(Visitor<T> base, String link);
	}
}
