package biblemulticonverter.sword;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.crosswire.jsword.versification.BibleBook;

import biblemulticonverter.data.BookID;

public class BookMapping {

	public static final Map<BibleBook, BookID> MAPPING;

	public static final Set<BibleBook> UNMAPPED_BOOKS = Collections.unmodifiableSet(EnumSet.of( //
			BibleBook.PSS151, BibleBook.ESD3, BibleBook.ESD4, BibleBook.ESD5, BibleBook.JUBS,
			BibleBook.BAR4, BibleBook.ASCEN_ISA, BibleBook.PS_JOS, BibleBook.APOSTOLIC,
			BibleBook.CLEM1, BibleBook.CLEM2, BibleBook.COR3, BibleBook.EP_COR_PAUL, BibleBook.JOS_ASEN,
			BibleBook.T12PATR, BibleBook.T12PATR_TASH, BibleBook.T12PATR_TBENJ, BibleBook.T12PATR_TDAN,
			BibleBook.T12PATR_GAD, BibleBook.T12PATR_TISS, BibleBook.T12PATR_TJOS, BibleBook.T12PATR_TJUD,
			BibleBook.T12PATR_TLEVI, BibleBook.T12PATR_TNAPH, BibleBook.T12PATR_TREU,
			BibleBook.T12PATR_TSIM, BibleBook.T12PATR_TZeb, BibleBook.BAR2, BibleBook.EP_BAR, BibleBook.BARN,
			BibleBook.HERM, BibleBook.HERM_MAND, BibleBook.HERM_SIM, BibleBook.HERM_VIS));

	static {
		Map<BibleBook, BookID> mapping = new EnumMap<>(BibleBook.class);
		mapping.put(BibleBook.INTRO_BIBLE, BookID.INTRODUCTION);
		mapping.put(BibleBook.INTRO_OT, BookID.INTRODUCTION_OT);
		mapping.put(BibleBook.INTRO_NT, BookID.INTRODUCTION_NT);

		for (BibleBook bb : BibleBook.values()) {
			if (!UNMAPPED_BOOKS.contains(bb) && !mapping.containsKey(bb))
				mapping.put(bb, BookID.fromOsisId(bb.getOSIS()));
		}

		MAPPING = Collections.unmodifiableMap(mapping);
	}

	/** do not instantiate */
	private BookMapping() {
	}

	public static void main(String[] args) {
		System.out.println("LOADED");
		System.out.println(MAPPING.size() + "/" + UNMAPPED_BOOKS.size() + "/" + BibleBook.values().length);
	}
}
