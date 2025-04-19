package biblemulticonverter.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import biblemulticonverter.data.FormattedText.ExtendedLineBreakKind;
import biblemulticonverter.data.FormattedText.FormattingInstructionKind;
import biblemulticonverter.data.FormattedText.LineBreakKind;
import biblemulticonverter.data.FormattedText.Visitor;
import biblemulticonverter.data.FormattedText.VisitorAdapter;

/**
 * Pseudo bible book that is used to store metadata about the bible. Metadata is
 * stored in the prolog of the first chapter. That way, exporters do not have to
 * support exporting of metadata.
 */
public class MetadataBook {

	private final Book book;
	private final Map<String, String> metadata = new LinkedHashMap<String, String>();

	public MetadataBook() {
		book = new Book("Metadata", BookID.METADATA, "Metadata", "Metadata");
		book.getChapters().add(new Chapter());
		book.getChapters().get(0).setProlog(new FormattedText());
	}

	protected MetadataBook(Book book) {
		this.book = book;
		if (book.getId() != BookID.METADATA || !book.getAbbr().equals("Metadata") || !book.getShortName().equals("Metadata") || !book.getLongName().equals("Metadata"))
			throw new IllegalArgumentException("Book is not the metadata book");
		if (book.getChapters().size() != 1)
			throw new IllegalArgumentException("Metadata book must have exactly one chapter");
		Chapter chapter = book.getChapters().get(0);
		if (chapter.getVerses().size() > 0)
			throw new IllegalArgumentException("Metadata book may not have verses");
		if (chapter.getProlog() == null) {
			throw new IllegalArgumentException("Metadata book must have prolog");
		}
		parseValues(chapter.getProlog());
	}

	public Book getBook() {
		return book;
	}

	public String getValue(MetadataBookKey key) {
		return metadata.get(key.toString());
	}

	public String getValue(String key) {
		validateKey(key);
		return metadata.get(key);
	}

	public List<String> getKeys() {
		return new ArrayList<String>(metadata.keySet());
	}

	public void setValue(MetadataBookKey key, String value) {
		setValue(key.toString(), value);
	}

	public void setValue(String key, String value) {
		validateKey(key);
		if (metadata.put(key, Utils.validateString("value", value, Utils.NORMALIZED_WHITESPACE_REGEX + "(\n" + Utils.NORMALIZED_WHITESPACE_REGEX + ")*")) == null) {
			appendValue(book.getChapters().get(0).getProlog(), key, value);
		} else {
			rebuildProlog();
		}
	}

	public void deleteKey(String key) {
		validateKey(key);
		metadata.remove(key);
		rebuildProlog();
	}

	public void finished() {
		book.getChapters().get(0).getProlog().finished();
	}

	public void validate(Map<String, Set<FormattedText.ValidationCategory>> validationCategories) {
		try {
			String mapContent = metadata.toString();
			metadata.clear();
			parseValues(book.getChapters().get(0).getProlog());
			String prologContent = metadata.toString();
			rebuildProlog();
			metadata.clear();
			parseValues(book.getChapters().get(0).getProlog());
			String rebuiltContent = metadata.toString();
			if (!mapContent.equals(prologContent) || !mapContent.equals(rebuiltContent))
				throw new IllegalStateException("MetadataBook cannot be rebuilt");
			finished();
		} catch (IllegalStateException ex) {
			FormattedText.ValidationCategory.PROLOG_VALIDATION_FAILED.throwOrRecord(book.getAbbr(), validationCategories, ex.getMessage());
		}
	}

	private void validateKey(String key) {
		if (key.matches("[A-Za-z0-9.-]+@[A-Za-z0-9.-]+"))
			return;
		if (!key.matches("[a-z]+"))
			throw new IllegalArgumentException("Invalid metadata key: " + key);
		MetadataBookKey.valueOf(key);
	}

	private void rebuildProlog() {
		FormattedText prolog = new FormattedText();
		for (Map.Entry<String, String> entry : metadata.entrySet()) {
			appendValue(prolog, entry.getKey(), entry.getValue());
		}
		book.getChapters().get(0).setProlog(prolog);
	}

	private void appendValue(FormattedText prolog, String key, String value) {
		Visitor<RuntimeException> visitor = prolog.getAppendVisitor();
		Visitor<RuntimeException> boldVisitor = visitor.visitFormattingInstruction(FormattingInstructionKind.BOLD);
		boldVisitor.visitText(key + ":");
		String restValue = "";
		if (value.contains("\n")) {
			restValue = value.substring(value.indexOf('\n') + 1);
			value = value.substring(0, value.indexOf('\n'));
		}
		visitor.visitText(" " + value);
		if (restValue.length() > 0) {
			for (String part : restValue.split("\n")) {
				visitor.visitLineBreak(ExtendedLineBreakKind.NEWLINE, 1);
				visitor.visitText(part);
			}
		}
		visitor.visitLineBreak(ExtendedLineBreakKind.PARAGRAPH, 0);
	}

	private void parseValues(FormattedText prolog) {
		final MetadataVisitor keyVisitor = new MetadataVisitor() {

			@Override
			public void visitText(String text) throws RuntimeException {
				advanceState(1, 2);
				keyText = text;
			}

			@Override
			public void visitLineBreak(ExtendedLineBreakKind kind, int indent) throws RuntimeException {
				throw new IllegalStateException();
			}

			@Override
			public Visitor<RuntimeException> visitFormattingInstruction(FormattingInstructionKind kind) throws RuntimeException {
				throw new IllegalStateException();
			}

		};
		prolog.accept(new MetadataVisitor() {

			private StringBuilder currentText = new StringBuilder();

			@Override
			public void visitText(String text) throws RuntimeException {
				if (keyVisitor.state == 2) {
					keyVisitor.advanceState(2, 3);
					if (!text.startsWith(" "))
						throw new IllegalArgumentException(text);
					text = text.substring(1);
				} else {
					keyVisitor.advanceState(4, 3);
				}
				currentText.append(text);
			}

			@Override
			public void visitLineBreak(ExtendedLineBreakKind kind, int indent) throws RuntimeException {
				if (kind == ExtendedLineBreakKind.NEWLINE && indent == 1) {
					keyVisitor.advanceState(3, 4);
					currentText.append('\n');
				} else if (kind == ExtendedLineBreakKind.PARAGRAPH && indent == 0) {
					keyVisitor.advanceState(3, 0);
					String key = keyVisitor.keyText;
					if (!key.endsWith(":"))
						throw new IllegalArgumentException(key);
					key = key.substring(0, key.length() - 1);
					validateKey(key);
					if (metadata.put(key, currentText.toString()) != null)
						throw new IllegalArgumentException("Duplicate key: " + key);
					currentText.setLength(0);
				} else {
					throw new IllegalArgumentException(kind + "@" + indent);
				}
			}

			@Override
			public Visitor<RuntimeException> visitFormattingInstruction(FormattingInstructionKind kind) throws RuntimeException {
				keyVisitor.advanceState(0, 1);
				return keyVisitor;
			}
		});
	}

	private static abstract class MetadataVisitor extends VisitorAdapter<RuntimeException> {
		private int state = 0;
		protected String keyText = null;

		public MetadataVisitor() {
			super(null);
		}

		protected void advanceState(int currentState, int newState) {
			if (state != currentState)
				throw new IllegalStateException();
			state = newState;
		}

		@Override
		protected void beforeVisit() {
			throw new IllegalStateException();
		}
	}

	public static enum MetadataBookKey {
		version, revision, status,
		source, identifier, type, publisher, date, coverage, format, creator,
		language, subject, contributors, description, title, rights, contributor
	}
}
