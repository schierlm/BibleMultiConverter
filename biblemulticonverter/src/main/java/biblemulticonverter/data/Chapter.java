package biblemulticonverter.data;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import biblemulticonverter.data.FormattedText.Headline;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.FormattedText.VisitorAdapter;

/**
 * Represents a chapter of the bible, with an optional prolog and multiple
 * verses.
 */
public class Chapter {

	private FormattedText prolog;
	private final List<Verse> verses;

	public Chapter() {
		this.prolog = null;
		this.verses = new ArrayList<Verse>();
	}

	public void validate(Bible bible, BookID book, String bookAbbr, int cnumber, List<String> danglingReferences, Map<String,Set<String>> dictionaryEntries) {
		// chapters may have no verses, if not yet translated but a later
		// chapter is.
		if (prolog != null)
			prolog.validate(bible, book, bookAbbr + " " + cnumber + ":Prolog", danglingReferences, dictionaryEntries);
		Set<String> verseNumbers = new HashSet<String>();
		for (Verse verse : verses) {
			if (!verseNumbers.add(verse.getNumber()))
				throw new IllegalStateException("Duplicate verse number " + bookAbbr + " " + cnumber + ":" + verse.getNumber());
			verse.validate(bible, book, bookAbbr + " " + cnumber + ":" + verse.getNumber(), danglingReferences, dictionaryEntries);
		}
		int lastVerse = 0;
		for (VirtualVerse vv : createVirtualVerses()) {
			if (vv.getNumber() <= lastVerse)
				throw new IllegalStateException("Invalid order of virtual verses: " + vv.getNumber() + " after " + lastVerse);
			lastVerse = vv.getNumber();
			vv.validate(bible, book, bookAbbr, cnumber, danglingReferences, dictionaryEntries);
		}
		List<VerseRange> ranges = createVerseRanges();
		for (VerseRange vr : ranges) {
			for (VerseRange vr2 : ranges) {
				if (vr != vr2 && vr.overlaps(vr2))
					throw new IllegalStateException("Overlapping verse ranges: " + vr.getMinVerse() + "-" + vr.getMaxVerse() + " and " + vr2.getMinVerse() + "-" + vr2.getMaxVerse());
			}
			vr.validate(bible, book, bookAbbr, cnumber, danglingReferences, dictionaryEntries);
		}
	}

	public FormattedText getProlog() {
		return prolog;
	}

	public void setProlog(FormattedText prolog) {
		this.prolog = prolog;
	}

	public List<Verse> getVerses() {
		return verses;
	}

	public List<VirtualVerse> createVirtualVerses() {
		return createVirtualVerses(false);
	}

