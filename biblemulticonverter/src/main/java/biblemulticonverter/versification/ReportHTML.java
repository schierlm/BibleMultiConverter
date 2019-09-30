package biblemulticonverter.versification;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Versification;
import biblemulticonverter.data.Versification.Reference;
import biblemulticonverter.data.VersificationMapping;
import biblemulticonverter.data.VersificationSet;

public class ReportHTML implements VersificationFormat {

	public static final String[] HELP_TEXT = {
			"HTML report that shows difference of covered verses.",
			"",
			"If the filename contains 'flat', verse maps are not merged if they are the same in one book.",
			"If the filename contains 'flip', columns and rows are flipped (as on the Logos Verse Maps wiki page).",
			"",
			"If no versifications are included, but some mappings, all those mappings need the same source versification.",
			"In that case, only one output layout is supported (no 'flat' or 'flip' options)."
	};

	@Override
	public void doImport(VersificationSet vset, String... importArgs) throws Exception {
		throw new UnsupportedOperationException("HTML import not supported");
	}

	@Override
	public boolean isExportSupported() {
		return true;
	}

	@Override
	public void doExport(File outputFile, List<Versification> versifications, List<VersificationMapping> mappings) throws Exception {
		if (!versifications.isEmpty())
			doExportVersifications(outputFile, versifications);
		else if (!mappings.isEmpty())
			doExportMappings(outputFile, mappings);
		else
			throw new IOException("Neither versifications nor mappings present to export");
	}

