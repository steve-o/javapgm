/* Null-NAK or NNAK packet.  Feedback by PGM infrastructure after
 * suppressing local network NAKs for adaptive parameter calculation.
 */
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

@SuppressWarnings("unused")
public class NullNakPacket {

	protected SocketBuffer	_skb = null;
	protected int			_offset = 0;

	private static final int NNAK_SQN_OFFSET			= 0;
	private static final int NNAK_SRC_NLA_AFI_OFFSET	= 4;
	private static final int NNAK_RESERVED_OFFSET		= 6;
	private static final int NNAK_SRC_NLA_OFFSET		= 8;
	private static final int NNAK_GRP_NLA_AFI_OFFSET	= 12;
	private static final int NNAK_RESERVED2_OFFSET		= 14;
	private static final int NNAK_GRP_NLA_OFFSET		= 16;
	private static final int NNAK_OPTIONS_OFFSET		= 20;

	private static final int NNAK6_SQN_OFFSET			= 0;
	private static final int NNAK6_SRC_NLA_AFI_OFFSET	= 4;
	private static final int NNAK6_RESERVED_OFFSET		= 6;
	private static final int NNAK6_SRC_NLA_OFFSET		= 8;
	private static final int NNAK6_GRP_NLA_AFI_OFFSET	= 24;
	private static final int NNAK6_RESERVED2_OFFSET		= 26;
	private static final int NNAK6_GRP_NLA_OFFSET		= 28;
	private static final int NNAK6_OPTIONS_OFFSET		= 44;

	private static final int SIZEOF_INADDR	= 4;
	private static final int SIZEOF_INADDR6	= 16;

	public NullNakPacket (SocketBuffer skb, int offset) {
		this._skb = skb;
		this._offset = offset;
	}

	public final long getSequenceNumber() {
		return this._skb.getUnsignedInt (this._offset + NNAK_SQN_OFFSET);
	}

	public final int getSourcePathAddressFamilyIndicator() {
		return this._skb.getUnsignedShort (this._offset + NNAK_SRC_NLA_AFI_OFFSET);
	}

	public final String getSourcePath() {
		InetAddress nnak_src_nla = null;
		try {
			switch (getSourcePathAddressFamilyIndicator()) {
			case Packet.AFI_IP:
				byte[] in_addr = new byte[SIZEOF_INADDR];
				System.arraycopy (this._skb.getRawBytes(), this._offset + NNAK_SRC_NLA_OFFSET,
						  in_addr, 0,
						  in_addr.length);
				nnak_src_nla = Inet4Address.getByAddress (in_addr);
				break;
			case Packet.AFI_IP6:
				byte[] in6_addr = new byte[SIZEOF_INADDR6];
				System.arraycopy (this._skb.getRawBytes(), this._offset + NNAK_SRC_NLA_OFFSET,
						  in6_addr, 0,
						  in6_addr.length);
				nnak_src_nla = Inet6Address.getByAddress (in6_addr);
				break;
			default:
				break;
			}
		} catch (UnknownHostException e) {}
		if (null == nnak_src_nla)
			return "unknown";
		else
			return nnak_src_nla.toString();
	}

	public final int getGroupPathAddressFamilyIndicator() {
		switch (getSourcePathAddressFamilyIndicator()) {
		case Packet.AFI_IP:
			return this._skb.getUnsignedShort (this._offset + NNAK_GRP_NLA_AFI_OFFSET);
		case Packet.AFI_IP6:
			return this._skb.getUnsignedShort (this._offset + NNAK6_GRP_NLA_AFI_OFFSET);
		default:
			return 0;
		}
	}

	public final String getGroupPath() {
		InetAddress nnak_grp_nla = null;
		int nnak_grp_nla_offset;
		switch (getSourcePathAddressFamilyIndicator()) {
		case Packet.AFI_IP:
			nnak_grp_nla_offset = NNAK_GRP_NLA_OFFSET;
			break;
		case Packet.AFI_IP6:
			nnak_grp_nla_offset = NNAK6_GRP_NLA_OFFSET;
			break;
		default:
			return "invalid";
		}
		try {
			switch (getGroupPathAddressFamilyIndicator()) {
			case Packet.AFI_IP:
				byte[] in_addr = new byte[SIZEOF_INADDR];
				System.arraycopy (this._skb.getRawBytes(), this._offset + nnak_grp_nla_offset,
						  in_addr, 0,
						  in_addr.length);
				nnak_grp_nla = Inet4Address.getByAddress (in_addr);
				break;
			case Packet.AFI_IP6:
				byte[] in6_addr = new byte[SIZEOF_INADDR6];
				System.arraycopy (this._skb.getRawBytes(), this._offset + nnak_grp_nla_offset,
						  in6_addr, 0,
						  in6_addr.length);
				nnak_grp_nla = Inet6Address.getByAddress (in6_addr);
				break;
			default:
				break;
			}
		} catch (UnknownHostException e) {}
		if (null == nnak_grp_nla)
			return "unknown";
		else
			return nnak_grp_nla.toString();
	}

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
		       ", \"nnakSqn\": " + getSequenceNumber() +
		       ", \"nnakSrcNlaAfi\": " + getSourcePathAddressFamilyIndicator() +
		       ", \"nnakSrcNla\": " + getSourcePath() +
		       ", \"nnakGrpNlaAfi\": " + getGroupPathAddressFamilyIndicator() +
		       ", \"nnakGrpNla\": " + getGroupPath() +
		        "}\n";
	}
}

/* eof */