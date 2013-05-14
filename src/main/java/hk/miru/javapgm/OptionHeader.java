/* PGM Option Extension.
 */
package hk.miru.javapgm;

public class OptionHeader {

	protected SocketBuffer	_skb = null;
	protected int		_offset = 0;

	private static final int OPT_TYPE_OFFSET	= 0;
	private static final int OPT_LENGTH_OFFSET	= 1;
	public static final int OPT_TOTAL_LENGTH_OFFSET	= 2;

	private static final int OPT_MASK	= 0x7f;
	private static final int OPT_END	= 0x80;

	public static final int OPT_LENGTH	= 0x00;
	public static final int OPT_FRAGMENT	= 0x01;
	public static final int OPT_NAK_LIST	= 0x02;
	public static final int OPT_JOIN	= 0x03;
	public static final int OPT_REDIRECT	= 0x07;
	public static final int OPT_SYN		= 0x0d;
	public static final int OPT_FIN		= 0x0e;
	public static final int OPT_RST		= 0x0f;

	public OptionHeader (SocketBuffer skb, int offset) {
		this._skb = skb;
		this._offset = offset;
	}

	private int getRawType() {
		return this._skb.getUnsignedByte (this._offset + OPT_TYPE_OFFSET);
	}

	public final int getType() {
		return (getRawType() & OPT_MASK);
	}

	public final boolean isFragmentOption() {
		return (OPT_FRAGMENT == getType());
	}

	public final boolean isNakListOption() {
		return (OPT_NAK_LIST == getType());
	}

	public final boolean isLastOption() {
		return ((getRawType() & OPT_END) == OPT_END);
	}

	public void setLastOption() {
		this._skb.setUnsignedByte (this._offset + OPT_TYPE_OFFSET, getRawType() | OPT_END);
	}

	public final int getOffset() {
		return this._offset;
	}

	public final int getLength() {
		return this._skb.getUnsignedByte (this._offset + OPT_LENGTH_OFFSET);
	}
}

/* eof */