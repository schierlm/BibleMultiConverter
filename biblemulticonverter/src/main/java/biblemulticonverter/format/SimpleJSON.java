package biblemulticonverter.format;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText.ExtendedLineBreakKind;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.HyperlinkType;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.RawHTMLMode;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.MetadataBook;
import biblemulticonverter.data.MetadataBook.MetadataBookKey;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.Versification;
import biblemulticonverter.data.VirtualVerse;

public class SimpleJSON implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Very simple JSON bible export (verse text only).",
			"",
			"Usage: SimpleJSON <OutputFile>"
	};

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(exportArgs[0]), StandardCharsets.UTF_8))) {
			SimpleJSONVisitor sjv = new SimpleJSONVisitor(bw);
			bw.write("\t{\"osis\": [{\"osisText\": [{\"osisRefWork\": {\"_value\": \"" + json(bible.getName()) + "\"}," +
					"\t\t\"header\": [{\"work\": [{");
			MetadataBook mdbk = bible.getMetadataBook();
			for (MetadataBookKey key : Arrays.asList(MetadataBookKey.title, MetadataBookKey.creator,
					MetadataBookKey.contributors, MetadataBookKey.identifier, MetadataBookKey.subject,
					MetadataBookKey.date, MetadataBookKey.description, MetadataBookKey.publisher, MetadataBookKey.type,
					MetadataBookKey.source, MetadataBookKey.language, MetadataBookKey.coverage, MetadataBookKey.rights)) {
				if (key != MetadataBookKey.title)
					bw.write(',');
				String value = mdbk == null ? null : mdbk.getValue(key);
				bw.write("\"" + key.toString() + "\": [{\"_text\": \"" + json(value == null ? "" : value) + "\"}]");
			}
			bw.write("}]}],\t\"div\": [");
			boolean first = true;
			for (Book bk : bible.getBooks()) {
				if (bk.getChapters().isEmpty() || bk.getChapters().get(0).getVerses().isEmpty()) {
					System.out.println("WARNING: Skipping empty book " + bk.getAbbr());
					continue;
				}
				if (!first)
					bw.write(',');
				bw.write("\t{\"name\": {\"_value\": \"" + json(bk.getLongName()) + "\"},\"osisID\": {\"_value\": \"" + json(bk.getId().getOsisID()) + "\"},\"chapter\": [");
				first = true;
				for (int ch = 1; ch <= bk.getChapters().size(); ch++) {
					if (!first)
						bw.write(',');
					Chapter cc = bk.getChapters().get(ch - 1);
					bw.write("\t{\"cnumber\":" + ch + ",\n\"verse\": [");
					first = true;
					for (VirtualVerse vv : cc.createVirtualVerses()) {
						if (!first)
							bw.write(',');
						bw.write("\t{\"osisID\": {\"_value\": \"" +
								json(bk.getId().getOsisID() + "." + ch + "." + vv.getNumber()) + "\"},\"vnumber\":" +
								vv.getNumber() + ",\"_text\": \"");
						boolean firstVerse = true;
						for (Verse v : vv.getVerses()) {
							if (!firstVerse || !v.getNumber().equals("" + vv.getNumber())) {
								bw.write(" (" + v.getNumber() + ") ");
							}
							v.accept(sjv);
							firstVerse = false;
						}
						bw.write("\"}");
						first = false;
					}
					bw.write("]}");
					first = false;
				}
				bw.write("]}");
				first = false;
			}
			bw.write("]}]}]}\n");
		}
	}

	/** Escape JSON */
	private static String json(String raw) {
		return raw.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static class SimpleJSONVisitor implements Visitor<IOException> {

		private Writer writer;

		protected SimpleJSONVisitor(Writer writer) {
			this.writer = writer;
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
			writer.write(json(text));
		}

		@Override
		public Visitor<IOException> visitHeadline(int depth) throws IOException {
			throw new RuntimeException("Headlines not supported");
		}

		@Override
		public Visitor<IOException> visitFootnote(boolean ofCrossReferences) throws IOException {
			return null;
		}

		@Override
		public Visitor<IOException> visitCrossReference(String firstBookAbbr, BookID firstBook, int firstChapter, String firstVerse, String lastBookAbbr, BookID lastBook, int lastChapter, String lastVerse) throws IOException {
			return this;
		}

		@Override
		public void visitLineBreak(ExtendedLineBreakKind kind, int indent) throws IOException {
			visitText(" ");
		}

		@Override
		public Visitor<IOException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, Versification.Reference[] sourceVerses, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) throws IOException {
			return this;
		}

		@Override
		public Visitor<IOException> visitDictionaryEntry(String dictionary, String entry) throws IOException {
			return this;
		}

		@Override
		public Visitor<IOException> visitFormattingInstruction(FormattingInstructionKind kind) throws IOException {
			return this;
		}

		@Override
		public Visitor<IOException> visitCSSFormatting(String css) throws IOException {
			return this;
		}

		@Override
		public void visitVerseSeparator() throws IOException {
			visitText("/");
		}

		@Override
		public void visitRawHTML(RawHTMLMode mode, String raw) throws IOException {
		}

		@Override
		public Visitor<IOException> visitVariationText(String[] variations) throws IOException {
			throw new RuntimeException("Variations not supported");
		}

		@Override
		public Visitor<IOException> visitSpeaker(String labelOrStrongs) throws IOException {
			return this;
		}

		@Override
		public Visitor<IOException> visitHyperlink(HyperlinkType type, String target) throws IOException {
			return this;
		}

		@Override
		public Visitor<IOException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws IOException {
			return prio.handleVisitor(category, this);
		}

		@Override
		public boolean visitEnd() throws IOException {
			return false;
		}
	}
}
