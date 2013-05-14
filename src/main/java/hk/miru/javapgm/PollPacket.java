/* Poll packet for starting receiver poll rounds.
 */
package hk.miru.javapgm;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

@SuppressWarnings("unused")
public class PollPacket {

	protected SocketBuffer	_skb = null;
	protected int		_offset = 0;

	private static final int POLL_SQN_OFFSET	= 0;
	private static final int POLL_ROUND_OFFSET	= 4;
	private static final int POLL_S_TYPE_OFFSET	= 6;
	private static final int POLL_NLA_AFI_OFFSET	= 8;
	private static final int POLL_RESERVED_OFFSET	= 10;
	private static final int POLL_NLA_OFFSET	= 12;
	private static final int POLL_BO_IVL_OFFSET	= 16;
	private static final int POLL_RAND_OFFSET	= 20;
	private static final int POLL_MASK_OFFSET	= 24;
	private static final int POLL_OPTIONS_OFFSET	= 28;

	private static final int POLL6_SQN_OFFSET	= 0;
	private static final int POLL6_ROUND_OFFSET	= 4;
	private static final int POLL6_S_TYPE_OFFSET	= 6;
	private static final int POLL6_NLA_AFI_OFFSET	= 8;
	private static final int POLL6_RESERVED_OFFSET	= 10;
	private static final int POLL6_NLA_OFFSET	= 12;
	private static final int POLL6_BO_IVL_OFFSET	= 28;
	private static final int POLL6_RAND_OFFSET	= 32;
	private static final int POLL6_MASK_OFFSET	= 36;
	private static final int POLL6_OPTIONS_OFFSET	= 40;

	private static final int SIZEOF_INADDR	= 4;
	private static final int SIZEOF_INADDR6	= 16;

	public PollPacket (SocketBuffer skb, int offset) {
		this._skb = skb;
		this._offset = offset;
	}

	public final long getSequenceNumber() {
		return this._skb.getUnsignedInt (this._offset + POLL_SQN_OFFSET);
	}

	public final int getRound() {
		return this._skb.getUnsignedShort (this._offset + POLL_ROUND_OFFSET);
	}

	public final int getSubtype() {
		return this._skb.getUnsignedShort (this._offset + POLL_S_TYPE_OFFSET);
	}

	public final int getPathAddressFamilyIndicator() {
		return this._skb.getUnsignedShort (this._offset + POLL_NLA_AFI_OFFSET);
	}

	public final String getPath() {
		InetAddress poll_nla = null;
		try {
			switch (this.getPathAddressFamilyIndicator()) {
			case Packet.AFI_IP:
				byte[] in_addr = new byte[SIZEOF_INADDR];
				System.arraycopy (this._skb.getRawBytes(), this._offset + POLL_NLA_OFFSET,
						  in_addr, 0,
						  in_addr.length);
				poll_nla = Inet4Address.getByAddress (in_addr);
				break;
			case Packet.AFI_IP6:
				byte[] in6_addr = new byte[SIZEOF_INADDR6];
				System.arraycopy (this._skb.getRawBytes(), this._offset + POLL_NLA_OFFSET,
						  in6_addr, 0,
						  in6_addr.length);
				poll_nla = Inet6Address.getByAddress (in6_addr);
				break;
			default:
				break;
			}
		} catch (UnknownHostException e) {}
		if (null == poll_nla)
			return "unknown";
		else
			return poll_nla.toString();
	}

	public final long getBackoffInterval() {
		switch (this.getPathAddressFamilyIndicator()) {
		case Packet.AFI_IP:
			return this._skb.getUnsignedInt (this._offset + POLL_BO_IVL_OFFSET);
		case Packet.AFI_IP6:
			return this._skb.getUnsignedInt (this._offset + POLL6_BO_IVL_OFFSET);
		default:
			return 0;
		}
	}

	public final long getRand() {
		switch (this.getPathAddressFamilyIndicator()) {
		case Packet.AFI_IP:
			return this._skb.getUnsignedInt (this._offset + POLL_RAND_OFFSET);
		case Packet.AFI_IP6:
			return this._skb.getUnsignedInt (this._offset + POLL6_RAND_OFFSET);
		default:
			return 0;
		}
	}

	public final long getMask() {
		switch (this.getPathAddressFamilyIndicator()) {
		case Packet.AFI_IP:
			return this._skb.getUnsignedInt (this._offset + POLL_MASK_OFFSET);
		case Packet.AFI_IP6:
			return this._skb.getUnsignedInt (this._offset + POLL6_MASK_OFFSET);
		default:
			return 0;
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
		       ", \"pollSqn\": " + this.getSequenceNumber() +
		       ", \"pollRound\": " + this.getRound() +
		       ", \"pollSType\": " + this.getSubtype() +
		       ", \"pollNlaAfi\": " + this.getPathAddressFamilyIndicator() +
		       ", \"pollNla\": \"" + this.getPath() + "\"" +
		       ", \"pollBoIvl\": " + this.getBackoffInterval() +
		       ", \"pollRand\": 0x" + Long.toHexString (this.getRand()) +
		       ", \"pollMask\": 0x" + Long.toHexString (this.getMask()) +
		        "}\n";
	}

}

/* eof */