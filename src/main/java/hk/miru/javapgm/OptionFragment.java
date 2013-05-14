/* PGM Option Fragment Extension.
 */
package hk.miru.javapgm;

public class OptionFragment {

	protected SocketBuffer	_skb = null;
	protected int		_offset = 0;

	private static final int OPT_SQN_OFFSET		= 4;
	private static final int OPT_FRAG_OFF_OFFSET	= 8;
	private static final int OPT_FRAG_LEN_OFFSET	= 12;

	public OptionFragment (SocketBuffer skb, int offset) {
		this._skb = skb;
		this._offset = offset;
	}

	public final SequenceNumber getFirstSequenceNumber() {
		return SequenceNumber.fromIntBits (this._skb.getSignedInt (this._offset + OPT_SQN_OFFSET));
	}

	public final long getFragmentOffset() {
		return this._skb.getUnsignedInt (this._offset + OPT_FRAG_OFF_OFFSET);
	}

/* Rename FRAG_LEN to APDU length as this isn't the fragment length */
	public final long getApduLength() {
		return this._skb.getUnsignedInt (this._offset + OPT_FRAG_LEN_OFFSET);
	}
}

/* eof */