	public List<VirtualVerse> createVirtualVerses(boolean titleAsVerseZero) {

		// split up verses to separate headlines
		final List<VirtualVerse> tempVerses = new ArrayList<VirtualVerse>();
		BitSet numericVerseNumbers = new BitSet(verses.size());
		for (final Verse verse : verses) {
			int num;
			try {
				num = Integer.parseInt(verse.getNumber());
				numericVerseNumbers.set(num);
			} catch (NumberFormatException ex) {
				// ignore nonnumeric verse numbers
				num = Integer.MAX_VALUE;
				if (titleAsVerseZero && verse.getNumber().equals("1/t"))
					num = 0;
			}
			final int vnum = num;
			verse.accept(new VisitorAdapter<RuntimeException>(null) {

				VirtualVerse vv = new VirtualVerse(vnum);
				boolean hasContent = false;

				{
					tempVerses.add(vv);
					vv.getVerses().add(new Verse(verse.getNumber()));
				}

				@Override
				public Visitor<RuntimeException> visitHeadline(int depth) {
					Headline h = new Headline(depth);
					if (hasContent) {
						vv = new VirtualVerse(vnum);
						tempVerses.add(vv);
						vv.getVerses().add(new Verse(verse.getNumber()));
						hasContent = false;
					}
					vv.getHeadlines().add(h);
					return h.getAppendVisitor();
				}

				@Override
				public int visitElementTypes(String elementTypes) throws RuntimeException {
					return 0;
				}

				@Override
				public void visitStart() {
					hasContent = true;
				}

				@Override
				public boolean visitEnd() throws RuntimeException {
					hasContent = true;
					return false;
				}

				@Override
				protected void beforeVisit() {
					hasContent = true;
				}

				@Override
				protected Visitor<RuntimeException> getVisitor() {
					return vv.getVerses().get(0).getAppendVisitor();
				}
			});
		}

		// group verses sensibly
		List<VirtualVerse> result = new ArrayList<VirtualVerse>();
		VirtualVerse current = null;
		int nextverse = titleAsVerseZero ? 0 : 1;
		for (VirtualVerse vv : tempVerses) {
			for (Headline h : vv.getHeadlines())
				h.finished();
			for (Verse v : vv.getVerses())
				v.finished();
			boolean makeNew;
			if (current == null || vv.getHeadlines().size() > 0) {
				makeNew = true;
				if (vv.getNumber() != Integer.MAX_VALUE && vv.getNumber() > nextverse) {
					nextverse = vv.getNumber();
				}
			} else if (vv.getNumber() == Integer.MAX_VALUE) {
				makeNew = false;
			} else {
				// numeric verse without headlines; may be both as new verse and
				// as appended one;
				// decide based on verse number
				int vnum = vv.getNumber();
				if (vnum < nextverse) {
					makeNew = false;
				} else if (vnum > nextverse + 1 && numericVerseNumbers.nextSetBit(nextverse) < vnum) {
					makeNew = false;
					numericVerseNumbers.clear(vnum);
				} else {
					makeNew = true;
					nextverse = vnum;
				}
			}
			if (makeNew) {
				current = new VirtualVerse(nextverse);
				current.getHeadlines().addAll(vv.getHeadlines());
				for (Verse vvv : vv.getVerses()) {
					if (vvv.getElementTypes(1).length() > 0)
						current.getVerses().add(vvv);
				}
				result.add(current);
				nextverse++;
			} else {
				for (Verse vvv : vv.getVerses()) {
					if (vvv.getElementTypes(1).length() > 0)
						current.getVerses().add(vvv);
				}
			}
		}
		return result;
	}

