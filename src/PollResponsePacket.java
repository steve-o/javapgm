import java.io.*;
import java.net.*;
import java.util.*;

public class PollResponsePacket extends PgmPacket {

	private static final int POLR_SQN_OFFSET	= 0;
	private static final int POLR_ROUND_OFFSET	= 4;
	private static final int POLR_RESERVED_OFFSET	= 6;
	private static final int POLR_OPTIONS_OFFSET	= 8;

	private int _polr_offset = 0;

	public PollResponsePacket (SocketBuffer skb) {
		super (skb);
		this._polr_offset = this._offset + PGM_TYPE_DATA_OFFSET;
	}

	public static final PollResponsePacket decode (SocketBuffer skb) {
		return new PollResponsePacket (skb);
	}

	public final long getSequenceNumber() {
		return this.getUnsignedInt (this._polr_offset + POLR_SQN_OFFSET);
	}

	public final long getRound() {
		return this.getUnsignedShort (this._polr_offset + POLR_ROUND_OFFSET);
	}

	public final long getReserved() {
		return this.getUnsignedShort (this._polr_offset + POLR_RESERVED_OFFSET);
	}

	public String toString() {
		GlobalSourceId gsi = this.getGlobalSourceId ();
		return  "{" +
			"sourcePort:" + this.getSourcePort() +
		       " destinationPort:" + this.getDestinationPort() +
		       " type:" + this.getTypeName (this.getType()) +
		       " options:" + this.getOptions() +
		       " checksum:0x" + Integer.toHexString(this.getChecksum()) +
		       " gsi:" + gsi +
		       " tsduLength:" + this.getTsduLength() +
		       " polrSqn:" + this.getSequenceNumber() +
		       " polrRound:" + this.getRound() +
		       " polrReserved:" + this.getReserved() +
		        "}\n";
	}

}
