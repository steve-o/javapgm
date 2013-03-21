/* Null-NAK or NNAK packet.  Feedback by PGM infrastructure after
 * suppressing local network NAKs for adaptive parameter calculation.
 */
import java.io.*;
import java.net.*;
import java.util.*;

public class NullNakPacket extends PgmPacket {

	private static final int NNAK_SQN_OFFSET		= 0;
	private static final int NNAK_SRC_NLA_AFI_OFFSET	= 4;
	private static final int NNAK_RESERVED_OFFSET		= 6;
	private static final int NNAK_SRC_NLA_OFFSET		= 8;
	private static final int NNAK_GRP_NLA_AFI_OFFSET	= 12;
	private static final int NNAK_RESERVED2_OFFSET		= 14;
	private static final int NNAK_GRP_NLA_OFFSET		= 16;
	private static final int NNAK_OPTIONS_OFFSET		= 20;

	private static final int NNAK6_SQN_OFFSET		= 0;
	private static final int NNAK6_SRC_NLA_AFI_OFFSET	= 4;
	private static final int NNAK6_RESERVED_OFFSET		= 6;
	private static final int NNAK6_SRC_NLA_OFFSET		= 8;
	private static final int NNAK6_GRP_NLA_AFI_OFFSET	= 24;
	private static final int NNAK6_RESERVED2_OFFSET		= 26;
	private static final int NNAK6_GRP_NLA_OFFSET		= 28;
	private static final int NNAK6_OPTIONS_OFFSET		= 44;

	private int _nnak_offset = 0;

	public NullNakPacket (SocketBuffer skb) {
		super (skb);
		this._nnak_offset = this._offset + PGM_TYPE_DATA_OFFSET;
	}

	public static final NullNakPacket decode (SocketBuffer skb) {
		return new NullNakPacket (skb);
	}

	public final long getSequenceNumber() {
		return this.getUnsignedInt (this._nnak_offset + NNAK_SQN_OFFSET);
	}

	public final int getSourcePathAddressFamilyIndicator() {
		return this.getUnsignedShort (this._nnak_offset + NNAK_SRC_NLA_AFI_OFFSET);
	}

	public final String getSourcePath() {
		InetAddress nnak_src_nla = null;
		try {
			switch (this.getSourcePathAddressFamilyIndicator()) {
			case AFI_IP:
				byte[] in_addr = new byte[ SIZEOF_INADDR ];
				System.arraycopy (this._buf, this._nnak_offset + NNAK_SRC_NLA_OFFSET, in_addr, 0, in_addr.length);
				nnak_src_nla = Inet4Address.getByAddress (in_addr);
				break;
			case AFI_IP6:
				byte[] in6_addr = new byte[ SIZEOF_INADDR6 ];
				System.arraycopy (this._buf, this._nnak_offset + NNAK_SRC_NLA_OFFSET, in6_addr, 0, in6_addr.length);
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
		switch (this.getSourcePathAddressFamilyIndicator()) {
		case AFI_IP:
			return this.getUnsignedShort (this._nnak_offset + NNAK_GRP_NLA_AFI_OFFSET);
		case AFI_IP6:
			return this.getUnsignedShort (this._nnak_offset + NNAK6_GRP_NLA_AFI_OFFSET);
		default:
			return 0;
		}
	}

	public final String getGroupPath() {
		InetAddress nnak_grp_nla = null;
		int nnak_grp_nla_offset;
		switch (this.getSourcePathAddressFamilyIndicator()) {
		case AFI_IP:
			nnak_grp_nla_offset = NNAK_GRP_NLA_OFFSET;
			break;
		case AFI_IP6:
			nnak_grp_nla_offset = NNAK6_GRP_NLA_OFFSET;
			break;
		default:
			return "invalid";
		}
		try {
			switch (this.getGroupPathAddressFamilyIndicator()) {
			case AFI_IP:
				byte[] in_addr = new byte[ SIZEOF_INADDR ];
				System.arraycopy (this._buf, nnak_grp_nla_offset, in_addr, 0, in_addr.length);
				nnak_grp_nla = Inet4Address.getByAddress (in_addr);
				break;
			case AFI_IP6:
				byte[] in6_addr = new byte[ SIZEOF_INADDR6 ];
				System.arraycopy (this._buf, nnak_grp_nla_offset, in6_addr, 0, in6_addr.length);
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
		GlobalSourceId gsi = this.getGlobalSourceId();
		return  "{" +
			 "\"sourcePort\": " + this.getSourcePort() +
		       ", \"destinationPort\": " + this.getDestinationPort() +
		       ", \"type\": \"" + this.getTypeName (this.getType()) + "\"" +
		       ", \"options\": " + this.getOptions() +
		       ", \"checksum\": 0x" + Integer.toHexString (this.getChecksum()) +
		       ", \"gsi\": \"" + gsi + "\"" +
		       ", \"tsduLength\": " + this.getTsduLength() +
		       ", \"nnakSqn\": " + this.getSequenceNumber() +
		       ", \"nnakSrcNlaAfi\": " + this.getSourcePathAddressFamilyIndicator() +
		       ", \"nnakSrcNla\": " + this.getSourcePath() +
		       ", \"nnakGrpNlaAfi\": " + this.getGroupPathAddressFamilyIndicator() +
		       ", \"nnakGrpNla\": " + this.getGroupPath() +
		        "}\n";
	}
}

/* eof */
