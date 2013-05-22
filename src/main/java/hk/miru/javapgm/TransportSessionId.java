/* Unique identifier for each PGM transport session.
 */
package hk.miru.javapgm;

import static hk.miru.javapgm.Preconditions.checkArgument;
import static hk.miru.javapgm.Preconditions.checkNotNull;

import javax.annotation.Nullable;

public class TransportSessionId
{
	private GlobalSourceId gsi = null;
	private int sourcePort = 0;

	public TransportSessionId (@Nullable GlobalSourceId gsi, int sourcePort) {
                this.gsi = gsi;
                this.sourcePort = sourcePort;
	}

	public TransportSessionId (TransportSessionId tsi) {
                this (tsi.getGlobalSourceId(), tsi.getSourcePort());
	}

        @Override
	public int hashCode() {
		return toString().hashCode();
	}

        @Override
	public boolean equals (@Nullable Object object) {
		if (null != object && (object instanceof TransportSessionId))
		{
/* using this.gsi == other.gsi does not call this.gsi.equals(other.gsi) */
			if (this.gsi.equals (((TransportSessionId)object).getGlobalSourceId())
			    && this.sourcePort== ((TransportSessionId)object).getSourcePort())
			{
				return true;
			}
		}
		return false;
	}

	public GlobalSourceId getGlobalSourceId() {
		return this.gsi;
	}

	public int getSourcePort() {
		return this.sourcePort;
	}

        @SuppressWarnings("PointlessBitwiseExpression")
	public final byte[] getAsBytes() {
		byte[] bytes = new byte[8];
		byte[] gsiBytes = this.gsi.getBytes();
		bytes[0] = gsiBytes[0];
		bytes[1] = gsiBytes[1];
		bytes[2] = gsiBytes[2];
		bytes[3] = gsiBytes[3];
		bytes[4] = gsiBytes[4];
		bytes[5] = gsiBytes[5];
		bytes[6] = (byte) ((this.sourcePort >> 8) & 0xff);
		bytes[7] = (byte) ((this.sourcePort >> 0) & 0xff);
		return bytes;
	}

	private void setGlobalSourceId (GlobalSourceId gsi) {
                checkNotNull (gsi);
		this.gsi = gsi;
	}

	private void setSourcePort (int sourcePort) {
                checkArgument (sourcePort >= 0 && sourcePort <= 65535);
		this.sourcePort = sourcePort;
	}

        @Override
	public String toString() {
		return this.gsi.toString() + '.' + String.valueOf (this.sourcePort);
	}
}

/* eof */