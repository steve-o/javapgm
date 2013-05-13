/* Original data or ODATA packet.  Used for wrapping first tranmission of data
 * on the wire.
 */

public class OriginalData {

	protected SocketBuffer	_skb = null;
	protected int			_offset = 0;

	public static final int SIZEOF_DATA_HEADER		= 8;

	private static final int DATA_SQN_OFFSET		= 0;
	private static final int DATA_TRAIL_OFFSET		= 4;
	private static final int DATA_OPTIONS_OFFSET	= SIZEOF_DATA_HEADER;

	private static final int OPT_TOTAL_LENGTH_OFFSET = 2;

	public OriginalData (SocketBuffer skb, int offset) {
		this._skb = skb;
		this._offset = offset;
	}

	public final SequenceNumber getDataSequenceNumber() {
		return SequenceNumber.fromIntBits (this._skb.getSignedInt (this._offset + DATA_SQN_OFFSET));
	}

	public final void setDataSequenceNumber (SequenceNumber data_sqn) {
		this._skb.setUnsignedInt (this._offset + DATA_SQN_OFFSET, data_sqn.longValue());
	}

	public final SequenceNumber getDataTrail() {
		return SequenceNumber.fromIntBits (this._skb.getSignedInt (this._offset + DATA_TRAIL_OFFSET));
	}

	public final void setDataTrail (SequenceNumber data_trail) {
		this._skb.setUnsignedInt (this._offset + DATA_TRAIL_OFFSET, data_trail.longValue());
	}

	public final int getOptionTotalLength() {
		if (!this._skb.getHeader().isOptionExtensionPresent()) {
			return 0;
		} else {
			return this._skb.getUnsignedShort (this._offset + DATA_OPTIONS_OFFSET + OPT_TOTAL_LENGTH_OFFSET);
		}
	}

	public final byte[] getData () {
		byte[] buf = new byte[this._skb.getHeader().getTsduLength()];
		System.arraycopy (this._skb.getRawBytes(), this._offset + DATA_OPTIONS_OFFSET,
				  buf, 0,
				  this._skb.getHeader().getTsduLength());
		return buf;
	}

	public final String getDataAsString () {
		byte[] buf = getData();
		return new String (buf);
	}

	public String toString() {
		Header header = this._skb.getHeader();
		return  "{ " +
			 "\"sourcePort\": " + header.getSourcePort() +
		       ", \"destinationPort\": " + header.getDestinationPort() +
		       ", \"type\": \"" + header.getTypeAsString() + "\"" +
		       ", \"options\": " + header.getOptions() +
		       ", \"checksum\": 0x" + Integer.toHexString (header.getChecksum()) +
		       ", \"gsi\": \"" + header.getGlobalSourceId() + "\"" +
		       ", \"tsduLength\": " + header.getTsduLength() +
		       ", \"dataSqn\": " + getDataSequenceNumber() +
		       ", \"dataTrail\": " + getDataTrail() +
		       ", \"dataData\": \"" + getDataAsString() + "\"" +
		        " }";
	}
}

/* eof */