package biblemulticonverter.data;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import biblemulticonverter.tools.AbstractVersificationDetector.VersificationScheme;

/**
 * Represents which verses are included in a specific bible edition.
 * {@link VersificationMapping}s can be used to map between versifications and
 * compare different bible editions.
 */
public final class Versification {

	public static Versification fromReferenceList(String name, String description, String[] aliases, List<Reference> references) {
		LinkedHashMap<BookID, List<List<String>>> verseLists = new LinkedHashMap<>();
		Set<Reference> seenReferences = new HashSet<>();
		for (Reference ref : references) {
			if (!seenReferences.add(ref))
				throw new IllegalArgumentException("Duplicate reference in versification: " + ref);
			if (!verseLists.containsKey(ref.getBook()))
				verseLists.put(ref.getBook(), new ArrayList<List<String>>());
			List<List<String>> chapters = verseLists.get(ref.getBook());
			while (chapters.size() < ref.getChapter())
				chapters.add(new ArrayList<String>());
			List<String> verses = chapters.get(ref.getChapter() - 1);
			verses.add(ref.getVerse());
		}
		LinkedHashMap<BookID, VerseSet[]> verseSets = new LinkedHashMap<>();
		for (Map.Entry<BookID, List<List<String>>> bk : verseLists.entrySet()) {
			VerseSet[] vs = new VerseSet[bk.getValue().size()];
			for (int i = 0; i < vs.length; i++) {
				BitSet bits = new BitSet();
				ArrayList<String> extra = new ArrayList<>();
				for (String verse : bk.getValue().get(i)) {
					try {
						bits.set(Integer.parseInt(verse));
					} catch (NumberFormatException ex) {
						extra.add(verse);
					}
				}
				int from = 1, to = 0;
				if (!bits.isEmpty()) {
					to = bits.previousSetBit(bits.length());
					from = bits.previousClearBit(to) + 1;
					bits.clear(from, to + 1);
					BitSet old = bits;
					bits = new BitSet();
					bits.or(old);
				}
				if (!bits.isEmpty() || to >= from || !extra.isEmpty())
					vs[i] = new VerseSet(bits.isEmpty() ? null : bits, from, to, extra.isEmpty() ? null : extra);
			}
			verseSets.put(bk.getKey(), vs);
		}
		Versification unorderedResult = new Versification(name, null, null, verseSets, null);
		int lastPos = -2;
		List<Integer> reorderBoundaries = new ArrayList<>();
		for (int i = 0; i < references.size(); i++) {
			int pos = unorderedResult.getIndexForReference(references.get(i));
			if (pos != lastPos + 1) {
				reorderBoundaries.add(pos);
			}
			lastPos = pos;
		}
		Versification result = new Versification(name, description, aliases, verseSets, reorderBoundaries);
		return result;
	}

	public static Versification fromVerseCounts(String name, String description, String[] aliases, LinkedHashMap<BookID, int[]> counts) {
		LinkedHashMap<BookID, VerseSet[]> verseSets = new LinkedHashMap<>();
		for (Map.Entry<BookID, int[]> bk : counts.entrySet()) {
			VerseSet[] vs = new VerseSet[bk.getValue().length];
			for (int i = 0; i < vs.length; i++) {
				vs[i] = new VerseSet(null, 1, bk.getValue()[i], null);
			}
			verseSets.put(bk.getKey(), vs);
		}
		return new Versification(name, description, aliases, verseSets, null);
	}

	public static Versification fromStandardVersification(String name, StandardVersification sv) {
		LinkedHashMap<BookID, int[]> counts = new LinkedHashMap<>();
		for (int z = BookID.BOOK_Gen.getZefID(); z <= BookID.BOOK_Rev.getZefID(); z++) {
			BookID bk = BookID.fromZefId(z);
			int[] vc = sv.getVerseCount(bk);
			if (vc != null)
				counts.put(bk, vc);
		}
		return fromVerseCounts(name, null, null, counts);
	}

