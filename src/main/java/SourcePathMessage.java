/* Source path message or SPM packet.  Used for defining and keeping multicast
 * circuit state.
 */
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

@SuppressWarnings("unused")
public class SourcePathMessage {

	protected SocketBuffer	_skb = null;
	protected int		_offset = 0;

	private static final int SPM_SQN_OFFSET		= 0;
	private static final int SPM_TRAIL_OFFSET	= 4;
	private static final int SPM_LEAD_OFFSET	= 8;
	private static final int SPM_NLA_AFI_OFFSET	= 12;
	private static final int SPM_RESERVED_OFFSET	= 14;
	private static final int SPM_NLA_OFFSET		= 16;
	private static final int SPM_OPTIONS_OFFSET	= 20;

	private static final int SPM6_SQN_OFFSET	= 0;
	private static final int SPM6_TRAIL_OFFSET	= 4;
	private static final int SPM6_LEAD_OFFSET	= 8;
	private static final int SPM6_NLA_AFI_OFFSET	= 12;
	private static final int SPM6_RESERVED_OFFSET	= 14;
	private static final int SPM6_NLA_OFFSET	= 16;
	private static final int SPM6_OPTIONS_OFFSET	= 32;

	private static final int SIZEOF_INADDR	= 4;
	private static final int SIZEOF_INADDR6	= 16;

	public SourcePathMessage (SocketBuffer skb, int offset) {
		this._skb = skb;
		this._offset = offset;
	}

	public final SequenceNumber getSpmSequenceNumber() {
		return SequenceNumber.fromIntBits (this._skb.getSignedInt (this._offset + SPM_SQN_OFFSET));
	}

	public final SequenceNumber getSpmTrail() {
		return SequenceNumber.fromIntBits (this._skb.getSignedInt (this._offset + SPM_TRAIL_OFFSET));
	}

	public final SequenceNumber getSpmLead() {
		return SequenceNumber.fromIntBits (this._skb.getSignedInt (this._offset + SPM_LEAD_OFFSET));
	}

	public final int getSpmNlaAfi() {
		return this._skb.getUnsignedShort (this._offset + SPM_NLA_AFI_OFFSET);
	}

	public final InetAddress getSpmNla() {
		try {
			switch (this.getSpmNlaAfi()) {
			case Packet.AFI_IP:
				byte[] in_addr = new byte[SIZEOF_INADDR];
				System.arraycopy (this._skb.getRawBytes(), this._offset + SPM_NLA_OFFSET,
						  in_addr, 0,
						  in_addr.length);
				return Inet4Address.getByAddress (in_addr);
			case Packet.AFI_IP6:
				byte[] in6_addr = new byte[SIZEOF_INADDR6];
				System.arraycopy (this._skb.getRawBytes(), this._offset + SPM_NLA_OFFSET,
						  in6_addr, 0,
						  in6_addr.length);
				return Inet6Address.getByAddress (in6_addr);
			default:
				return null;
			}
		} catch (UnknownHostException e) {
			return null;
		}
	}

        @Override
	public String toString() {
		Header header = this._skb.getHeader();
		return  "{" +
			 "\"sourcePort\": " + header.getSourcePort() +
		       ", \"destinationPort\": " + header.getDestinationPort() +
		       ", \"type\": \"" + header.getTypeAsString() + "\"" +
		       ", \"options\": " + header.getOptions() +
		       ", \"checksum\": 0x" + Integer.toHexString (header.getChecksum()) +
		       ", \"gsi\": \"" + header.getGlobalSourceId() + "\"" +
		       ", \"tsduLength\": " + header.getTsduLength() +
		       ", \"spmSqn\": " + this.getSpmSequenceNumber() +
		       ", \"spmTrail\": " + this.getSpmTrail() +
		       ", \"spmLead\": " + this.getSpmLead() +
		       ", \"spmNlaAfi\": " + this.getSpmNlaAfi() +
		       ", \"spmNla\": \"" + this.getSpmNla() + "\"" +
		        "}\n";
	}
}

/* eof */