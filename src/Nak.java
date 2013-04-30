/* Negative acknowledgement or NAK packet.  Sent by receiver to source to re-
 * request delivery of a lost or corrupt packet.
 */
import java.io.*;
import java.net.*;
import java.util.*;

public class Nak {

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
	private static final int SIZEOF_PGM_NAK			= 20;

	public Nak (SocketBuffer skb, int offset) {
		this._skb = skb;
		this._offset = offset;
	}

	public static SocketBuffer create() {
		SocketBuffer skb = new SocketBuffer (Packet.SIZEOF_PGM_HEADER + SIZEOF_PGM_NAK);
		skb.setHeaderOffset (0);
		skb.getHeader().setType (Packet.PGM_NAK);
		skb.pull (Packet.SIZEOF_PGM_HEADER);
		return skb;
	}

	public final long getNakSequenceNumber() {
		return this._skb.getUnsignedInt (this._offset + NAK_SQN_OFFSET);
	}

	public void setNakSequenceNumber (SequenceNumber nak_sqn) {
		this._skb.setUnsignedInt (this._offset + NAK_SQN_OFFSET, nak_sqn.longValue());
	}

	public final int getNakSourceNlaAfi() {
		return this._skb.getUnsignedShort (this._offset + NAK_SRC_NLA_AFI_OFFSET);
	}

	private void setNakSourceNlaAfi (int afi) {
		this._skb.setUnsignedShort (this._offset + NAK_SRC_NLA_AFI_OFFSET, afi);
	}

	public final InetAddress getNakSourceNla() {
		InetAddress nak_src_nla = null;
		try {
			switch (getNakSourceNlaAfi()) {
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
		return nak_src_nla;
	}

	public void setNakSourceNla (InetAddress nak_src_nla) {
		if (Inet4Address.class.isInstance (nak_src_nla)) {
			setNakSourceNlaAfi (Packet.AFI_IP);
			System.arraycopy (nak_src_nla.getAddress(), 0,
					  this._skb.getRawBytes(), this._offset + NAK_SRC_NLA_OFFSET,
					  SIZEOF_INADDR);
		} else if (Inet6Address.class.isInstance (nak_src_nla)) {
			setNakSourceNlaAfi (Packet.AFI_IP6);
			System.arraycopy (nak_src_nla.getAddress(), 0,
					  this._skb.getRawBytes(), this._offset + NAK_SRC_NLA_OFFSET,
					  SIZEOF_INADDR6);
		}
	}

	public final int getNakGroupNlaAfi() {
		switch (getNakSourceNlaAfi()) {
		case Packet.AFI_IP:
			return this._skb.getUnsignedShort (this._offset + NAK_GRP_NLA_AFI_OFFSET);
		case Packet.AFI_IP6:
			return this._skb.getUnsignedShort (this._offset + NAK6_GRP_NLA_AFI_OFFSET);
		default:
			return 0;
		}
	}

	private void setNakGroupNlaAfi (int afi) {
		switch (getNakSourceNlaAfi()) {
		case Packet.AFI_IP:
			this._skb.setUnsignedShort (this._offset + NAK_GRP_NLA_AFI_OFFSET, afi);
			break;
		case Packet.AFI_IP6:
			this._skb.setUnsignedShort (this._offset + NAK6_GRP_NLA_AFI_OFFSET, afi);
			break;
		default:
			break;
		}
	}

	public final InetAddress getNakGroupNla() {
		InetAddress nak_grp_nla = null;
		int nak_grp_nla_offset;
		switch (getNakSourceNlaAfi()) {
		case Packet.AFI_IP:
			nak_grp_nla_offset = NAK_GRP_NLA_OFFSET;
			break;
		case Packet.AFI_IP6:
			nak_grp_nla_offset = NAK6_GRP_NLA_OFFSET;
			break;
		default:
			return nak_grp_nla;
		}
		try {
			switch (getNakGroupNlaAfi()) {
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
		return nak_grp_nla;
	}

	public void setNakGroupNla (InetAddress nak_grp_nla) {
		int nak_grp_nla_offset;
		switch (getNakSourceNlaAfi()) {
		case Packet.AFI_IP:
			nak_grp_nla_offset = NAK_GRP_NLA_OFFSET;
			break;
		case Packet.AFI_IP6:
			nak_grp_nla_offset = NAK6_GRP_NLA_OFFSET;
			break;
		default:
			return;
		}
		if (Inet4Address.class.isInstance (nak_grp_nla)) {
			setNakGroupNlaAfi (Packet.AFI_IP);
			System.arraycopy (nak_grp_nla.getAddress(), 0,
					  this._skb.getRawBytes(), this._offset + nak_grp_nla_offset,
					  SIZEOF_INADDR);
		} else if (Inet6Address.class.isInstance (nak_grp_nla)) {
			setNakGroupNlaAfi (Packet.AFI_IP6);
			System.arraycopy (nak_grp_nla.getAddress(), 0,
					  this._skb.getRawBytes(), this._offset + nak_grp_nla_offset,
					  SIZEOF_INADDR6);
		}
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
		       ", \"nakSqn\": " + getNakSequenceNumber() +
		       ", \"nakSrcNlaAfi\": " + getNakSourceNlaAfi() +
		       ", \"nakSrcNla\": " + getNakSourceNla() +
		       ", \"nakGrpNlaAfi\": " + getNakGroupNlaAfi() +
		       ", \"nakGrpNla\": " + getNakGroupNla() +
		        "}";
	}
}

/* eof */
