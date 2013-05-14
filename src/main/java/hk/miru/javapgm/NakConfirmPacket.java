/* NAK confirm packet or NCF is sent on immediate receipt of a NAK to suppress
 * other peer subscribers from sending their own NAK.
 */
package hk.miru.javapgm;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

@SuppressWarnings("unused")
public class NakConfirmPacket {

	protected SocketBuffer	_skb = null;
	protected int		_offset = 0;

	private static final int NCF_SQN_OFFSET			= 0;
	private static final int NCF_SRC_NLA_AFI_OFFSET		= 4;
	private static final int NCF_RESERVED_OFFSET		= 6;
	private static final int NCF_SRC_NLA_OFFSET		= 8;
	private static final int NCF_GRP_NLA_AFI_OFFSET		= 12;
	private static final int NCF_RESERVED2_OFFSET		= 14;
	private static final int NCF_GRP_NLA_OFFSET		= 16;
	private static final int NCF_OPTIONS_OFFSET		= 20;

	private static final int NCF6_SQN_OFFSET		= 0;
	private static final int NCF6_SRC_NLA_AFI_OFFSET	= 4;
	private static final int NCF6_RESERVED_OFFSET		= 6;
	private static final int NCF6_SRC_NLA_OFFSET		= 8;
	private static final int NCF6_GRP_NLA_AFI_OFFSET	= 24;
	private static final int NCF6_RESERVED2_OFFSET		= 26;
	private static final int NCF6_GRP_NLA_OFFSET		= 28;
	private static final int NCF6_OPTIONS_OFFSET		= 44;

	private static final int SIZEOF_INADDR	= 4;
	private static final int SIZEOF_INADDR6	= 16;

	public NakConfirmPacket (SocketBuffer skb, int offset) {
		this._skb = skb;
		this._offset = offset;
	}

	public final long getSequenceNumber() {
		return this._skb.getUnsignedInt (this._offset + NCF_SQN_OFFSET);
	}

	public final int getSourcePathAddressFamilyIndicator() {
		return this._skb.getUnsignedShort (this._offset + NCF_SRC_NLA_AFI_OFFSET);
	}

	public final String getSourcePath() {
		InetAddress ncf_src_nla = null;
		try {
			switch (getSourcePathAddressFamilyIndicator()) {
			case Packet.AFI_IP:
				byte[] in_addr = new byte[SIZEOF_INADDR];
				System.arraycopy (this._skb.getRawBytes(), this._offset + NCF_SRC_NLA_OFFSET,
						  in_addr, 0,
						  in_addr.length);
				ncf_src_nla = Inet4Address.getByAddress (in_addr);
				break;
			case Packet.AFI_IP6:
				byte[] in6_addr = new byte[SIZEOF_INADDR6];
				System.arraycopy (this._skb.getRawBytes(), this._offset + NCF_SRC_NLA_OFFSET,
						  in6_addr, 0,
						  in6_addr.length);
				ncf_src_nla = Inet6Address.getByAddress (in6_addr);
				break;
			default:
				break;
			}
		} catch (UnknownHostException e) {}
		if (null == ncf_src_nla)
			return "unknown";
		else
			return ncf_src_nla.toString();
	}

	public final int getGroupPathAddressFamilyIndicator() {
		switch (getSourcePathAddressFamilyIndicator()) {
		case Packet.AFI_IP:
			return this._skb.getUnsignedShort (this._offset + NCF_GRP_NLA_AFI_OFFSET);
		case Packet.AFI_IP6:
			return this._skb.getUnsignedShort (this._offset + NCF6_GRP_NLA_AFI_OFFSET);
		default:
			return 0;
		}
	}

	public final String getGroupPath() {
		InetAddress ncf_grp_nla = null;
		int ncf_grp_nla_offset;
		switch (getSourcePathAddressFamilyIndicator()) {
		case Packet.AFI_IP:
			ncf_grp_nla_offset = NCF_GRP_NLA_OFFSET;
			break;
		case Packet.AFI_IP6:
			ncf_grp_nla_offset = NCF6_GRP_NLA_OFFSET;
			break;
		default:
			return "invalid";
		}
		try {
			switch (getGroupPathAddressFamilyIndicator()) {
			case Packet.AFI_IP:
				byte[] in_addr = new byte[SIZEOF_INADDR];
				System.arraycopy (this._skb.getRawBytes(), this._offset + ncf_grp_nla_offset,
						  in_addr, 0,
						  in_addr.length);
				ncf_grp_nla = Inet4Address.getByAddress (in_addr);
				break;
			case Packet.AFI_IP6:
				byte[] in6_addr = new byte[SIZEOF_INADDR6];
				System.arraycopy (this._skb.getRawBytes(), this._offset + ncf_grp_nla_offset,
						  in6_addr, 0,
						  in6_addr.length);
				ncf_grp_nla = Inet6Address.getByAddress (in6_addr);
				break;
			default:
				break;
			}
		} catch (UnknownHostException e) {}
		if (null == ncf_grp_nla)
			return "unknown";
		else
			return ncf_grp_nla.toString();
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
		       ", \"ncfSqn\": " + getSequenceNumber() +
		       ", \"ncfSrcNlaAfi\": " + getSourcePathAddressFamilyIndicator() +
		       ", \"ncfSrcNla\": " + getSourcePath() +
		       ", \"ncfGrpNlaAfi\": " + getGroupPathAddressFamilyIndicator() +
		       ", \"ncfGrpNla\": " + getGroupPath() +
		        "}";
	}

}

/* eof */