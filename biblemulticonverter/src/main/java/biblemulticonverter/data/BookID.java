package biblemulticonverter.data;

/**
 * ID for uniquely referring to a bible book.
 */
public enum BookID {

	METADATA("x-Meta", false, -4, "Metadata", "Mta"),

	INTRODUCTION("x-Intr", false, -1, "Introduction", "Int"),
	INTRODUCTION_OT("x-IntrOT", false, -2, "Introduction OT", "IOT"),

	BOOK_Gen("Gen", false, 1, "Genesis", "Gen"),
	BOOK_Exod("Exod", false, 2, "Exodus", "Exo"),
	BOOK_Lev("Lev", false, 3, "Leviticus", "Lev"),
	BOOK_Num("Num", false, 4, "Numbers", "Num"),
	BOOK_Deut("Deut", false, 5, "Deuteronomy", "Deu"),
	BOOK_Josh("Josh", false, 6, "Joshua", "Jos"),
	BOOK_Judg("Judg", false, 7, "Judges", "Jdg"),
	BOOK_Ruth("Ruth", false, 8, "Ruth", "Rth"),
	BOOK_1Sam("1Sam", false, 9, "1 Samuel", "1Sa"),
	BOOK_2Sam("2Sam", false, 10, "2 Samuel", "2Sa"),
	BOOK_1Kgs("1Kgs", false, 11, "1 Kings", "1Ki"),
	BOOK_2Kgs("2Kgs", false, 12, "2 Kings", "2Ki"),
	BOOK_1Chr("1Chr", false, 13, "1 Chronicles", "1Ch"),
	BOOK_2Chr("2Chr", false, 14, "2 Chronicles", "2Ch"),
	BOOK_Ezra("Ezra", false, 15, "Ezra", "Ezr"),
	BOOK_Neh("Neh", false, 16, "Nehemiah", "Neh"),
	BOOK_Esth("Esth", false, 17, "Esther", "Est"),
	BOOK_Job("Job", false, 18, "Job", "Job"),
	BOOK_Ps("Ps", false, 19, "Psalm", "Psa"),
	BOOK_Prov("Prov", false, 20, "Proverbs", "Pro"),
	BOOK_Eccl("Eccl", false, 21, "Ecclesiastes", "Ecc"),
	BOOK_Song("Song", false, 22, "Song of Solomon", "Son"),
	BOOK_Isa("Isa", false, 23, "Isaiah", "Isa"),
	BOOK_Jer("Jer", false, 24, "Jeremiah", "Jer"),
	BOOK_Lam("Lam", false, 25, "Lamentations", "Lam"),
	BOOK_Ezek("Ezek", false, 26, "Ezekiel", "Eze"),
	BOOK_Dan("Dan", false, 27, "Daniel", "Dan"),
	BOOK_Hos("Hos", false, 28, "Hosea", "Hos"),
	BOOK_Joel("Joel", false, 29, "Joel", "Joe"),
	BOOK_Amos("Amos", false, 30, "Amos", "Amo"),
	BOOK_Obad("Obad", false, 31, "Obadiah", "Oba"),
	BOOK_Jonah("Jonah", false, 32, "Jonah", "Jon"),
	BOOK_Mic("Mic", false, 33, "Micah", "Mic"),
	BOOK_Nah("Nah", false, 34, "Nahum", "Nah"),
	BOOK_Hab("Hab", false, 35, "Habakkuk", "Hab"),
	BOOK_Zeph("Zeph", false, 36, "Zephaniah", "Zep"),
	BOOK_Hag("Hag", false, 37, "Haggai", "Hag"),
	BOOK_Zech("Zech", false, 38, "Zechariah", "Zec"),
	BOOK_Mal("Mal", false, 39, "Malachi", "Mal"),

	INTRODUCTION_NT("x-IntrNT", true, -3, "Introduction NT", "INT"),