	public void doExportMappings(File outputFile, List<VersificationMapping> mappings) throws Exception {
		Versification from = mappings.get(0).getFrom();
		for (VersificationMapping m : mappings) {
			if (m.getFrom() != from)
				throw new IOException("All mappings need to have the same source versification");
		}
		Set<BookID> fromBooks = EnumSet.noneOf(BookID.class);
		for (int i = 0; i < from.getVerseCount(); i++) {
			fromBooks.add(from.getReference(i).getBook());
		}
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
			bw.write("<!DOCTYPE html>\n" +
					"<html>\n" +
					"<head>\n" +
					"<meta charset=\"UTF-8\">\n" +
					"<title>Versification report</title>\n" +
					"<style type=\"text/css\">\n" +
					"body { font-family: Verdana, Arial, Helvetica, sans-serif; }\n" +
					"table {border-collapse: collapse; }\n" +
					"td, th {border: 1px solid #777; padding: 5px; white-space: nowrap; text-align: left; vertical-align: top; }\n" +
					"td.diff {color: red; }\n" +
					"tr.same th, tr.same td {color: gray; padding: 1px; font-size: 50%; }\n" +
					"td.skipped { text-align: center;}\n" +
					"</style>\n" +
					"</head>\n" +
					"<body>\n" +
					"<h1>Versification mapping report</h1>\n" +
					"<ul>\n");
			for (BookID bid : fromBooks) {
				bw.write("<li><a href=\"#" + h(bid.getOsisID()) + "\">" + h(bid.getEnglishName()) + "</a></li>\n");
			}
			bw.write("</ul>\n");
			for (BookID bid : fromBooks) {
				bw.write("<h2 id=\"" + h(bid.getOsisID()) + "\">" + h(bid.getEnglishName()) + "</h2>\n<table>\n");
				bw.write("<tr><th>" + h(from.getName()) + "</th>");
				for (VersificationMapping m : mappings) {
					bw.write("<th>" + h(m.getTo().getName()) + "</th>");
				}
				bw.write("</tr>\n");
				Reference[] lastRefs = null;
				int unwrittenRefs = 0;
				for (int i = 0; i < from.getVerseCount(); i++) {
					Reference r = from.getReference(i);
					if (r.getBook() != bid)
						continue;
					List<List<Reference>> mappingInfo = mappings.stream().map(m -> m.getMapping(r)).collect(Collectors.toList());
					boolean isContinuation = mappingInfo.stream().allMatch(l -> l.size() == 1);
					Reference[] newRefs = null;
					if (isContinuation) {
						newRefs = new Reference[mappings.size() + 1];
						newRefs[0] = r;
						for (int j = 1; j < newRefs.length; j++) {
							newRefs[j] = mappingInfo.get(j - 1).get(0);
						}
						if (lastRefs == null) {
							isContinuation = false;
						} else {
							for (int j = 0; j < newRefs.length; j++) {
								if (newRefs[j].getBook() != lastRefs[j].getBook() || newRefs[j].getChapter() != newRefs[j].getChapter()) {
									isContinuation = false;
									break;
								}
								if (!newRefs[j].getVerse().matches("[0-9]+") || !lastRefs[j].getVerse().matches("[0-9]+")) {
									isContinuation = false;
									break;
								}
								int lastVerse = Integer.parseInt(lastRefs[j].getVerse());
								int newVerse = Integer.parseInt(newRefs[j].getVerse());
								if (newVerse != lastVerse + 1) {
									isContinuation = false;
									break;
								}
							}
						}
					}
					if (isContinuation) {
						unwrittenRefs++;
					} else {
						flushUnwrittenRefs(bw, unwrittenRefs, lastRefs);
						unwrittenRefs = 0;
						if (newRefs != null) {
							flushUnwrittenRefs(bw, 1, newRefs);
						} else {
							bw.write("<tr><th>" + h(r.toString()) + "</th>");
							for (List<Reference> mr : mappingInfo) {
								bw.write(mr.size() == 1 && mr.get(0).equals(r) ? "<td>" : "<td class=\"diff\">");
								boolean first = true;
								for (Reference rr : mr) {
									if (!first)
										bw.write("<br>");
									first = false;
									bw.write(h(rr.toString()));
								}
								bw.write("</td>");
							}
							bw.write("</tr>\n");
						}
					}
					lastRefs = newRefs;
				}
				flushUnwrittenRefs(bw, unwrittenRefs, lastRefs);
				bw.write("</table>\n");
			}
		}
		mappings.get(0).getFrom().getName();
	}

	private void flushUnwrittenRefs(BufferedWriter bw, int unwrittenRefs, Reference[] lastRefs) throws IOException {
		if (unwrittenRefs == 0)
			return;
		boolean allSame = true;
		for (int i = 1; i < lastRefs.length; i++) {
			if (!lastRefs[i].equals(lastRefs[0])) {
				allSame = false;
				break;
			}
		}
		if (unwrittenRefs > 1) {
			bw.write(allSame ? "<tr class=\"same\">" : "<tr>");
			bw.write("<td class=\"skipped" + (allSame ? "" : " diff") + "\" colspan=\"" + lastRefs.length + "\">... [+" + (unwrittenRefs - 1) + "]</td></tr>\n");
		}
		bw.write(allSame ? "<tr class=\"same\">" : "<tr>");
		bw.write("<th>" + h(lastRefs[0].toString()) + "</th>");
		for (int i = 1; i < lastRefs.length; i++) {
			bw.write(lastRefs[i].equals(lastRefs[0]) ? "<td>" : "<td class=\"diff\">");
			bw.write(h(lastRefs[i].toString()) + "</td>");
		}
		bw.write("</tr>\n");
	}

	private void doExportVersifications(File outputFile, List<Versification> versifications) throws Exception {
		Map<BookID, BookVersification[]> bookVersifications = new EnumMap<>(BookID.class);
		for (int i = 0; i < versifications.size(); i++) {
			Versification v11n = versifications.get(i);
			for (int j = 0; j < v11n.getVerseCount(); j++) {
				Reference r = v11n.getReference(j);
				if (!bookVersifications.containsKey(r.getBook())) {
					BookVersification[] bvs = new BookVersification[versifications.size()];
					for (int k = 0; k < bvs.length; k++) {
						bvs[k] = new BookVersification();
					}
					bookVersifications.put(r.getBook(), bvs);
				}
				bookVersifications.get(r.getBook())[i].addVerse(r.getChapter(), r.getVerse());
			}
		}
		boolean flip = outputFile.getName().contains("flip");
		boolean flat = outputFile.getName().contains("flat");
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
			bw.write("<!DOCTYPE html>\n" +
					"<html>\n" +
					"<head>\n" +
					"<meta charset=\"UTF-8\">\n" +
					"<title>Versification report</title>\n" +
					"<style type=\"text/css\">\n" +
					"body { font-family: Verdana, Arial, Helvetica, sans-serif; }\n" +
					"table {border-collapse: collapse; }\n" +
					"td, th {border: 1px solid #777; white-space: nowrap; text-align: left; vertical-align: top; }\n" +
					"</style>\n" +
					"</head>\n" +
					"<body>\n" +
					"<h1>Versification report</h1>\n" +
					"<ul>\n");
			for (BookID bid : bookVersifications.keySet()) {
				bw.write("<li><a href=\"#" + h(bid.getOsisID()) + "\">" + h(bid.getEnglishName()) + "</a></li>\n");
			}
			bw.write("</ul>\n");
			for (BookID bid : bookVersifications.keySet()) {
				Map<String, BookVersification> table = new HashMap<>();
				int maxChaps = 0;
				outer: for (int i = 0; i < bookVersifications.get(bid).length; i++) {
					String name = versifications.get(i).getName();
					BookVersification bv = bookVersifications.get(bid)[i];
					if (bv.getChapterCount() > maxChaps)
						maxChaps = bv.getChapterCount();
					if (!flat) {
						for (Map.Entry<String, BookVersification> other : table.entrySet()) {
							if (other.getValue().equals(bv)) {
								table.remove(other.getKey());
								List<String> keyParts = new ArrayList<>(Arrays.asList(other.getKey().split(" ")));
								keyParts.add(name);
								Collections.sort(keyParts);
								table.put(String.join(" ", keyParts), other.getValue());
								continue outer;
							}
						}
					}
					table.put(name, bv);
				}
				List<String> tableKeys = new ArrayList<>(table.keySet());
				tableKeys.sort(Comparator.comparing((String s) -> s.replaceAll("[^ ]", "").replace(' ', 'x')).reversed().thenComparing(s -> s));
				bw.write("<h2 id=\"" + h(bid.getOsisID()) + "\">" + h(bid.getEnglishName()) + "</h2>\n<table>\n<tr><th>&nbsp;</th>");
				if (flip) {
					for (String key : tableKeys) {
						bw.write("<th>" + h(key).replace(" ", "<br>") + "</th>");
					}
					bw.write("</tr>\n");
					for (int i = 1; i <= maxChaps; i++) {
						bw.write("<tr><th>" + i + "</th>");
						for (String key : tableKeys) {
							bw.write("<td>" + h(table.get(key).getChapter(i).getVerseString()) + "</td>");
						}
						bw.write("</tr>\n");
					}
				} else {
					for (int i = 1; i <= maxChaps; i++) {
						bw.write("<th>" + i + "</th>");
					}
					bw.write("</tr>\n");
					for (String key : tableKeys) {
						bw.write("<tr><th>" + renderKey(key) + "</th>");
						for (int i = 1; i <= maxChaps; i++) {
							bw.write("<td>" + h(table.get(key).getChapter(i).getVerseString()) + "</td>");
						}
						bw.write("</tr>\n");
					}
				}
				bw.write("</table>\n");
			}
		}
	}

	private static String renderKey(String key) {
		if (!key.contains(" "))
			return h(key);
		return "<span title=\"" + h(key).replace(" ", "&#10;") + "\">" + h(key.replaceFirst(" .*", "") + " (+" + key.replaceAll("[^ ]", "").length()) + ")</span>";
	}

	private static String h(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	private static class BookVersification {
		private final List<BookVersificationChapter> chapters = new ArrayList<>();

		public void addVerse(int chapter, String verse) {
			while (chapter > chapters.size())
				chapters.add(new BookVersificationChapter());
			chapters.get(chapter - 1).addVerse(verse);
		}

		public int getChapterCount() {
			return chapters.size();
		}

		public BookVersificationChapter getChapter(int chapter) {
			return chapter <= chapters.size() ? chapters.get(chapter - 1) : BookVersificationChapter.EMPTY_CHAPTER;
		}

		@Override
		public int hashCode() {
			return chapters.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			BookVersification other = (BookVersification) obj;
			return chapters.equals(other.chapters);
		}
	}

	private static class BookVersificationChapter {

		public static final BookVersificationChapter EMPTY_CHAPTER = new BookVersificationChapter();

		private StringBuilder verses = new StringBuilder();
		private int rangeStart = -1, rangeEnd = -1;

		private void doAdd(String verse) {
			if (verses.length() > 0)
				verses.append(",");
			verses.append(verse);
		}

		private void finishRange() {
			if (rangeStart != -1) {
				if (rangeStart == rangeEnd)
					doAdd("" + rangeStart);
				else
					doAdd(rangeStart + "-" + rangeEnd);
				rangeStart = rangeEnd = -1;
			}
		}

		public String getVerseString() {
			finishRange();
			return verses.toString();
		}

		public void addVerse(String verse) {
			try {
				int vno = Integer.parseInt(verse);
				if (vno <= 0)
					throw new IllegalStateException();
				if (vno == rangeEnd + 1) {
					rangeEnd++;
				} else {
					finishRange();
					rangeStart = rangeEnd = vno;
				}
			} catch (NumberFormatException ex) {
				finishRange();
				doAdd(verse.equals("1/t") ? "title" : verse);
			}
		}

		@Override
		public int hashCode() {
			return getVerseString().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			BookVersificationChapter other = (BookVersificationChapter) obj;
			return getVerseString().equals(other.getVerseString());
		}
	}
}
