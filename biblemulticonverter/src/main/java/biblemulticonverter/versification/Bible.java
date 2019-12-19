package biblemulticonverter.versification;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import biblemulticonverter.Main;
import biblemulticonverter.ModuleRegistry.Module;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.FormattedText.VisitorAdapter;
import biblemulticonverter.data.Verse;
import biblemulticonverter.data.Versification;
import biblemulticonverter.data.Versification.Reference;
import biblemulticonverter.data.VersificationMapping;
import biblemulticonverter.data.VersificationSet;
import biblemulticonverter.format.Diffable;
import biblemulticonverter.format.ImportFormat;

public class Bible implements VersificationFormat {

	public static final String[] HELP_TEXT = {
			"Extract versification/mapping from a Bible or create dummy Bible from versification/mapping.",
			"",
			"Usage for import: <ImportFormat> <ImportFile> <VersificationName> {VERSES|TAGS|XREF|UNTAGGED_VERSES}[,[...]]",
			"Import mapping: <ImportFormat> <ImportFile> <MappingName> MAPPING",
			"",
			"Export is written in Diffable format."
	};

	@Override
	public void doImport(VersificationSet vset, String... importArgs) throws Exception {
		Module<ImportFormat> importModule = Main.importFormats.get(importArgs[0]);
		biblemulticonverter.data.Bible bible = importModule.getImplementationClass().newInstance().doImport(new File(importArgs[1]));
		doImport(vset, bible, importArgs[2], importArgs[3]);
	}