	BOOK_Matt("Matt", true, 40, "Matthew", "Mat"),
	BOOK_Mark("Mark", true, 41, "Mark", "Mar"),
	BOOK_Luke("Luke", true, 42, "Luke", "Luk"),
	BOOK_John("John", true, 43, "John", "Joh"),
	BOOK_Acts("Acts", true, 44, "Acts", "Act"),
	BOOK_Rom("Rom", true, 45, "Romans", "Rom"),
	BOOK_1Cor("1Cor", true, 46, "1 Corinthians", "1Co"),
	BOOK_2Cor("2Cor", true, 47, "2 Corinthians", "2Co"),
	BOOK_Gal("Gal", true, 48, "Galatians", "Gal"),
	BOOK_Eph("Eph", true, 49, "Ephesians", "Eph"),
	BOOK_Phil("Phil", true, 50, "Philippians", "Php"),
	BOOK_Col("Col", true, 51, "Colossians", "Col"),
	BOOK_1Thess("1Thess", true, 52, "1 Thessalonians", "1Th"),
	BOOK_2Thess("2Thess", true, 53, "2 Thessalonians", "2Th"),
	BOOK_1Tim("1Tim", true, 54, "1 Timothy", "1Ti"),
	BOOK_2Tim("2Tim", true, 55, "2 Timothy", "2Ti"),
	BOOK_Titus("Titus", true, 56, "Titus", "Tit"),
	BOOK_Phlm("Phlm", true, 57, "Philemon", "Phm"),
	BOOK_Heb("Heb", true, 58, "Hebrews", "Heb"),
	BOOK_Jas("Jas", true, 59, "James", "Jas"),
	BOOK_1Pet("1Pet", true, 60, "1 Peter", "1Pe"),
	BOOK_2Pet("2Pet", true, 61, "2 Peter", "2Pe"),
	BOOK_1John("1John", true, 62, "1 John", "1Jn"),
	BOOK_2John("2John", true, 63, "2 John", "2Jn"),
	BOOK_3John("3John", true, 64, "3 John", "3Jn"),
	BOOK_Jude("Jude", true, 65, "Jude", "Jud"),
	BOOK_Rev("Rev", true, 66, "Revelation", "Rev"),

	BOOK_Jdt("Jdt", false, 67, "Judit", "Jdt"),
	BOOK_Wis("Wis", false, 68, "Wisdom", "Wis"),
	BOOK_Tob("Tob", false, 69, "Tobit", "Tob"),
	BOOK_Sir("Sir", false, 70, "Sirach", "Sir"),
	BOOK_Bar("Bar", false, 71, "Baruch", "Bar"),
	BOOK_1Macc("1Macc", false, 72, "1 Maccabees", "1Ma"),
	BOOK_2Macc("2Macc", false, 73, "2 Maccabees", "2Ma"),
	BOOK_AddDan("AddDan", false, 74, "Additions to Daniel", "xDa"),
	BOOK_AddEsth("AddEsth", false, 75, "Additions to Esther", "xEs"),
	BOOK_PrMan("PrMan", false, 76, "Prayer of Manasseh", "Man"),
	BOOK_3Macc("3Macc", false, 77, "3 Maccabees", "3Ma"),
	BOOK_4Macc("4Macc", false, 78, "4 Maccabees", "4Ma"),
	BOOK_EpJer("EpJer", false, 79, "Letter of Jeremiah", "LJe"),
	BOOK_1Esd("1Esd", false, 80, "1 Esdras", "1Es"),
	BOOK_2Esd("2Esd", false, 81, "2 Esdras", "2Es"),
	BOOK_Odes("Odes", false, 82, "Odes", "Ode"),
	BOOK_PssSol("PssSol", false, 83, "Psalms of Solomon", "PsS"),
	BOOK_EpLao("EpLao", false, 84, "Epistle to the Laodiceans", "Lao"),
	BOOK_1En("1En", false, 85, "1 Enoch", "1En"),
	BOOK_kGen("x-kGen", false, 86, "kGen", "kGn"),
	BOOK_Sus("Sus", false, 87, "Susanna", "Sus"),
	BOOK_Bel("Bel", false, 88, "Bel and the Dragon", "Bel"),
	BOOK_AddPs("AddPs", false, 89, "Psalm 151", "Ps2"),

