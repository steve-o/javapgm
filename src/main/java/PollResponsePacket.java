/* Poll-response or POLR packet.
 */
import java.io.*;
import java.net.*;
import java.util.*;

public class PollResponsePacket {

	protected SocketBuffer	_skb = null;
	protected int		_offset = 0;

	private static final int POLR_SQN_OFFSET	= 0;
	private static final int POLR_ROUND_OFFSET	= 4;
	private static final int POLR_RESERVED_OFFSET	= 6;
	private static final int POLR_OPTIONS_OFFSET	= 8;

	public PollResponsePacket (SocketBuffer skb, int offset) {
		this._skb = skb;
		this._offset = offset;
	}

	public final long getSequenceNumber() {
		return this._skb.getUnsignedInt (this._offset + POLR_SQN_OFFSET);
	}

	public final long getRound() {
		return this._skb.getUnsignedShort (this._offset + POLR_ROUND_OFFSET);
	}

	public final long getReserved() {
		return this._skb.getUnsignedShort (this._offset + POLR_RESERVED_OFFSET);
	}

	public String toString() {
		Header header = this._skb.getHeader();
		return  "{" +
			 "\"sourcePort\": " + header.getSourcePort() +
		       ", \"destinationPort\": " + header.getDestinationPort() +
		       ", \"type\": \"" + header.getTypeAsString() + "\"" +
		       ", \"options\": " + header.getOptions() +
		       ", \"checksum\": 0x" + Integer.toHexString (header.getChecksum()) +
		       ", \"gsi\": \"" + header.getGlobalSourceId() + "\"" +
		       ", \"tsduLength\": " + header.getTsduLength() +
		       ", \"polrSqn\": " + this.getSequenceNumber() +
		       ", \"polrRound\": " + this.getRound() +
		       ", \"polrReserved\": " + this.getReserved() +
		        "}\n";
	}
}

/* eof */
