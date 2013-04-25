/* PGM packet header.
 */
import java.io.*;
import java.net.*;
import java.util.*;

public class Header {

	protected SocketBuffer		_skb = null;
	protected int			_offset = 0;

	public Header (SocketBuffer skb, int offset) {
		this._skb = skb;
		this._offset = offset;
	}

	public final int getChecksum() {
		return this._skb.getUnsignedShort (this._offset + Packet.PGM_CHECKSUM_OFFSET);
	}

	public final boolean hasChecksum() {
		return (0 != getChecksum());
	}

	public void clearChecksum() {
		this._skb.setUnsignedShort (this._offset + Packet.PGM_CHECKSUM_OFFSET, 0);
	}

	public final int getType() {
		return this._skb.getUnsignedByte (this._offset + Packet.PGM_TYPE_OFFSET);
	}

	public final String getTypeAsString() {
		final int type = getType();
		switch (type) {
		case Packet.PGM_SPM:	return "PGM_SPM";
		case Packet.PGM_POLL:	return "PGM_POLL";
		case Packet.PGM_POLR:	return "PGM_POLR";
		case Packet.PGM_ODATA:	return "PGM_ODATA";
		case Packet.PGM_RDATA:	return "PGM_RDATA";
		case Packet.PGM_NAK:	return "PGM_NAK";
		case Packet.PGM_NNAK:	return "PGM_NNAK";
		case Packet.PGM_NCF:	return "PGM_NCF";
		case Packet.PGM_SPMR:	return "PGM_SPMR";
		default:		return "(unknown)";
		}
	}

	public final boolean isUpstream() {
		final int type = getType();
		return (type == Packet.PGM_NAK ||		/* unicast */
			type == Packet.PGM_NNAK ||		/* unicast */
			type == Packet.PGM_SPMR ||		/* multicast + unicast */
			type == Packet.PGM_POLR);		/* unicast */
	}

	public final boolean isPeer() {
		return (getType() == Packet.PGM_SPMR);	/* multicast */
	}

	public final boolean isDownstream() {
		final int type = getType();
		return (type == Packet.PGM_SPM ||		/* all multicast */
			type == Packet.PGM_ODATA ||
			type == Packet.PGM_RDATA ||
			type == Packet.PGM_POLL ||
			type == Packet.PGM_NCF);
	}

	public final int getOptions() {
		return this._skb.getUnsignedByte (this._offset + Packet.PGM_OPTIONS_OFFSET);
	}

	public final boolean isParity() {
		return ((getOptions() & Packet.PGM_OPT_PARITY) == Packet.PGM_OPT_PARITY);
	}

	public final boolean isVariablePacketLength() {
		return ((getOptions() & Packet.PGM_OPT_VAR_PKTLEN) == Packet.PGM_OPT_VAR_PKTLEN);
	}

	public final boolean isNetworkSignificant() {
		return ((getOptions() & Packet.PGM_OPT_NETWORK) == Packet.PGM_OPT_NETWORK);
	}

	public final boolean isOptionExtensionPresent() {
		return ((getOptions() & Packet.PGM_OPT_PRESENT) == Packet.PGM_OPT_PRESENT);
	}

	public final boolean isOptionEncoded() {
		return ((getOptions() & Packet.PGM_OP_ENCODED) == Packet.PGM_OP_ENCODED);
	}

	public final GlobalSourceId getGlobalSourceId() {
		byte[] buf = new byte[GlobalSourceId.SIZE];
		System.arraycopy (this._skb.getRawBytes(), this._offset + Packet.PGM_GSI_OFFSET, buf, 0, GlobalSourceId.SIZE);
		return new GlobalSourceId (buf);
	}

	public final int getSourcePort() {
		return this._skb.getUnsignedShort (this._offset + Packet.PGM_SPORT_OFFSET);
	}

	public final int getDestinationPort() {
		return this._skb.getUnsignedShort (this._offset + Packet.PGM_DPORT_OFFSET);
	}

	public final TransportSessionId getTransportSessionId() {
		return new TransportSessionId (getGlobalSourceId(), getSourcePort());
	}

	public final int getTsduLength() {
		return this._skb.getUnsignedShort (this._offset + Packet.PGM_TSDU_LENGTH_OFFSET);
	}
}

/* eof */
