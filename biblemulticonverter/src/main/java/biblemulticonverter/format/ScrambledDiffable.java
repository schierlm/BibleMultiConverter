package biblemulticonverter.format;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import biblemulticonverter.data.Bible;
import biblemulticonverter.data.Book;
import biblemulticonverter.data.BookID;
import biblemulticonverter.data.Chapter;
import biblemulticonverter.data.FormattedText;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.Verse;

public class ScrambledDiffable implements ExportFormat {

	public static final String[] HELP_TEXT = {
			"Like Diffable, but with scrambled text; for tests with non-free bibles.",
			"",
			"Usage: ScrambledDiffable <OutputFile> [+<Password>|-<Password>|=<Const>|#<Hash>]",
			"",
			"Use this export format if you want to share a Bible text that triggers",
			"a bug, but are unsure whether the license of the text allows it. This way",
			"you can create a Bible text that uses the same structure, but the actual",
			"words are scrambled beyond repair (or repairable with a password if you prefer).",
			"",
			"Try using '=23' as argument first, which should replace all letters by 'X',",
			"resulting in a well compressible file. In case that one does not reproduce",
			"the bug, use without arguments (random numbers).",
			"In case you want to share a Bible where others are able to compare if their",
			"verses are the same, use '#SHA-1' as argument; that way, the same verse will",
			"scramble to the same 'ciphertext', so the resulting files are still diffable",
			"although unreadable. In case you have to be able to reverse the scrambling",
			"(if the whole file is unchanged), you can use '+Password' for initial scrambling",
			"and '-Password' for later decrypting. To verify if two bible were scrambled",
			"from the same source (using different parameters), scramble them again in",
			"const mode, and diff the results.",
			"Note that since 'encrypting' uses a stream cipher, if you use the same password",
			"for more than one file, an attacker that knows only this piece of information",
			"can use it for correlation attacks to get the plain text. Therefore, use different",
			"passwords for multiple bibles (like, add the bible name to them)."
	};

	@Override
	public void doExport(Bible bible, String... exportArgs) throws Exception {
		String outputFile = exportArgs[0];
		Scrambler scrambler = new Scrambler(exportArgs.length == 1 ? null : exportArgs[1]);

		for (Book book : bible.getBooks()) {
			if (book.getId() == BookID.METADATA)
				continue;
			for (Chapter chapter : book.getChapters()) {
				if (chapter.getProlog() != null) {
					FormattedText newProlog = new FormattedText();
					scrambleText(scrambler, chapter.getProlog(), newProlog);
					chapter.setProlog(newProlog);
				}
				List<Verse> verses = chapter.getVerses();
				for (int i = 0; i < verses.size(); i++) {
					Verse oldVerse = verses.get(i);
					Verse newVerse = new Verse(oldVerse.getNumber());
					scrambleText(scrambler, oldVerse, newVerse);
					verses.set(i, newVerse);
				}
			}
		}

		new Diffable().doExport(bible, new String[] { outputFile });
	}

	private void scrambleText(Scrambler scrambler, FormattedText oldText, FormattedText newText) throws GeneralSecurityException {
		MessageDigest digest = scrambler.getDigest();
		if (digest != null) {
			oldText.accept(new DigestVisitor(digest));
			scrambler.updateFromDigest();
		}
		oldText.accept(new ScrambleVisitor(newText.getAppendVisitor(), scrambler));
		newText.finished();
	}

	private static class Scrambler {

		private final SecureRandom rnd;
		private final int offset;
		private Cipher cipher;
		private final MessageDigest digest;

