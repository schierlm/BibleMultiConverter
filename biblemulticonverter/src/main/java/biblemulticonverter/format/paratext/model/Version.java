package biblemulticonverter.format.paratext.model;

public enum Version {
	V1(1, 0, 0),
	V2(2, 0, 0),
	V2_0_3(2, 0, 3),
	V2_2(2, 2, 0),
	V3(3, 0, 0);

	private final int major;
	private final int minor;
	private final int patch;

	Version(int major, int minor, int patch) {
		this.major = major;
		this.minor = minor;
		this.patch = patch;
	}

	public boolean isLowerOrEqualTo(Version other) {
		if (major < other.major) {
			return true;
		} else if (major == other.major) {
			if (minor < other.minor) {
				return true;
			} else if (minor == other.minor) {
				return patch <= other.patch;
			}
		}
		return false;
	}
}