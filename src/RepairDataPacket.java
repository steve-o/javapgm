/* Repair-data or RDATA packet.  Used for re-transmits of payload data.
 */
import java.io.*;
import java.net.*;
import java.util.*;

public class RepairDataPacket extends PgmPacket {

	private static final int RDATA_SQN_OFFSET	= 0;
	private static final int RDATA_TRAIL_OFFSET	= 4;
	private static final int RDATA_OPTIONS_OFFSET	= 8;

	private int _header_length = 0;
	private int _rdata_offset = 0;

	public RepairDataPacket (SocketBuffer skb) {
		super (skb);
		this._rdata_offset = this._offset + PGM_TYPE_DATA_OFFSET;
		this._header_length = this._rdata_offset + RDATA_OPTIONS_OFFSET - this._offset;
	}

	public static final RepairDataPacket decode (SocketBuffer skb) {
		return new RepairDataPacket (skb);
	}

	public final long getSequenceNumber() {
		return this.getUnsignedInt (this._rdata_offset + RDATA_SQN_OFFSET);
	}

	public final long getTrail() {
		return this.getUnsignedInt (this._rdata_offset + RDATA_TRAIL_OFFSET);
	}

	public String toString() {
		GlobalSourceId gsi = this.getGlobalSourceId ();
		return  "{" +
			 "\"sourcePort\": " + this.getSourcePort() +
		       ", \"destinationPort\": " + this.getDestinationPort() +
		       ", \"type\": " + this.getTypeName (this.getType()) +
		       ", \"options\": " + this.getOptions() +
		       ", \"checksum\": 0x" + Integer.toHexString (this.getChecksum()) +
		       ", \"gsi\": \"" + gsi + "\"" +
		       ", \"tsduLength\": " + this.getTsduLength() +
		       ", \"rdataSqn\": " + this.getSequenceNumber() +
		       ", \"rdataTrail\": " + this.getTrail() +
		        "}\n";
	}
}

/* eof */
