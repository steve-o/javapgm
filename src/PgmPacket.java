import java.io.*;
import java.net.*;
import java.util.*;

public class PgmPacket {

	protected static final int AFI_IP		= 1;
	protected static final int AFI_IP6		= 2;

	protected static final int SIZEOF_INADDR	= 4;
	protected static final int SIZEOF_INADDR6	= 16;
	protected static final int SIZEOF_PGM_HEADER	= 16;

	public static final int PGM_SPM			= 0x00;
	public static final int PGM_POLL		= 0x01;
	public static final int PGM_POLR		= 0x02;
	public static final int PGM_ODATA		= 0x04;
	public static final int PGM_RDATA		= 0x05;
	public static final int PGM_NAK			= 0x08;
	public static final int PGM_NNAK		= 0x09;
	public static final int PGM_NCF			= 0x0a;
	public static final int PGM_SPMR		= 0x0c;
	public static final int PGM_MAX			= 0xff;

	public static final int PGM_OPT_PARITY		= 0x80;
	public static final int PGM_OPT_VAR_PKTLEN	= 0x40;
	public static final int PGM_OPT_NETWORK		= 0x02;
	public static final int PGM_OPT_PRESENT		= 0x01;

	private static final int PGM_SPORT_OFFSET	= 0;
	private static final int PGM_DPORT_OFFSET	= 2;
	private static final int PGM_TYPE_OFFSET	= 4;
	private static final int PGM_OPTIONS_OFFSET	= 5;
	private static final int PGM_CHECKSUM_OFFSET	= 6;
	private static final int PGM_GSI_OFFSET		= 8;
	private static final int PGM_TSDU_LENGTH_OFFSET	= 14;
	protected static final int PGM_TYPE_DATA_OFFSET	= SIZEOF_PGM_HEADER;

	protected byte[]	_buf	= null;
	protected int		_offset	= 0;
	protected int		_length	= 0;
	protected long		timestamp = 0;

	public static PgmPacket decode (SocketBuffer skb) {
		int pgm_type = skb.getData()[PGM_TYPE_OFFSET] & 0xff;
		switch (pgm_type){
		case PGM_SPM:
			return SourcePathMessagePacket.decode (skb);
		case PGM_POLL:
			return PollPacket.decode (skb);
		case PGM_POLR:
			return PollResponsePacket.decode (skb);
		case PGM_ODATA:
			return OriginalDataPacket.decode (skb);
		case PGM_RDATA:
			return RepairDataPacket.decode (skb);
		case PGM_NAK:
			return NakPacket.decode (skb);
		case PGM_NNAK:
			return NullNakPacket.decode (skb);
		case PGM_NCF:
			return NakConfirmPacket.decode (skb);
		default:
			return new PgmPacket (skb);
		}
	}

	public PgmPacket (SocketBuffer skb) {
		this._buf = skb.getData();
		this._offset = skb.getOffset();
		this._length = this._offset + skb.getLength();
		this.timestamp = skb.getTimestamp();
	}

	public boolean isValid() {
		if (this._length < SIZEOF_PGM_HEADER) {
			System.out.println ("UDP payload too small for PGM packet at " + this._length + " bytes, expecting at least " + SIZEOF_PGM_HEADER + " bytes.");
			return false;
		}
		return true;
	}

/* PGM payload, header looks as follows:
 *
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |         Source Port           |       Destination Port        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |      Type     |    Options    |           Checksum            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                        Global Source ID                   ... |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * | ...    Global Source ID       |           TSDU Length         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * | Type specific data ...
 * +-+-+-+-+-+-+-+-+-+- ...
 */

	public final int getSourcePort() {
		return this.getUnsignedShort (PGM_SPORT_OFFSET);
	}

	public final void setSourcePort (int port) {
		this.setUnsignedShort (PGM_SPORT_OFFSET, port);
	}

	public final int getDestinationPort() {
		return this.getUnsignedShort (PGM_DPORT_OFFSET);
	}