	protected static Versification fromVerseSets(String name, String description, List<String> rules) {
		List<String> aliases = new ArrayList<>();
		LinkedHashMap<BookID, VerseSet[]> verseSets = new LinkedHashMap<>();
		List<Integer> reorderBoundaries = null;
		for (String r : rules) {
			if (r.startsWith("=")) {
				aliases.add(r.substring(1));
			} else if (r.startsWith("~ ")) {
				String[] parts = r.substring(2).split(" ");
				reorderBoundaries = new ArrayList<>();
				for (int i = 0; i < parts.length; i++) {
					reorderBoundaries.add(Integer.parseInt(parts[i]));
				}
			} else {
				String[] parts = r.split(" ");
				BookID bk = BookID.fromOsisId(parts[0]);
				VerseSet[] vss = new VerseSet[parts.length - 1];
				for (int i = 0; i < vss.length; i++) {
					if (parts[i + 1].isEmpty())
						continue;
					String[] extras = parts[i + 1].split("\\+");
					int lastRangeFrom = 1, lastRangeTo = 0;
					BitSet bits = null;
					if (!extras[0].isEmpty()) {
						String[] ranges = extras[0].split(",");
						for (int j = 0; j < ranges.length; j++) {
							int rfrom, rto;
							if (ranges[j].contains("-")) {
								String[] fromto = ranges[j].split("-");
								rfrom = Integer.parseInt(fromto[0]);
								rto = Integer.parseInt(fromto[1]);
							} else {
								rfrom = rto = Integer.parseInt(ranges[j]);
							}
							if (j < ranges.length - 1) {
								if (bits == null)
									bits = new BitSet();
								bits.set(rfrom, rto + 1);
							} else {
								lastRangeFrom = rfrom;
								lastRangeTo = rto;
							}
						}
					}
					ArrayList<String> extraVerses = extras.length == 1 ? null : new ArrayList<>(Arrays.asList(extras).subList(1, extras.length));
					vss[i] = new VerseSet(bits, lastRangeFrom, lastRangeTo, extraVerses);
				}
				verseSets.put(bk, vss);
			}
		}
		return new Versification(name, description, aliases.isEmpty() ? null : (String[]) aliases.toArray(new String[aliases.size()]), verseSets, reorderBoundaries);
	}

	private String name;
	private final String description;
	private final String[] aliases;
	private final int verseCount;
	private final Map<BookID, Integer> firstVerseIndexByBook = new EnumMap<>(BookID.class);
	private final TreeMap<Integer, BookID> bookByFirstVerseIndex = new TreeMap<>();
	private final Map<BookID, VerseSet[]> verseSets = new EnumMap<>(BookID.class);
	private final TreeMap<Integer, Integer> reorderForwardOffsets = new TreeMap<>();
	private final TreeMap<Integer, Integer> reorderBackwardOffsets = new TreeMap<>();