	protected void doImport(VersificationSet vset, biblemulticonverter.data.Bible bible, String versificationName, String importSources) throws IOException {
		if (importSources.equalsIgnoreCase("MAPPING")) {
			final List<Reference> fromRefs = new ArrayList<>();
			final List<Reference> toRefs = new ArrayList<>();
			final Map<Reference, List<Reference>> mappings = new HashMap<>();
			for (Book bk : bible.getBooks()) {
				int cnum = 0;
				for (Chapter ch : bk.getChapters()) {
					cnum++;
					for (Verse v : ch.getVerses()) {
						Reference toRef = new Reference(bk.getId(), cnum, v.getNumber());
						toRefs.add(toRef);
						v.accept(new VisitorAdapter<RuntimeException>(null) {

							private Reference currentRef = toRef;

							@Override
							protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) {
								return this;
							}

							@Override
							public void visitVerseSeparator() {
								accountForRef();
							}

							@Override
							public void visitText(String text) {
								accountForRef();
							}

							@Override
							public void visitLineBreak(LineBreakKind kind) {
								accountForRef();
							}

							private void accountForRef() {
								if (currentRef != null) {
									if (!mappings.containsKey(currentRef))
										fromRefs.add(currentRef);
									mappings.computeIfAbsent(currentRef, k -> new ArrayList<>()).add(toRef);
									currentRef = null;
								}
							}

							@Override
							public Visitor<RuntimeException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws RuntimeException {
								if (category.equals("v11n") && key.equals("origverse")) {
									String[] parts = value.split("--");
									currentRef = new Reference(BookID.fromOsisId(parts[0]), Integer.parseInt(parts[1]), parts[2].replace('D', '.').replace('C', ',').replace('S', '/'));
									return null;
								}
								return this;
							}
						});
					}
				}
			}
			Versification vFrom = Versification.fromReferenceList(versificationName + "_From", null, null, fromRefs);
			Versification vTo = Versification.fromReferenceList(versificationName + "_To", null, null, toRefs);
			VersificationMapping mapping = VersificationMapping.build(vFrom, vTo, mappings);
			vset.add(Arrays.asList(vFrom, vTo), Arrays.asList(mapping));
			return;
		}
		EnumSet<ImportSource> sources = EnumSet.noneOf(ImportSource.class);
		for (String source : importSources.split(",")) {
			sources.add(ImportSource.valueOf(source.toUpperCase()));
		}
		final List<Reference> refs = new ArrayList<>();
		final List<Reference> xrefRefs = new ArrayList<>();
		for (Book bk : bible.getBooks()) {
			int cnum = 0;
			for (Chapter ch : bk.getChapters()) {
				cnum++;
				for (Verse v : ch.getVerses()) {
					Reference vref = new Reference(bk.getId(), cnum, v.getNumber());
					if (sources.contains(ImportSource.VERSES)) {
						refs.add(vref);
					}
					if (sources.contains(ImportSource.TAGS) || sources.contains(ImportSource.UNTAGGED_VERSES) || sources.contains(ImportSource.XREF)) {
						v.accept(new VisitorAdapter<RuntimeException>(null) {

							boolean tagFound = false;

							@Override
							protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) throws RuntimeException {
								return this;
							}

							public void visitText(String text) throws RuntimeException {
								if (sources.contains(ImportSource.UNTAGGED_VERSES) && !tagFound)
									refs.add(vref);
							}

							@Override
							public Visitor<RuntimeException> visitCrossReference(String bookAbbr, BookID book, int firstChapter, String firstVerse, int lastChapter, String lastVerse) throws RuntimeException {
								if (sources.contains(ImportSource.XREF)) {
									Reference startRef = new Reference(book, firstChapter, firstVerse);
									if (!xrefRefs.contains(startRef))
										xrefRefs.add(startRef);
									Reference endRef = new Reference(book, lastChapter, lastVerse);
									if (!xrefRefs.contains(endRef))
										xrefRefs.add(endRef);
								}
								return this;
							}

							@Override
							public Visitor<RuntimeException> visitExtraAttribute(ExtraAttributePriority prio, String category, String key, String value) throws RuntimeException {
								if (sources.contains(ImportSource.TAGS) && category.equals("v11n") && key.equals("origverse")) {
									String[] parts = value.split("--");
									refs.add(new Reference(BookID.fromOsisId(parts[0]), Integer.parseInt(parts[1]), parts[2].replace('D', '.').replace('C', ',').replace('S', '/')));
								}
								if (sources.contains(ImportSource.UNTAGGED_VERSES) && category.equals("v11n") && key.equals("origverse")) {
									tagFound = true;
								}
								return this;
							}
						});
					}
				}
			}
		}
		refs.addAll(xrefRefs);
		vset.add(Arrays.asList(Versification.fromReferenceList(versificationName, null, null, refs)), null);
	}

	@Override
	public boolean isExportSupported() {
		return true;
	}

	@Override
	public void doExport(File outputFile, List<Versification> versifications, List<VersificationMapping> mappings) throws Exception {
		biblemulticonverter.data.Bible bible = buildDummyBible(versifications, mappings);
		new Diffable().doExport(bible, outputFile.getPath());
	}

	protected biblemulticonverter.data.Bible buildDummyBible(List<Versification> versifications, List<VersificationMapping> mappings) throws IOException {
		final Versification versification;
		final VersificationMapping mapping;
		if (mappings.size() == 1 && versifications.isEmpty()) {
			mapping = mappings.get(0);
			versification = mapping.getFrom();
		} else if (versifications.size() == 1 && mappings.isEmpty()) {
			versification = versifications.get(0);
			VersificationSet vs = new VersificationSet();
			vs.add(Arrays.asList(versification), Collections.emptyList());
			mapping = vs.findMapping(versification.getName(), versification.getName(), -1);
		} else {
			throw new IllegalArgumentException("Bible export can only export one single versification and or one mapping");
		}
		biblemulticonverter.data.Bible bible = new biblemulticonverter.data.Bible(versification.getName());
		Map<BookID, Book> books = new EnumMap<>(BookID.class);
		for (int i = 0; i < versification.getVerseCount(); i++) {
			Reference sr = versification.getReference(i);
			for (Reference r : mapping.getMapping(sr)) {
				if (!books.containsKey(r.getBook())) {
					Book book = new Book(r.getBook().getOsisID(), r.getBook(), r.getBook().getEnglishName(), r.getBook().getEnglishName());
					bible.getBooks().add(book);
					books.put(r.getBook(), book);
				}
				List<Chapter> chs = books.get(r.getBook()).getChapters();
				while (chs.size() < r.getChapter())
					chs.add(new Chapter());
				Verse v = chs.get(r.getChapter() - 1).getVerses().stream().filter(vv -> vv.getNumber().equals(r.getVerse())).findFirst().orElse(null);
				boolean needSpace = v != null;
				if (v == null) {
					v = new Verse(r.getVerse());
					chs.get(r.getChapter() - 1).getVerses().add(v);
				}
				if (needSpace || !r.equals(sr)) {
					Visitor<RuntimeException> vv = v.getAppendVisitor().visitExtraAttribute(ExtraAttributePriority.KEEP_CONTENT, "v11n", "origverse", sr.getBook().getOsisID() + "--" + sr.getChapter() + "--" + sr.getVerse().replace('.', 'D').replace(',', 'C').replace('/', 'S'));
					if (needSpace)
						vv.visitText(" ");
					String verseNumber;
					if (!sr.getBook().equals(r.getBook())) {
						verseNumber = sr.getBook().getOsisID() + " " + sr.getChapter() + ":" + sr.getVerse();
					} else if (sr.getChapter() != r.getChapter()) {
						verseNumber = sr.getChapter() + ":" + sr.getVerse();
					} else {
						verseNumber = sr.getVerse();
					}
					vv.visitFormattingInstruction(FormattingInstructionKind.BOLD).visitText("(" + verseNumber + ")");
					vv.visitText(" ");
				}
				v.getAppendVisitor().visitText("[]");
			}
		}
		for (Book bk : bible.getBooks()) {
			for (Chapter ch : bk.getChapters()) {
				for (Verse v : ch.getVerses())
					v.finished();
			}
		}
		return bible;
	}

	private static enum ImportSource {
		VERSES, UNTAGGED_VERSES, TAGS, XREF
	}
}
