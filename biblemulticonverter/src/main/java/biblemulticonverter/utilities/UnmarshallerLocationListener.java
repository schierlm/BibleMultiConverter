package biblemulticonverter.utilities;

import javax.xml.bind.Unmarshaller;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamReader;

import java.util.HashMap;
import java.util.Map;

/**
 * An {@link Unmarshaller.Listener} that records the locations (line and column numbers, see {@link Location}) of the
 * objects that are being read by the given {@link XMLStreamReader}. These locations can later be accessed using the
 * {@link #getLocation(Object)} method.
 */
public class UnmarshallerLocationListener extends Unmarshaller.Listener {

	private XMLStreamReader xsr;
	private String filename;
	private Map<Object, Location> locations = new HashMap<>();

	public void setXMLStreamReader(String filename, XMLStreamReader xsr) {
		locations.clear();
		this.xsr = xsr;
		this.filename = filename;
	}

	@Override
	public void beforeUnmarshal(Object target, Object parent) {
		locations.put(target, xsr.getLocation());
	}

	public Location getLocation(Object o) {
		return locations.get(o);
	}

	public String getHumanReadableLocation(Object o) {
		Location location = getLocation(o);
		if (location == null) {
			return filename;
		} else {
			return filename + " line " + location.getLineNumber() + ", column " + location.getColumnNumber();
		}
	}
}