	public final void setDestinationPort (int port) {
		this.setUnsignedShort (PGM_DPORT_OFFSET, port);
	}

	public final int getType() {
		return this.getUnsignedByte (PGM_TYPE_OFFSET);
	}

	public final boolean isUpstream() {
		final int type = this.getType();
		return (type == PGM_NAK ||		/* unicast */
			type == PGM_NNAK ||		/* unicast */
			type == PGM_SPMR ||		/* multicast + unicast */
			type == PGM_POLR);		/* unicast */
	}

	public final boolean isPeer() {
		return (this.getType() == PGM_SPMR);	/* multicast */
	}

	public final boolean isDownstream() {
		final int type = this.getType();
		return (type == PGM_SPM ||		/* all multicast */
			type == PGM_ODATA ||
			type == PGM_RDATA ||
			type == PGM_POLL ||
			type == PGM_NCF);
	}

	public final void setType (int type) {
		this.setUnsignedByte (PGM_TYPE_OFFSET, type);
	}

	public static final String getTypeName (int type) {
		switch (type) {
		case PGM_SPM:		return "PGM_SPM";
		case PGM_POLL:		return "PGM_POLL";
		case PGM_POLR:		return "PGM_POLR";
		case PGM_ODATA:		return "PGM_ODATA";
		case PGM_RDATA:		return "PGM_RDATA";
		case PGM_NAK:		return "PGM_NAK";
		case PGM_NNAK:		return "PGM_NNAK";
		case PGM_NCF:		return "PGM_NCF";
		case PGM_SPMR:		return "PGM_SPMR";
		default:		return "INVALID";
		}
	}

	public final int getOptions() {
		return this.getUnsignedByte (PGM_OPTIONS_OFFSET);
	}

	public final void setOptions (int pgm_options) {
		this.setUnsignedByte (PGM_OPTIONS_OFFSET, pgm_options);
	}

	public final boolean isParity() {
		return (this.getOptions() & PGM_OPT_PARITY) > 0;
	}

	public final int getChecksum() {
		return this.getUnsignedShort (PGM_CHECKSUM_OFFSET);
	}

	public final void setChecksum (int checksum) {
		this.setUnsignedShort (PGM_CHECKSUM_OFFSET, checksum);
	}

	public void updateChecksum () {
		this.setChecksum (0);
		this.setChecksum (PgmChecksum.fold (PgmChecksum.partial (this._buf, this._offset, this._length, 0)));
	}

	public final GlobalSourceId getGlobalSourceId () {
		byte[] buf = new byte[ GlobalSourceId.SIZE ];
		System.arraycopy (this._buf, PGM_GSI_OFFSET, buf, 0, GlobalSourceId.SIZE);
		return new GlobalSourceId (buf);
	}

	public final void setGlobalSourceId (GlobalSourceId gsi) {
		System.arraycopy (gsi.getAsBytes(), 0, this._buf, PGM_GSI_OFFSET, GlobalSourceId.SIZE);
	}

	public final TransportSessionId getTransportSessionId () {
		return new TransportSessionId (this.getGlobalSourceId(), this.getSourcePort());
	}

	public final int getTsduLength() {
		return this.getUnsignedShort (PGM_TSDU_LENGTH_OFFSET);
	}

	public final void setTsduLength (int tsdu_length) {
		this.setUnsignedShort (PGM_TSDU_LENGTH_OFFSET, tsdu_length);
	}

	public final long getTimestamp() {
		return this.timestamp;
	}

	public String toString() {
		GlobalSourceId gsi = this.getGlobalSourceId ();
		return	"{ " +
			"\"sourcePort\": " + this.getSourcePort() + "" +
		       " \"destinationPort\": " + this.getDestinationPort() + "" +
		       " \"type\": \"" + this.getType() + "\"" +
		       " \"options\": " + this.getOptions() + "" +
		       " \"checksum\": 0x" + Integer.toHexString(this.getChecksum()) + "" +
		       " \"gsi\": \"" + gsi + "\"" +
		       " \"tsduLength\": " + this.getTsduLength() + "" +
			" }";
	}

