package biblemulticonverter.format;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.ExtendedLineBreakKind;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.FormattedText.VisitorAdapter;
import biblemulticonverter.data.Verse;

public class StrongConcordance implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Add concordance information to a Strong dictionary",
			"",
			"Usage: StrongConcordance <oldDictionaryFile> <newDictionaryFile>",
			"",
			"Parse a bible (the input file) and add the resulting concordance information to a Strong dictionary.",
			"The strong dictionary has to be stored in Diffable format; the new Strong dictionary will be stored",
			"in Diffable format again."
	};

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		if (exportArgs.length != 2)
			throw new IOException("Two parameters needed!");
		Map<String, List<OccurrenceInfo>> occurrences = new HashMap<String, List<OccurrenceInfo>>();
		int bookIndex = 0;
		for (Book bk : bible.getBooks()) {
			int cnumber = 0;
			for (Chapter ch : bk.getChapters()) {
				cnumber++;
				for (Verse v : ch.getVerses()) {
					Map<String, List<StringBuilder>> strongInfo = new HashMap<>();
					v.accept(new StrongInfoVisitor(strongInfo, bk.getId().isNT() ? 'G' : 'H'));
					for (Map.Entry<String, List<StringBuilder>> e : strongInfo.entrySet()) {
						String[] strongs = e.getKey().split("\\+");
						String suffix = "";
						if (strongs.length > 1)
							suffix = " [" + e.getKey() + "]";
						for (StringBuilder val : e.getValue()) {
							OccurrenceInfo info = new OccurrenceInfo(val.toString().trim() + suffix, bookIndex, cnumber, v.getNumber());
							for (String strong : strongs) {
								List<OccurrenceInfo> occInfo = occurrences.get(strong);
								if (occInfo == null) {
									occInfo = new ArrayList<OccurrenceInfo>();
									occurrences.put(strong, occInfo);
								}
								occInfo.add(info);
							}
						}
					}
				}
			}
			// save memory for the dictionary
			bk.getChapters().clear();
			bookIndex++;
		}

		Diffable diffable = new Diffable();
		Bible dict = diffable.doImport(new File(exportArgs[0]));

		for (Book bk : dict.getBooks()) {
			if (bk.getId() != BookID.DICTIONARY_ENTRY || occurrences.get(bk.getAbbr()) == null)
				continue;
			List<OccurrenceInfo> occ = occurrences.remove(bk.getAbbr());
			FormattedText old = bk.getChapters().get(0).getProlog();
			FormattedText changed = new FormattedText();
			Visitor<RuntimeException> v = changed.getAppendVisitor();
			old.accept(v);
			v.visitHeadline(1).visitText("Occurrences in " + bible.getName());
			Collections.sort(occ);
			List<OccurrenceInfo> part = new ArrayList<>();
			while (occ.size() > 0) {
				OccurrenceInfo first = occ.remove(0);
				part.add(first);
				while (occ.size() > 0 && occ.get(0).phrase.equals(first.phrase)) {
					part.add(occ.remove(0));
				}
				v.visitFormattingInstruction(FormattingInstructionKind.BOLD).visitText(first.phrase + " (" + part.size() + "):");
				for (int i = 0; i < part.size(); i++) {
					v.visitText(i == 0 ? " " : ", ");
					int cnt = 1;
					OccurrenceInfo curr = part.get(i);
					while (i + 1 < part.size() && part.get(i + 1).equals(curr)) {
						cnt++;
						i++;
					}
					Book book = bible.getBooks().get(curr.bookIndex);
					String bookAbbr = book.getAbbr();
					BookID book1 = book.getId();
					v.visitCrossReference(bookAbbr, book1, curr.chapter, curr.verse, bookAbbr, book1, curr.chapter, curr.verse).visitText(book.getAbbr() + " " + curr.chapter + ":" + curr.verse);
					if (cnt > 1)
						v.visitText(" (" + cnt + ")");
				}
				v.visitLineBreak(ExtendedLineBreakKind.PARAGRAPH, 0);
				part.clear();
			}
			changed.finished();
			bk.getChapters().get(0).setProlog(changed);
		}
		if (!occurrences.isEmpty())
			System.out.println("Missing Strong references in dictionary: " + occurrences.keySet());
		diffable.doExport(dict, new String[] { exportArgs[1] });
	}

	private static class OccurrenceInfo implements Comparable<OccurrenceInfo> {
		private final String phrase;
		private final int bookIndex;
		private final int chapter;
		private final String verse;

		public OccurrenceInfo(String phrase, int bookIndex, int chapter, String verse) {
			this.phrase = phrase;
			this.bookIndex = bookIndex;
			this.chapter = chapter;
			this.verse = verse;
		}

		@Override
		public int compareTo(OccurrenceInfo o) {
			int result = phrase.compareTo(o.phrase);
			if (result == 0)
				result = Integer.compare(bookIndex, o.bookIndex);
			if (result == 0)
				result = Integer.compare(chapter, o.chapter);
			if (result == 0)
				result = verse.compareTo(o.verse);
			return result;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + bookIndex;
			result = prime * result + chapter;
			result = prime * result + ((phrase == null) ? 0 : phrase.hashCode());
			result = prime * result + ((verse == null) ? 0 : verse.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			OccurrenceInfo other = (OccurrenceInfo) obj;
			if (bookIndex != other.bookIndex)
				return false;
			if (chapter != other.chapter)
				return false;
			if (phrase == null) {
				if (other.phrase != null)
					return false;
			} else if (!phrase.equals(other.phrase))
				return false;
			if (verse == null) {
				if (other.verse != null)
					return false;
			} else if (!verse.equals(other.verse))
				return false;
			return true;
		}
	}

	private static class StrongInfoVisitor extends VisitorAdapter<RuntimeException> {

		private final Map<String, List<StringBuilder>> strongInfo;
		private final char prefix;

		public StrongInfoVisitor(Map<String, List<StringBuilder>> strongInfo, char prefix) throws RuntimeException {
			super(null);
			this.strongInfo = strongInfo;
			this.prefix = prefix;
		}

		@Override
		protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) throws RuntimeException {
			return this;
		}

		@Override
		public Visitor<RuntimeException> visitGrammarInformation(char[] strongsPrefixes, int[] strongs, char[] strongsSuffixes, String[] rmac, int[] sourceIndices, String[] attributeKeys, String[] attributeValues) {
			if (strongs == null)
				return this;
			StringBuilder key = new StringBuilder();
			for (int i = 0; i < strongs.length; i++) {
				if (key.length() > 0)
					key.append('+');
				key.append(strongsPrefixes != null ? strongsPrefixes[i] : prefix).append(strongs[i]).append(strongsSuffixes != null && strongsSuffixes[i] != ' ' ? "" + strongsSuffixes[i] : "");
			}
			StringBuilder value = new StringBuilder();
			List<StringBuilder> values = strongInfo.get(key.toString());
			if (values == null) {
				values = new ArrayList<>();
				strongInfo.put(key.toString(), values);
			}
			values.add(value);
			return new StrongLabelVisitor(value);
		}
	}

	private static class StrongLabelVisitor extends VisitorAdapter<RuntimeException> {

		private final StringBuilder value;

		public StrongLabelVisitor(StringBuilder value) {
			super(null);
			this.value = value;
		}

		@Override
		protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) throws RuntimeException {
			return this;
		}

		@Override
		public void visitText(String text) throws RuntimeException {
			value.append(text);
		}
	}
}
