/* Negative acknowledgement or NAK packet.  Sent by receiver to source to re-
 * request delivery of a lost or corrupt packet.
 */
import java.io.*;
import java.net.*;
import java.util.*;

public class NakPacket {

	protected SocketBuffer	_skb = null;
	protected int		_offset = 0;

	private static final int NAK_SQN_OFFSET			= 0;
	private static final int NAK_SRC_NLA_AFI_OFFSET		= 4;
	private static final int NAK_RESERVED_OFFSET		= 6;
	private static final int NAK_SRC_NLA_OFFSET		= 8;
	private static final int NAK_GRP_NLA_AFI_OFFSET		= 12;
	private static final int NAK_RESERVED2_OFFSET		= 14;
	private static final int NAK_GRP_NLA_OFFSET		= 16;
	private static final int NAK_OPTIONS_OFFSET		= 20;

	private static final int NAK6_SQN_OFFSET		= 0;
	private static final int NAK6_SRC_NLA_AFI_OFFSET	= 4;
	private static final int NAK6_RESERVED_OFFSET		= 6;
	private static final int NAK6_SRC_NLA_OFFSET		= 8;
	private static final int NAK6_GRP_NLA_AFI_OFFSET	= 24;
	private static final int NAK6_RESERVED2_OFFSET		= 26;
	private static final int NAK6_GRP_NLA_OFFSET		= 28;
	private static final int NAK6_OPTIONS_OFFSET		= 44;

	private static final int SIZEOF_INADDR			= 4;
	private static final int SIZEOF_INADDR6			= 16;

	public NakPacket (SocketBuffer skb, int offset) {
		this._skb = skb;
		this._offset = offset;
	}

	public final long getSequenceNumber() {
		return this._skb.getUnsignedInt (this._offset + NAK_SQN_OFFSET);
	}

	public final int getSourcePathAddressFamilyIndicator() {
		return this._skb.getUnsignedShort (this._offset + NAK_SRC_NLA_AFI_OFFSET);
	}

	public final String getSourcePath() {
		InetAddress nak_src_nla = null;
		try {
			switch (getSourcePathAddressFamilyIndicator()) {
			case Packet.AFI_IP:
				byte[] in_addr = new byte[SIZEOF_INADDR];
				System.arraycopy (this._skb.getRawBytes(), this._offset + NAK_SRC_NLA_OFFSET,
						  in_addr, 0,
						  in_addr.length);
				nak_src_nla = Inet4Address.getByAddress (in_addr);
				break;
			case Packet.AFI_IP6:
				byte[] in6_addr = new byte[SIZEOF_INADDR6];
				System.arraycopy (this._skb.getRawBytes(), this._offset + NAK_SRC_NLA_OFFSET,
						  in6_addr, 0,
						  in6_addr.length);
				nak_src_nla = Inet6Address.getByAddress (in6_addr);
				break;
			default:
				break;
			}
		} catch (UnknownHostException e) {}
		if (null == nak_src_nla)
			return "unknown";
		else
			return nak_src_nla.toString();
	}

	public final int getGroupPathAddressFamilyIndicator() {
		switch (getSourcePathAddressFamilyIndicator()) {
		case Packet.AFI_IP:
			return this._skb.getUnsignedShort (this._offset + NAK_GRP_NLA_AFI_OFFSET);
		case Packet.AFI_IP6:
			return this._skb.getUnsignedShort (this._offset + NAK6_GRP_NLA_AFI_OFFSET);
		default:
			return 0;
		}
	}

	public final String getGroupPath() {
		InetAddress nak_grp_nla = null;
		int nak_grp_nla_offset;
		switch (getSourcePathAddressFamilyIndicator()) {
		case Packet.AFI_IP:
			nak_grp_nla_offset = NAK_GRP_NLA_OFFSET;
			break;
		case Packet.AFI_IP6:
			nak_grp_nla_offset = NAK6_GRP_NLA_OFFSET;
			break;
		default:
			return "invalid";
		}
		try {
			switch (getGroupPathAddressFamilyIndicator()) {
			case Packet.AFI_IP:
				byte[] in_addr = new byte[SIZEOF_INADDR];
				System.arraycopy (this._skb.getRawBytes(), this._offset + nak_grp_nla_offset,
						  in_addr, 0,
						  in_addr.length);
				nak_grp_nla = Inet4Address.getByAddress (in_addr);
				break;
			case Packet.AFI_IP6:
				byte[] in6_addr = new byte[SIZEOF_INADDR6];
				System.arraycopy (this._skb.getRawBytes(), this._offset + nak_grp_nla_offset,
						  in6_addr, 0,
						  in6_addr.length);
				nak_grp_nla = Inet6Address.getByAddress (in6_addr);
				break;
			default:
				break;
			}
		} catch (UnknownHostException e) {}
		if (null == nak_grp_nla)
			return "unknown";
		else
			return nak_grp_nla.toString();
	}

	public String toString() {
		Header header = this._skb.getHeader();
		return  "{" +
			 "\"sourcePort\": " + header.getSourcePort() +
		       ", \"destinationPort\": " + header.getDestinationPort() +
		       ", \"type\": \"" + header.getTypeName() + "\"" +
		       ", \"options\": " + header.getOptions() +
		       ", \"checksum\": 0x" + Integer.toHexString (header.getChecksum()) +
		       ", \"gsi\": \"" + header.getGlobalSourceId() + "\"" +
		       ", \"tsduLength\": " + header.getTsduLength() +
		       ", \"nakSqn\": " + getSequenceNumber() +
		       ", \"nakSrcNlaAfi\": " + getSourcePathAddressFamilyIndicator() +
		       ", \"nakSrcNla\": " + getSourcePath() +
		       ", \"nakGrpNlaAfi\": " + getGroupPathAddressFamilyIndicator() +
		       ", \"nakGrpNla\": " + getGroupPath() +
		        "}";
	}
}

/* eof */
