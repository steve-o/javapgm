/* PGM Option Length Extension.
 */
import java.io.*;
import java.net.*;
import java.util.*;

public class OptionLength {

	protected SocketBuffer		_skb = null;
	protected int			_offset = 0;

	private static final int OPT_TYPE_OFFSET		= 0;
	private static final int OPT_LENGTH_OFFSET		= 1;
	private static final int OPT_TOTAL_LENGTH_OFFSET	= 2;

	private static final int SIZEOF_PGM_OPT_LENGTH		= 4;

	public OptionLength (SocketBuffer skb, int offset) {
		this._skb = skb;
		this._offset = offset;
	}

	public static OptionLength create (SocketBuffer skb, int offset) {
		OptionLength optLength = new OptionLength (skb, offset);
		skb.setUnsignedByte (offset + OPT_TYPE_OFFSET, OptionHeader.OPT_LENGTH);
		skb.setUnsignedByte (offset + OPT_LENGTH_OFFSET, optLength.getLength());
		skb.reserve (optLength.getLength());
		return optLength;
	}

	public int getOffset() {
		return this._offset;
	}

	public int getLength() {
		return SIZEOF_PGM_OPT_LENGTH;
	}

	public void setTotalLength (int total_length) {
		this._skb.setUnsignedShort (this._offset + OPT_TOTAL_LENGTH_OFFSET, total_length);
	}
}

/* eof */