	public final byte getByte (int offset) {
		return this._buf[offset];
	}

	public final void setByte (int offset, byte value) {
		this._buf[offset] = value;
	}

	public final int getUnsignedByte (int offset) {
		return this._buf[offset] & 0xff;
	}

	public final void setUnsignedByte (int offset, int value) {
		this._buf[offset] = (byte)(value & 0xff);
	}

	public final short getShort (int offset) {
		return (short)((this._buf[offset + 0] << 8) +			/* keep sign-bit */
		       ((this._buf[offset + 1] & 0xff) << 0));
	}

	public final void setShort (int offset, short value) {
		this._buf[offset + 0] = (byte) (value >> 8);
		this._buf[offset + 1] = (byte)((value >> 0) & 0xff);
	}

	public final int getUnsignedShort (int offset) {
		return ((this._buf[offset + 0] & 0xff) << 8) +
		       ((this._buf[offset + 1] & 0xff) << 0);
	}

	public final void setUnsignedShort (int offset, int value) {
		this._buf[offset + 0] = (byte)((value >> 8) & 0xff);
		this._buf[offset + 1] = (byte)((value >> 0) & 0xff);
	}

	public final int getInt (int offset) {
		return (this._buf[offset + 0] << 24) +			/* keep sign-bit */
		       ((this._buf[offset + 1] & 0xff) << 16) +
		       ((this._buf[offset + 2] & 0xff) <<  8) +
		       ((this._buf[offset + 3] & 0xff) <<  0);
	}

	public final void setInt (int offset, int value) {
		this._buf[offset + 0] = (byte) (value >> 24);
		this._buf[offset + 1] = (byte)((value >> 16) & 0xff);
		this._buf[offset + 2] = (byte)((value >>  8) & 0xff);
		this._buf[offset + 3] = (byte)((value >>  0) & 0xff);
	}

	public final long getUnsignedInt (int offset) {
		return ((long)(this._buf[offset + 0] & 0xff) << 24) +
		       ((this._buf[offset + 1] & 0xff) << 16) +
		       ((this._buf[offset + 2] & 0xff) <<  8) +
		       ((this._buf[offset + 3] & 0xff) <<  0);
	}

	public final void setUnsignedInt (int offset, long value) {
		this._buf[offset + 0] = (byte)((value >> 24) & 0xff);
		this._buf[offset + 1] = (byte)((value >> 16) & 0xff);
		this._buf[offset + 2] = (byte)((value >>  8) & 0xff);
		this._buf[offset + 3] = (byte)((value >>  0) & 0xff);
	}

	public final long getLong (int offset) {
		return ((long)this._buf[offset + 0] << 56) +			/* keep sign-bit */
		       ((long)(this._buf[offset + 1] & 0xff) << 48) +
		       ((long)(this._buf[offset + 2] & 0xff) << 40) +
		       ((long)(this._buf[offset + 3] & 0xff) << 32) +
		       ((long)(this._buf[offset + 4] & 0xff) << 24) +
		       ((this._buf[offset + 5] & 0xff) << 16) +
		       ((this._buf[offset + 6] & 0xff) <<  8) +
		       ((this._buf[offset + 7] & 0xff) <<  0);
	}

	public final void setLong (int offset, long value) {
		this._buf[offset + 0] = (byte) (value >> 56);
		this._buf[offset + 1] = (byte)((value >> 48) & 0xff);
		this._buf[offset + 2] = (byte)((value >> 40) & 0xff);
		this._buf[offset + 3] = (byte)((value >> 32) & 0xff);
		this._buf[offset + 4] = (byte)((value >> 24) & 0xff);
		this._buf[offset + 5] = (byte)((value >> 16) & 0xff);
		this._buf[offset + 6] = (byte)((value >>  8) & 0xff);
		this._buf[offset + 7] = (byte)((value >>  0) & 0xff);
	}
}
