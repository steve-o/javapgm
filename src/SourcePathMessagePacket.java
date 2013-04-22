/* Source path message or SPM packet.  Used for defining and keeping multicast
 * circuit state.
 */
import java.io.*;
import java.net.*;
import java.util.*;

public class SourcePathMessagePacket {

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

	private static final int SIZEOF_INADDR		= 4;
	private static final int SIZEOF_INADDR6		= 16;

	public SourcePathMessagePacket (SocketBuffer skb, int offset) {
		this._skb = skb;
		this._offset = offset;
	}

	public final long getSequenceNumber() {
		return this._skb.getUnsignedInt (this._offset + SPM_SQN_OFFSET);
	}

	public final long getTrail() {
		return this._skb.getUnsignedInt (this._offset + SPM_TRAIL_OFFSET);
	}

	public final long getLead() {
		return this._skb.getUnsignedInt (this._offset + SPM_LEAD_OFFSET);
	}

	public final int getPathAddressFamilyIndicator() {
		return this._skb.getUnsignedShort (this._offset + SPM_NLA_AFI_OFFSET);
	}

	public final String getPath() {
		InetAddress spm_nla = null;
		try {
			switch (this.getPathAddressFamilyIndicator()) {
			case Packet.AFI_IP:
				byte[] in_addr = new byte[SIZEOF_INADDR];
				System.arraycopy (this._skb.getRawBytes(), this._offset + SPM_NLA_OFFSET,
						  in_addr, 0,
						  in_addr.length);
				spm_nla = Inet4Address.getByAddress (in_addr);
				break;
			case Packet.AFI_IP6:
				byte[] in6_addr = new byte[SIZEOF_INADDR6];
				System.arraycopy (this._skb.getRawBytes(), this._offset + SPM_NLA_OFFSET,
						  in6_addr, 0,
						  in6_addr.length);
				spm_nla = Inet6Address.getByAddress (in6_addr);
				break;
			default:
				break;
			}
		} catch (UnknownHostException e) {}
		if (null == spm_nla)
			return "unknown";
		else
			return spm_nla.toString();
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
		       ", \"spmSqn\": " + this.getSequenceNumber() +
		       ", \"spmTrail\": " + this.getTrail() +
		       ", \"spmLead\": " + this.getLead() +
		       ", \"spmNlaAfi\": " + this.getPathAddressFamilyIndicator() +
		       ", \"spmNla\": \"" + this.getPath() + "\"" +
		        "}\n";
	}
}

/* eof */
