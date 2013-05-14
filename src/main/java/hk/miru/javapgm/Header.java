/* PGM packet header.
 */
package hk.miru.javapgm;

public class Header {

	protected SocketBuffer	_skb = null;
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

	public void setChecksum (int csum) {
		this._skb.setUnsignedShort (this._offset + Packet.PGM_CHECKSUM_OFFSET, csum);
	}

	public void clearChecksum() {
		setChecksum (0);
	}

	public final int getType() {
		return this._skb.getUnsignedByte (this._offset + Packet.PGM_TYPE_OFFSET);
	}

	public void setType (int pgm_type) {
		this._skb.setUnsignedByte (this._offset + Packet.PGM_TYPE_OFFSET, pgm_type);
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

	public void setOptions (int options) {
		this._skb.setUnsignedByte (this._offset + Packet.PGM_OPTIONS_OFFSET, options);
	}

	public final GlobalSourceId getGlobalSourceId() {
		byte[] buf = new byte[GlobalSourceId.SIZE];
		System.arraycopy (this._skb.getRawBytes(), this._offset + Packet.PGM_GSI_OFFSET, buf, 0, GlobalSourceId.SIZE);
		return new GlobalSourceId (buf);
	}

	public void setGlobalSourceId (GlobalSourceId gsi) {
		System.arraycopy (gsi.getAsBytes(), 0, this._skb.getRawBytes(), this._offset + Packet.PGM_GSI_OFFSET, GlobalSourceId.SIZE);
	}

	public final int getSourcePort() {
		return this._skb.getUnsignedShort (this._offset + Packet.PGM_SPORT_OFFSET);
	}

	public void setSourcePort (int sport) {
		this._skb.setUnsignedShort (this._offset + Packet.PGM_SPORT_OFFSET, sport);
	}

	public final int getDestinationPort() {
		return this._skb.getUnsignedShort (this._offset + Packet.PGM_DPORT_OFFSET);
	}

	public void setDestinationPort (int dport) {
		this._skb.setUnsignedShort (this._offset + Packet.PGM_DPORT_OFFSET, dport);
	}

	public final TransportSessionId getTransportSessionId() {
		return new TransportSessionId (getGlobalSourceId(), getSourcePort());
	}

	public final int getTsduLength() {
		return this._skb.getUnsignedShort (this._offset + Packet.PGM_TSDU_LENGTH_OFFSET);
	}

        @Override
	public String toString() {
		StringBuilder options = new StringBuilder();
		if (isParity())
			options.append ("OPT_PARITY");
		if (isVariablePacketLength()) {
			if (options.length() > 0) options.append ("| ");
			options.append ("OPT_VAR_PKTLEN");
		}
		if (isNetworkSignificant()) {
			if (options.length() > 0) options.append ("| ");
			options.append ("OPT_NETWORK");
		}
		if (isOptionExtensionPresent()) {
			if (options.length() > 0) options.append ("| ");
			options.append ("OPT_PRESENT");
		}
		if (isOptionEncoded()) {
			if (options.length() > 0) options.append ("| ");
			options.append ("OP_ENCODED");
		}
		return  " { " +
			  "\"pgm_sport\": " + getSourcePort() +
			", \"pgm_dport\": " + getDestinationPort() +
			", \"pgm_type\": \"" + getTypeAsString() + "\"" +
			", \"pgm_options\": \"" + options + "\"" +
			", \"pgm_checksum\": " + getChecksum() +
			", \"pgm_gsi\": \"" + getGlobalSourceId() + "\"" +
			", \"pgm_tsdu_length\": " + getTsduLength() +
			" }";
	}
}

/* eof */