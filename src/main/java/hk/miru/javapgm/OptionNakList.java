/* PGM Option NAK List Extension.
 */
package hk.miru.javapgm;

import static hk.miru.javapgm.Preconditions.checkArgument;
import static hk.miru.javapgm.Preconditions.checkNotNull;

public class OptionNakList {

	protected SocketBuffer	_skb = null;
	protected int		_offset = 0;
	protected int           _length = 0;

	private static final int OPT_TYPE_OFFSET    = 0;
	private static final int OPT_LENGTH_OFFSET  = 1;
	private static final int OPT_SQN_OFFSET     = 4;

	private static final int SIZEOF_PGM_OPT_NAK_LIST_HEADER	= 4;
	private static final int SIZEOF_PGM_SQN			= 4;

	public OptionNakList (SocketBuffer skb, int offset, int length) {
                checkNotNull (skb);
		this._skb = skb;
		this._offset = offset;
		this._length = length;
	}

	public static OptionNakList create (SocketBuffer skb, int offset, int count) {
		OptionNakList optNakList = new OptionNakList (skb, offset, calculateLength (count));
		skb.setUnsignedByte (offset + OPT_TYPE_OFFSET, OptionHeader.OPT_NAK_LIST);
		skb.setUnsignedByte (offset + OPT_LENGTH_OFFSET, optNakList.getLength());
		skb.reserve (optNakList.getLength());
		return optNakList;
	}

	public int getOffset() {
		return this._offset;
	}

	public int getLength() {
		return this._length;
	}
        
        private static int calculateLength (int count) {
                checkArgument (count > 0 && count <= 62);
		return SIZEOF_PGM_OPT_NAK_LIST_HEADER + (count * SIZEOF_PGM_SQN);            
        }
        
        public int getSequenceCount() {
                return (this._length - SIZEOF_PGM_OPT_NAK_LIST_HEADER) / SIZEOF_PGM_SQN;
        }
        
        public SequenceNumber getOptionSequence (int index) {
                checkArgument (index >= 0);
                return SequenceNumber.fromIntBits (this._skb.getSignedInt (this._offset + OPT_SQN_OFFSET + (index * SIZEOF_PGM_SQN)));
        }

	public void setOptionSequence (int index, SequenceNumber sequence) {
                checkArgument (index >= 0);
                checkNotNull (sequence);
		this._skb.setUnsignedInt (this._offset + OPT_SQN_OFFSET + (index * SIZEOF_PGM_SQN), sequence.longValue());
	}
}

/* eof */