	public List<VirtualVerse> createVirtualVerses(BitSet allowedVerseNumbers) {
		return createVirtualVerses(false, allowedVerseNumbers);
	}
	public List<VirtualVerse> createVirtualVerses(boolean titleAsVerseZero, BitSet allowedVerseNumbers) {
		List<VirtualVerse> result = createVirtualVerses(titleAsVerseZero);
		if (allowedVerseNumbers == null)
			return result;
		boolean unsatisfied = false;
		VirtualVerse previous = null;
		// try to combine unsatisifed verses with the previous one or push them
		// if not possible
		for (int i = 0; i < result.size(); i++) {
			VirtualVerse current = result.get(i);
			int prevNumber = previous == null ? (titleAsVerseZero ? -1 : 0) : previous.getNumber();
			if (!allowedVerseNumbers.get(current.getNumber()) || (current.getNumber() <= prevNumber)) {
				if (previous != null && current.getHeadlines().size() == 0) {
					// combine it with the previous one
					previous.getVerses().addAll(current.getVerses());
					result.remove(i);
					i--;
					continue;
				}
				// cannot combine; find a new number for it
				int nextNumber = allowedVerseNumbers.nextSetBit(prevNumber + 1);
				if (nextNumber == -1) {
					nextNumber = Math.min(prevNumber + 1, 1000);
					unsatisfied = true;
				}
				VirtualVerse renumbered = new VirtualVerse(nextNumber);
				renumbered.getHeadlines().addAll(current.getHeadlines());
				renumbered.getVerses().addAll(current.getVerses());
				current = renumbered;
				result.set(i, current);
			}
			previous = current;
		}

		if (!unsatisfied)
			return result;

		// we have to jumble the verses a bit more now. First make sure we have
		// less virtual verses than we have available bits. If not, merge from
		// the end until satisfied.
		for (int i = result.size() - 1; i >= 1; i--) {
			if (result.size() <= allowedVerseNumbers.cardinality())
				break;
			VirtualVerse vv = result.get(i);
			if (vv.getHeadlines().size() == 0) {
				VirtualVerse prev = result.get(i - 1);
				prev.getVerses().addAll(vv.getVerses());
				result.remove(i);
			}
		}

		if (result.size() > allowedVerseNumbers.cardinality())
			throw new RuntimeException("Unable to satisfy verse map: more headlines (" + result.size() + ") than available verse numbers (" + allowedVerseNumbers.cardinality() + ")");

		// now try to renumber what is needed to fit the verses
		int lastNumber = titleAsVerseZero ? -1 : 0;
		for (int i = 0; i < result.size(); i++) {
			VirtualVerse vv = result.get(i);
			int remainingVerses = result.size() - i;
			int newNumber = allowedVerseNumbers.nextSetBit(lastNumber + 1);
			int remainingNumbers = allowedVerseNumbers.get(newNumber, Integer.MAX_VALUE).cardinality();
			int remainingIfUnchanged = allowedVerseNumbers.get(vv.getNumber(), Integer.MAX_VALUE).cardinality();
			if (!allowedVerseNumbers.get(vv.getNumber()) || remainingIfUnchanged < remainingVerses) {
				if (remainingNumbers < remainingVerses)
					throw new RuntimeException("Unable to satisfy verse map");
				VirtualVerse renumbered = new VirtualVerse(newNumber);
				renumbered.getHeadlines().addAll(vv.getHeadlines());
				renumbered.getVerses().addAll(vv.getVerses());
				vv = renumbered;
				result.set(i, vv);
			}
			lastNumber = vv.getNumber();
		}
		return result;
	}

	public List<VerseRange> createVerseRanges() {

		// create individual verse range for every verse
		final List<VerseRange> individualRanges = new ArrayList<>();
		for (final Verse verse : verses) {
			int chapter = 0, minVerse = Integer.MAX_VALUE, maxVerse = -1;
			for (String part : verse.getNumber().split("[/.a-zG-]+")) {
				int cc, vv;
				if (part.matches("[0-9]+,[0-9]+")) {
					String[] subparts = part.split(",");
					cc = Integer.parseInt(subparts[0]);
					vv = Integer.parseInt(subparts[1]);
				} else {
					cc = 0;
					vv = Integer.parseInt(part);
				}
				if (cc != chapter) {
					if (chapter == 0 && minVerse == Integer.MAX_VALUE) {
						// no (main) verses seen yet, use this extra verse:
						chapter = cc;
						minVerse = maxVerse = vv;
					} else if (cc == 0) {
						// main verse beats extra verse
						chapter = 0;
						minVerse = maxVerse = vv;
					} else {
						// ignore other extra verses
						continue;
					}
				}
				minVerse = Math.min(minVerse, vv);
				maxVerse = Math.max(maxVerse, vv);
			}
			individualRanges.add(new VerseRange(chapter, minVerse, maxVerse, verse));
		}

		// find overlapping verse ranges and combine them
		final List<VerseRange> result = new ArrayList<>();
		for (VerseRange current : individualRanges) {
			// do checking backwards: Consider ranges 2, 5, 1, 3, 4.6 for example 
			for (int i = result.size() - 1; i >= 0; i--) {
				VerseRange r2 = result.get(i);
				if (current.overlaps(r2)) {
					// merge all ranges up to that point
					while (result.size() > i) {
						current = new VerseRange(result.remove(result.size() - 1), current);
					}
				}
				// and continue checking the rest
			}
			result.add(current);
		}
		return result;
	}

	public int getVerseIndex(String verseNumber) {
		for (int i = 0; i < verses.size(); i++) {
			if (verses.get(i).getNumber().equals(verseNumber))
				return i;
		}
		return -1;
	}
}