	BOOK_PrAzar("PrAzar", false, 901, "Prayer of Azariah", "Aza"),
	BOOK_EsthGr("EsthGr", false, 902, "Greek Esther", "EsG"),
	BOOK_DanGr("DanGr", false, 903, "Greek Daniel", "DaG"),
	BOOK_Jub("Jub", false, 904, "Jubilees", "Jub"),
	BOOK_4Ezra("4Ezra", false, 905, "Ezra Apocalpyse", "4Ez"),
	BOOK_5Ezra("5Ezra", false, 906, "5 Ezra", "5Ez"),
	BOOK_6Ezra("6Ezra", false, 907, "6 Ezra", "6Ez"),
	BOOK_5ApocSyrPss("5ApocSyrPss", false, 908, "5 Apocryphal Syriac Psalms", "Ps3"),
	BOOK_2Bar("2Bar", false, 909, "(Syriac) Apocalypse of Baruch", "2Ba"),
	BOOK_4Bar("4Bar", false, 910, "4 Baruch", "4Ba"),
	BOOK_EpBar("EpBar", false, 911, "Letter of Baruch", "LBa"),
	BOOK_1Meq("1Meq", false, 912, "1 Meqabyan", "1Mq"),
	BOOK_2Meq("2Meq", false, 913, "2 Meqabyan", "2Mq"),
	BOOK_3Meq("3Meq", false, 914, "3 Meqabyan", "3Mq"),
	BOOK_Rep("Rep", false, 915, "Reproof", "Rep"),

	APPENDIX("x-App", true, -6, "Appendix", "App"),
	APPENDIX_OTHER("x-App-Other", true, -7, "Appendix Other", "ApO"),
	APPENDIX_CONCORDANCE("x-App-Conc", true, -8, "Appendix Concordance", "ApC"),
	APPENDIX_GLOSSARY("x-App-Gloss", true, -9, "Appendix Glossary", "ApG"),
	APPENDIX_TOPICAL("x-App-Topical", true, -10, "Appendix Topical Index", "ApT"),
	APPENDIX_NAMES("x-App-Names", true, -10, "Appendix Names Index", "ApN"),

	DICTIONARY_ENTRY("x-Dict", true, -5, "Dictionary Entry", "Dct");

	private final String osisID, englishName, threeLetterCode;
	private final int zefID;
	private final boolean nt;

	private BookID(String osisID, boolean nt, int zefID, String englishName, String threeLetterCode) {
		this.osisID = osisID;
		this.nt = nt;
		this.zefID = zefID;
		this.englishName = englishName;
		this.threeLetterCode = Utils.validateString("threeLetterCode", threeLetterCode, "[A-Za-z0-9]{3}");
	}

	public String getOsisID() {
		return osisID;
	}

	public String getEnglishName() {
		return englishName;
	}

	public String getThreeLetterCode() {
		return threeLetterCode;
	}

	public int getZefID() {
		return zefID;
	}

	public boolean isNT() {
		return nt;
	}

	public boolean isDeuterocanonical() {
		return zefID > 66;
	}

	public static BookID fromZefId(int zefId) {
		for (BookID id : values()) {
			if (id.zefID == zefId)
				return id;
		}
		throw new IllegalArgumentException("Unsupported ZefID: " + zefId);
	}

	public static BookID fromOsisId(String osisId) {
		for (BookID id : values()) {
			if (id.osisID.equals(osisId))
				return id;
		}
		throw new IllegalArgumentException("Unsupported OSIS ID: " + osisId);
	}
}