		public Scrambler(String mode) throws GeneralSecurityException {
			if (mode == null) {
				rnd = new SecureRandom();
				offset = 0;
				cipher = null;
				digest = null;
			} else if (mode.startsWith("+")) {
				rnd = new SecureRandom();
				offset = 1;
				cipher = initCipher(mode.substring(1).getBytes(StandardCharsets.UTF_8));
				digest = null;
			} else if (mode.startsWith("-")) {
				rnd = new SecureRandom();
				offset = -1;
				cipher = initCipher(mode.substring(1).getBytes(StandardCharsets.UTF_8));
				digest = null;
			} else if (mode.startsWith("=")) {
				rnd = null;
				offset = Integer.parseInt(mode.substring(1));
				cipher = null;
				digest = null;
			} else if (mode.startsWith("#")) {
				rnd = new SecureRandom();
				offset = 1;
				cipher = null;
				digest = MessageDigest.getInstance(mode.substring(1));
			} else {
				System.out.println("WARNING: Invalid scramble mode; using random numbers");
				rnd = new SecureRandom();
				offset = 0;
				cipher = null;
				digest = null;
			}
		}

		public MessageDigest getDigest() {
			if (digest != null)
				digest.reset();
			return digest;
		}

		public void updateFromDigest() throws GeneralSecurityException {
			cipher = initCipher(digest.digest());
			digest.reset();
		}

		private Cipher initCipher(byte[] keyData) throws GeneralSecurityException {
			MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
			sha256.reset();
			byte[] digest = sha256.digest(keyData);
			Cipher result = Cipher.getInstance("AES/CFB8/NoPadding");
			result.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(digest, 0, 16, "AES"), new IvParameterSpec(digest, 16, 16), rnd);
			return result;
		}

		public String scrambleText(String text) {
			char[] data = text.toCharArray();
			for (int i = 0; i < data.length; i++) {
				char ch = data[i];
				if (ch >= 'A' && ch <= 'Z') {
					ch = (char) ('A' + scrambleChar(ch - 'A', 26));
				} else if (ch >= 'a' && ch <= 'z') {
					ch = (char) ('a' + scrambleChar(ch - 'a', 26));
				} else if (ch >= '0' && ch <= '9') {
					ch = (char) ('0' + scrambleChar(ch - '0', 10));
				} else if (ch >= '\u0391' && ch <= '\u03A9') {
					if (ch > '\u03A1')
						ch--; // no uppercase final sigma!
					ch = (char) ('\u0391' + scrambleChar(ch - '\u0391', 24));
					if (ch > '\u03A1')
						ch++;
				} else if (ch >= '\u03B1' && ch <= '\u03C9') {
					ch = (char) ('\u03B1' + scrambleChar(ch - '\u03B1', 25));
				}
				data[i] = ch;
			}
			return new String(data);
		}

		public int scrambleChar(int value, int modulus) {
			if (value < 0 || value >= modulus)
				throw new IllegalArgumentException();
			if (cipher != null) {
				byte[] data = cipher.update(new byte[1]);
				if (data.length != 1)
					throw new IllegalStateException("Cipher is not a stream cipher");
				value += offset * data[0];
			} else if (rnd != null) {
				value = rnd.nextInt(modulus);
			} else {
				value = offset;
			}
			return ((value % modulus) + modulus) % modulus;
		}
	}

	private static class DigestVisitor extends FormattedText.VisitorAdapter<RuntimeException> {

		private MessageDigest digest;

		private DigestVisitor(MessageDigest digest) throws RuntimeException {
			super(null);
			this.digest = digest;
		}

		@Override
		public void visitText(String text) throws RuntimeException {
			digest.update(text.getBytes(StandardCharsets.UTF_8));
		}

		@Override
		protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) throws RuntimeException {
			return this;
		}
	}

	private static class ScrambleVisitor extends FormattedText.VisitorAdapter<RuntimeException> {
		private final Scrambler scrambler;

		private ScrambleVisitor(Visitor<RuntimeException> next, Scrambler scrambler) throws RuntimeException {
			super(next);
			this.scrambler = scrambler;
		}

		@Override
		public void visitText(String text) throws RuntimeException {
			super.visitText(scrambler.scrambleText(text));
		}

		@Override
		protected Visitor<RuntimeException> wrapChildVisitor(Visitor<RuntimeException> childVisitor) throws RuntimeException {
			return new ScrambleVisitor(childVisitor, scrambler);
		}
	}
}