	private Versification(String name, String description, String[] aliases, LinkedHashMap<BookID, VerseSet[]> verseSets, List<Integer> reorderBoundaries) {
		this.name = Utils.validateString("name", name, "[A-Za-z0-9._-]+");
		this.description = description;
		this.aliases = aliases;
		if (aliases != null) {
			if (aliases.length == 0)
				throw new IllegalArgumentException("aliases is empty");
			for (String alias : aliases)
				Utils.validateString("alias", alias, "[A-Za-z0-9._-]+");
		}
		this.verseSets.putAll(verseSets);
		int counter = 0;
		for (Map.Entry<BookID, VerseSet[]> book : verseSets.entrySet()) {
			firstVerseIndexByBook.put(book.getKey(), counter);
			bookByFirstVerseIndex.put(counter, book.getKey());
			for (VerseSet set : book.getValue()) {
				if (set != null)
					counter += set.getVerseCount();
			}
			if (book.getValue().length == 0)
				throw new IllegalArgumentException("Book without verse sets");
			if (book.getValue()[book.getValue().length - 1] == null)
				throw new IllegalArgumentException("Trailing null verse set detected");
		}
		verseCount = counter;
		if (reorderBoundaries == null) {
			reorderForwardOffsets.put(0, 0);
			reorderBackwardOffsets.put(0, 0);
		} else {
			TreeSet<Integer> valueSet = new TreeSet<>(reorderBoundaries);
			valueSet.add(verseCount);
			counter = 0;
			for (int start : reorderBoundaries) {
				int len = valueSet.ceiling(start + 1) - start;
				reorderForwardOffsets.put(counter, start - counter);
				reorderBackwardOffsets.put(start, counter - start);
				counter += len;
			}
			if (counter != verseCount)
				throw new RuntimeException();
		}
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String[] getAliases() {
		return aliases;
	}

	public String getDescription() {
		return description;
	}

	public int getVerseCount() {
		return verseCount;
	}

	public Reference getReference(int index) {
		if (index < 0 || index >= verseCount)
			throw new ArrayIndexOutOfBoundsException(index);
		index += reorderForwardOffsets.floorEntry(index).getValue();
		Map.Entry<Integer, BookID> bookRef = bookByFirstVerseIndex.floorEntry(index);
		index -= bookRef.getKey();
		BookID bookID = bookRef.getValue();
		int chapter = 0;
		for (VerseSet vs : verseSets.get(bookID)) {
			chapter++;
			if (vs == null)
				continue;
			if (index < vs.getVerseCount())
				return new Reference(bookID, chapter, vs.getVerse(index));
			index -= vs.getVerseCount();
		}
		throw new NoSuchElementException();
	}

	public boolean containsReference(Reference ref) {
		return containsReference(ref.getBook(), ref.getChapter(), ref.getVerse());
	}

	public int getIndexForReference(Reference ref) {
		return getIndexForReference(ref.getBook(), ref.getChapter(), ref.getVerse());
	}

	public boolean containsReference(BookID book, int chapter, String verse) {
		VerseSet[] vss = verseSets.get(book);
		if (vss == null || chapter < 1 || chapter > vss.length)
			return false;
		VerseSet vs = vss[chapter - 1];
		return vs != null && vs.containsVerse(verse);
	}

	public int getIndexForReference(BookID book, int chapter, String verse) {
		VerseSet[] vss = verseSets.get(book);
		if (vss == null || chapter < 1 || chapter > vss.length)
			return -1;
		VerseSet vs = vss[chapter - 1];
		int result = vs == null ? -1 : vs.indexOfVerse(verse);
		if (result != -1) {
			result += firstVerseIndexByBook.get(book);
			for (int i = 0; i < chapter - 1; i++) {
				if (vss[i] != null)
					result += vss[i].getVerseCount();
			}
			result += reorderBackwardOffsets.floorEntry(result).getValue();
		}
		return result;
	}

	protected void dumpVerseSets(Writer w) throws IOException {
		for (BookID bk : bookByFirstVerseIndex.values()) {
			w.write(" " + bk.getOsisID());
			for (VerseSet vs : verseSets.get(bk)) {
				w.write(" ");
				if (vs == null)
					continue;
				if (vs.verses != null) {
					int from = vs.verses.nextSetBit(0);
					while (from != -1) {
						int to = vs.verses.nextClearBit(from) - 1;
						if (to == from)
							w.write("" + from);
						else
							w.write(from + "-" + to);
						from = vs.verses.nextSetBit(to + 1);
						if (from != -1)
							w.write(',');
					}
				}
				if (vs.verses != null && !vs.verses.isEmpty() && vs.lastRangeTo >= vs.lastRangeFrom) {
					w.write(',');
				}
				if (vs.lastRangeFrom == vs.lastRangeTo)
					w.write("" + vs.lastRangeFrom);
				else if (vs.lastRangeFrom < vs.lastRangeTo)
					w.write(vs.lastRangeFrom + "-" + vs.lastRangeTo);
				if (vs.extraVerses != null) {
					for (String extraVerse : vs.extraVerses)
						w.write("+" + extraVerse);
				}
			}
			w.write('\n');
		}
		if (reorderForwardOffsets.size() > 1) {
			w.write(" ~");
			for (Map.Entry<Integer, Integer> b : reorderForwardOffsets.entrySet()) {
				w.write(" " + (b.getKey() + b.getValue()));
			}
			w.write('\n');
		}
	}

	public VersificationScheme toNewVersificationScheme() {
		Map<BookID, BitSet[]> coveredBooks = new EnumMap<BookID, BitSet[]>(BookID.class);
		for (Map.Entry<BookID, VerseSet[]> entry : verseSets.entrySet()) {
			BitSet[] value = new BitSet[entry.getValue().length];
			for (int i = 0; i < value.length; i++) {
				value[i] = new BitSet();
				VerseSet vs = entry.getValue()[i];
				if (vs == null)
					continue;
				if (vs.verses != null)
					value[i].or(vs.verses);
				if (vs.lastRangeTo >= vs.lastRangeFrom)
					value[i].set(vs.lastRangeFrom, vs.lastRangeTo + 1);
			}
			coveredBooks.put(entry.getKey(), value);
		}
		return new VersificationScheme(name, coveredBooks);
	}

	private static class VerseSet {
		private final BitSet verses;
		private final int lastRangeFrom;
		private final int lastRangeTo;
		private final ArrayList<String> extraVerses;
		private final int verseCount;

		private VerseSet(BitSet verses, int lastRangeFrom, int lastRangeTo, ArrayList<String> extraVerses) {
			if (verses != null && verses.isEmpty())
				throw new IllegalStateException();
			this.verses = verses;
			this.lastRangeFrom = Utils.validateNumber("lastRangeFrom", lastRangeFrom, 1, Integer.MAX_VALUE);
			this.lastRangeTo = Utils.validateNumber("lastRangeTo", lastRangeTo, lastRangeFrom - 1, Integer.MAX_VALUE);
			if (extraVerses != null && extraVerses.isEmpty())
				throw new IllegalStateException();
			this.extraVerses = extraVerses;
			this.verseCount = (verses == null ? 0 : verses.cardinality()) +
					(lastRangeTo - lastRangeFrom + 1) +
					(extraVerses == null ? 0 : extraVerses.size());
			if (verseCount == 0)
				throw new IllegalStateException();
		}

		private int getVerseCount() {
			return verseCount;
		}

		private boolean containsVerse(String verse) {
			try {
				int verseNumber = Integer.parseInt(verse);
				if (verseNumber >= lastRangeFrom && verseNumber <= lastRangeTo) {
					return true;
				} else if (verseNumber < lastRangeFrom && verses != null && verses.get(verseNumber)) {
					return true;
				} else {
					return false;
				}
			} catch (NumberFormatException ex) {
				return extraVerses != null && extraVerses.contains(verse);
			}
		}

		private int indexOfVerse(String verse) {
			try {
				int verseNumber = Integer.parseInt(verse);
				if (verseNumber >= lastRangeFrom && verseNumber <= lastRangeTo) {
					return verseNumber - lastRangeFrom + (verses == null ? 0 : verses.cardinality());
				} else if (verseNumber < lastRangeFrom && verses != null && verses.get(verseNumber)) {
					int result = 0;
					for (int i = verses.nextSetBit(0); i != verseNumber && i != -1; i = verses.nextSetBit(i + 1)) {
						result++;
					}
					return result;
				} else {
					return -1;
				}
			} catch (NumberFormatException ex) {
				int pos = extraVerses == null ? -1 : extraVerses.indexOf(verse);
				if (pos != -1)
					pos += verseCount - extraVerses.size();
				return pos;
			}
		}

		private String getVerse(int index) {
			if (index >= verseCount)
				throw new ArrayIndexOutOfBoundsException();
			if (verses != null) {
				if (index < verses.cardinality()) {
					for (int i = verses.nextSetBit(0); i != -1; i = verses.nextSetBit(i + 1)) {
						if (index == 0)
							return String.valueOf(i);
						index--;
					}
				} else {
					index -= verses.cardinality();
				}
			}
			if (index < lastRangeTo - lastRangeFrom + 1) {
				return String.valueOf(lastRangeFrom + index);
			}
			return extraVerses.get(index - lastRangeTo + lastRangeFrom - 1);
		}
	}

	public static class Reference {
		private final BookID book;
		private final int chapter;
		private final String verse;

		public Reference(BookID book, int chapter, String verse) {
			this.book = Utils.validateNonNull("book", book);
			this.chapter = Utils.validateNumber("chapter", chapter, 1, Integer.MAX_VALUE);
			this.verse = Utils.validateString("verse", verse, Utils.VERSE_REGEX);
		}

		public BookID getBook() {
			return book;
		}

		public int getChapter() {
			return chapter;
		}

		public String getVerse() {
			return verse;
		}

		@Override
		public String toString() {
			return book.getOsisID() + " " + chapter + ":" + verse;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Reference other = (Reference) obj;
			if (book != other.book)
				return false;
			if (chapter != other.chapter)
				return false;
			if (verse == null) {
				if (other.verse != null)
					return false;
			} else if (!verse.equals(other.verse))
				return false;
			return true;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((book == null) ? 0 : book.hashCode());
			result = prime * result + chapter;
			result = prime * result + ((verse == null) ? 0 : verse.hashCode());
			return result;
		}
	}
}
