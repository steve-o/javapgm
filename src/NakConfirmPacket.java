import java.io.*;
import java.net.*;
import java.util.*;

public class NakConfirmPacket extends PgmPacket {

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

	private int _ncf_offset = 0;

	public NakConfirmPacket (SocketBuffer skb) {
		super (skb);
		this._ncf_offset = this._offset + PGM_TYPE_DATA_OFFSET;
	}

	public static final NakConfirmPacket decode (SocketBuffer skb) {
		return new NakConfirmPacket (skb);
	}

	public final long getSequenceNumber() {
		return this.getUnsignedInt (this._ncf_offset + NCF_SQN_OFFSET);
	}

	public final int getSourcePathAddressFamilyIndicator() {
		return this.getUnsignedShort (this._ncf_offset + NCF_SRC_NLA_AFI_OFFSET);
	}

	public final String getSourcePath() {
		InetAddress ncf_src_nla = null;
		try {
			switch (this.getSourcePathAddressFamilyIndicator()) {
			case AFI_IP:
				byte[] in_addr = new byte[ SIZEOF_INADDR ];
				System.arraycopy (this._buf, this._ncf_offset + NCF_SRC_NLA_OFFSET,
						  in_addr, 0,
						  in_addr.length);
				ncf_src_nla = Inet4Address.getByAddress (in_addr);
				break;
			case AFI_IP6:
				byte[] in6_addr = new byte[ SIZEOF_INADDR6 ];
				System.arraycopy (this._buf, this._ncf_offset + NCF_SRC_NLA_OFFSET,
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
		switch (this.getSourcePathAddressFamilyIndicator()) {
		case AFI_IP:
			return this.getUnsignedShort (this._ncf_offset + NCF_GRP_NLA_AFI_OFFSET);
		case AFI_IP6:
			return this.getUnsignedShort (this._ncf_offset + NCF6_GRP_NLA_AFI_OFFSET);
		default:
			return 0;
		}
	}

	public final String getGroupPath() {
		InetAddress ncf_grp_nla = null;
		int ncf_grp_nla_offset;
		switch (this.getSourcePathAddressFamilyIndicator()) {
		case AFI_IP:
			ncf_grp_nla_offset = NCF_GRP_NLA_OFFSET;
			break;
		case AFI_IP6:
			ncf_grp_nla_offset = NCF6_GRP_NLA_OFFSET;
			break;
		default:
			return "invalid";
		}
		try {
			switch (this.getGroupPathAddressFamilyIndicator()) {
			case AFI_IP:
				byte[] in_addr = new byte[ SIZEOF_INADDR ];
				System.arraycopy(this._buf, ncf_grp_nla_offset,
						 in_addr, 0,
						 in_addr.length);
				ncf_grp_nla = Inet4Address.getByAddress (in_addr);
				break;
			case AFI_IP6:
				byte[] in6_addr = new byte[ SIZEOF_INADDR6 ];
				System.arraycopy(this._buf, ncf_grp_nla_offset,
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

	public String toString() {
		GlobalSourceId gsi = this.getGlobalSourceId ();
		return  "{" +
			"sourcePort:" + this.getSourcePort() +
		       " destinationPort:" + this.getDestinationPort() +
		       " type:" + this.getTypeName (this.getType()) +
		       " options:" + this.getOptions() +
		       " checksum:0x" + Integer.toHexString(this.getChecksum()) +
		       " gsi:" + gsi +
		       " tsduLength:" + this.getTsduLength() +
		       " ncfSqn:" + this.getSequenceNumber() +
		       " ncfSrcNlaAfi:" + this.getSourcePathAddressFamilyIndicator() +
		       " ncfSrcNla:" + this.getSourcePath() +
		       " ncfGrpNlaAfi:" + this.getGroupPathAddressFamilyIndicator() +
		       " ncfGrpNla:" + this.getGroupPath() +
		        "}\n";
	}

}
