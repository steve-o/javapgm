/* PGM Option NAK List Extension.
 */

public class OptionNakList {

	protected SocketBuffer	_skb = null;
	protected int			_offset = 0;
	protected int			_count = 0;

	private static final int OPT_TYPE_OFFSET		= 0;
	private static final int OPT_LENGTH_OFFSET		= 1;
	private static final int OPT_SQN_OFFSET			= 4;

	private static final int SIZEOF_PGM_OPT_NAK_LIST_HEADER	= 4;
	private static final int SIZEOF_PGM_SQN			= 4;

	public OptionNakList (SocketBuffer skb, int offset, int count) {
		this._skb = skb;
		this._offset = offset;
		this._count = count;
	}

	public static OptionNakList create (SocketBuffer skb, int offset, int count) {
		OptionNakList optNakList = new OptionNakList (skb, offset, count);
		skb.setUnsignedByte (offset + OPT_TYPE_OFFSET, OptionHeader.OPT_NAK_LIST);
		skb.setUnsignedByte (offset + OPT_LENGTH_OFFSET, optNakList.getLength());
		skb.reserve (optNakList.getLength());
		return optNakList;
	}

	public int getOffset() {
		return this._offset;
	}

	public int getLength() {
		return SIZEOF_PGM_OPT_NAK_LIST_HEADER + (this._count * SIZEOF_PGM_SQN);
	}

	public void setOptionSequence (int index, SequenceNumber sequence) {
		this._skb.setUnsignedInt (this._offset + OPT_SQN_OFFSET + (index * SIZEOF_PGM_SQN), sequence.longValue());
	}
}

/* eof */