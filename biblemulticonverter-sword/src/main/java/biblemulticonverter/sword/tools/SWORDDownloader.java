package biblemulticonverter.sword.tools;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookFilters;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.book.install.InstallManager;
import org.crosswire.jsword.book.install.Installer;
import org.crosswire.jsword.book.install.sword.HttpSwordInstaller;
import org.crosswire.jsword.book.sword.SwordBookPath;

import biblemulticonverter.tools.Tool;

public class SWORDDownloader implements Tool {

	public static final String[] HELP_TEXT = {
			"Usage: SWORDDownloader (to list repositories)",
			"       SWORDDownloader <moduleDir> (to list installed modules)",
			"       SWORDDownloader <moduleDir> <repo> (to list installable modules)",
			"       SWORDDownloader <moduleDir> <repo> <module> [<module> [...]] (to install modules)",
			"",
			"Download or update SWORD modules from a remote HTTP repository. <repo> can be either the",
			"name of a known repository or a string consisting of hostname, package directory, and",
			"catalog directory, separated by pipes. The default repository would be",
			"\"www.crosswire.org|/ftpmirror/pub/sword/packages/rawzip|/ftpmirror/pub/sword/raw\".",
			"<module> can either be the name of a module, or a wildcard ending in a * or @ (for example,",
			"Ger* for all German modules, or * for all modules). Either one can optionally be suffixed",
			"by a ! (to download even if the same version is already present) or a + (to only update if",
			"already present). An additional ? will just print local and remote version numbers without",
			"installing anything."
	};

	@Override
	public void run(String... args) throws Exception {
		InstallManager imanager = new InstallManager();
		if (args.length == 0) {
			for (Map.Entry<String, Installer> mapEntry : imanager.getInstallers().entrySet()) {
				System.out.println(mapEntry.getKey().replace(' ', '_') + ": " + mapEntry.getValue().getInstallerDefinition());
			}
			return;
		}

		// initialize list of installed books (this may create some log
		// output...)
		System.out.println("Loading locally installed books...");
		SwordBookPath.setDownloadDir(new File(args[0]));
		Books installedBooks = Books.installed();
		System.out.println("======");

		if (args.length == 1) {
			for (Book bk : installedBooks.getBooks(BookFilters.getOnlyBibles())) {
				System.out.println(bk.getInitials() + " (" + bk.getName() + "): " + bk.getProperty("Version"));
			}
			return;
		}

		Installer installer;
		if (args[1].contains("|")) {
			String[] parts = args[1].split("\\|");
			if (parts.length != 3)
				throw new IOException("Invalid repository: " + args[1]);
			HttpSwordInstaller httpInstaller = new HttpSwordInstaller();
			httpInstaller.setHost(parts[0]);
			httpInstaller.setPackageDirectory(parts[1]);
			httpInstaller.setCatalogDirectory(parts[2]);
			installer = httpInstaller;
		} else {
			installer = imanager.getInstaller(args[1].replace('_', ' '));
			if (installer == null)
				throw new IOException("Unknown repository: " + args[1]);
		}

		System.out.println("Loading remote book list...");
		installer.reloadBookList();
		List<Book> availableBooks = installer.getBooks(BookFilters.getOnlyBibles());
		System.out.println("======");

		if (args.length == 2) {
			args = new String[] { args[0], args[1], "*?" };
		}

		for (int i = 2; i < args.length; i++) {
			boolean force = false, updateOnly = false, printOnly = false;
			String name = args[i].replace('@', '*');
			if (name.endsWith("?")) {
				printOnly = true;
				name = name.substring(0, name.length() - 1);
			}
			if (name.endsWith("!")) {
				force = true;
				name = name.substring(0, name.length() - 1);
			} else if (name.endsWith("+")) {
				updateOnly = true;
				name = name.substring(0, name.length() - 1);
			}
			for (Book bk : availableBooks) {
				if (bk.getInitials().equalsIgnoreCase(name) || (name.endsWith("*") && bk.getInitials().toLowerCase().startsWith(name.substring(0, name.length() - 1).toLowerCase()))) {
					Book installedBook = installedBooks.getBook(bk.getInitials());
					if (installedBook == null) {
						if (updateOnly)
							continue;
					} else if (installedBook != null && installedBook.getProperty("Version").equals(bk.getProperty("Version"))) {
						if (!force)
							continue;
					}
					if (printOnly) {
						System.out.print(bk.getInitials() + " (" + bk.getName() + "): ");
						if (installedBook == null) {
							System.out.println("NEW -> " + bk.getProperty("Version"));
						} else if (installedBook.getProperty("Version").equals(bk.getProperty("Version"))) {
							System.out.println(installedBook.getProperty("Version"));
						} else {
							System.out.println(installedBook.getProperty("Version") + " -> " + bk.getProperty("Version"));
						}
					} else {
						System.out.println("Installing " + bk.getInitials() + " (" + bk.getName() + ")...");
						try {
							installer.install(bk);
						} catch (ArithmeticException ex) {
							// happens for some IBT modules...
							System.err.println(ex.toString());
						}
					}
				}
			}
		}
	}
}
