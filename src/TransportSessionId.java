
public class TransportSessionId
{
	private GlobalSourceId gsi = null;
	private int sourcePort = 0;

	public TransportSessionId (GlobalSourceId gsi, int sourcePort) {
		this.setGlobalSourceId (gsi);
		this.setSourcePort (sourcePort);
	}

	public TransportSessionId (TransportSessionId tsi) {
		if (null == tsi)
			throw new NullPointerException ("tsi may not be null");

		this.setGlobalSourceId (tsi.getGlobalSourceId());
		this.setSourcePort (tsi.getSourcePort());
	}

	public int hashCode() {
		return this.toString().hashCode();
	}

	public boolean equals (Object object) {
		if (null != object && (object instanceof TransportSessionId))
		{
/* using this.gsi == other.gsi does not call this.gsi.equals(other.gsi) */
			if (this.getGlobalSourceId().equals (((TransportSessionId)object).getGlobalSourceId())
			    && this.getSourcePort() == ((TransportSessionId)object).getSourcePort())
			{
				return true;
			}
		}
		return false;
	}

	public GlobalSourceId getGlobalSourceId() {
		return gsi;
	}

	public int getSourcePort() {
		return sourcePort;
	}

	public final byte[] getAsBytes() {
		byte[] bytes = new byte[8];
		byte[] gsiBytes = gsi.getAsBytes();
		bytes[0] = gsiBytes[0];
		bytes[1] = gsiBytes[1];
		bytes[2] = gsiBytes[2];
		bytes[3] = gsiBytes[3];
		bytes[4] = gsiBytes[4];
		bytes[5] = gsiBytes[5];
		bytes[6] = (byte) ((sourcePort >>> 8) & 0xff);
		bytes[7] = (byte) (sourcePort & 0xff);
		return bytes;
	}

	private final void setGlobalSourceId (GlobalSourceId gsi) {
		this.gsi = gsi;
	}

	private final void setSourcePort (int sourcePort) {
		if (sourcePort < 0 || sourcePort > 65535)
			throw new IllegalArgumentException ("Invalid port: " + sourcePort);

		this.sourcePort = sourcePort;
	}

	public String toString() {
		return gsi.toString() + '.' + String.valueOf (sourcePort);
	}
}

/* eof */
