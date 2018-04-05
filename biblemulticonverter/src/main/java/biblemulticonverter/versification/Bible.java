package biblemulticonverter.versification;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import biblemulticonverter.Main;
import biblemulticonverter.ModuleRegistry.Module;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText.ExtraAttributePriority;
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
			"Extract versification from a Bible or create dummy Bible from versification.",
			"",
			"Usage for import: <ImportFormat> <ImportFile> <VersificationName> {VERSES|TAGS|XREF}[,[...]]",
			"Export is written in Diffable format."
	};

	@Override
	public void doImport(VersificationSet vset, String... importArgs) throws Exception {
		Module<ImportFormat> importModule = Main.importFormats.get(importArgs[0]);
		biblemulticonverter.data.Bible bible = importModule.getImplementationClass().newInstance().doImport(new File(importArgs[1]));
		EnumSet<ImportSource> sources = EnumSet.noneOf(ImportSource.class);
		for (String source : importArgs[3].split(",")) {
			sources.add(ImportSource.valueOf(source.toUpperCase()));
		}
		final List<Reference> refs = new ArrayList<>();
		final List<Reference> xrefRefs = new ArrayList<>();
		for (Book bk : bible.getBooks()) {
			int cnum = 0;
			for (Chapter ch : bk.getChapters()) {
				cnum++;
				for (Verse v : ch.getVerses()) {
					if (sources.contains(ImportSource.VERSES)) {
						refs.add(new Reference(bk.getId(), cnum, v.getNumber()));
					}
					if (sources.contains(ImportSource.TAGS) || sources.contains(ImportSource.XREF)) {
						v.accept(new VisitorAdapter<RuntimeException>(null) {
							@Override
							protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) throws RuntimeException {
								return this;
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
								if (sources.contains(ImportSource.XREF) && category.equals("v11n") && key.equals("origverse")) {
									String[] parts = value.split("--");
									refs.add(new Reference(BookID.fromOsisId(parts[0]), Integer.parseInt(parts[1]), parts[2].replace('D', '.').replace('C', ',').replace('S', '/')));
								}
								return this;
							}
						});
					}
				}
			}
		}
		refs.addAll(xrefRefs);
		vset.add(Arrays.asList(Versification.fromReferenceList(importArgs[2], null, null, refs)), null);
	}

	@Override
	public boolean isExportSupported() {
		return true;
	}

	@Override
	public void doExport(File outputFile, List<Versification> versifications, List<VersificationMapping> mappings) throws Exception {
		if (versifications.size() != 1 || !mappings.isEmpty())
			throw new IllegalArgumentException("Bible export can only export one single versification and no mappings");
		Versification versification = versifications.get(0);
		biblemulticonverter.data.Bible bible = new biblemulticonverter.data.Bible(versification.getName());
		Map<BookID, Book> books = new EnumMap<>(BookID.class);
		for (int i = 0; i < versification.getVerseCount(); i++) {
			Reference r = versification.getReference(i);
			if (!books.containsKey(r.getBook())) {
				Book book = new Book(r.getBook().getOsisID(), r.getBook(), r.getBook().getEnglishName(), r.getBook().getEnglishName());
				bible.getBooks().add(book);
				books.put(r.getBook(), book);
			}
			List<Chapter> chs = books.get(r.getBook()).getChapters();
			while (chs.size() < r.getChapter())
				chs.add(new Chapter());
			Verse v = new Verse(r.getVerse());
			v.getAppendVisitor().visitText("[]");
			v.finished();
			chs.get(r.getChapter() - 1).getVerses().add(v);
		}
		new Diffable().doExport(bible, outputFile.getPath());
	}

	private static enum ImportSource {
		VERSES, TAGS, XREF
	}
}
