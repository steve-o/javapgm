import java.io.*;
import java.net.*;
import java.util.*;

public class SourcePathMessagePacket extends PgmPacket {

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

	private int _spm_offset = 0;
	private int _spm_options_offset = 0;

	public SourcePathMessagePacket (SocketBuffer skb) {
		super (skb);
		this._spm_offset = this._offset + PGM_TYPE_DATA_OFFSET;
	}

	public static final SourcePathMessagePacket decode (SocketBuffer skb) {
		return new SourcePathMessagePacket (skb);
	}

	public final long getSequenceNumber() {
		return this.getUnsignedInt (this._spm_offset + SPM_SQN_OFFSET);
	}

	public final long getTrail() {
		return this.getUnsignedInt (this._spm_offset + SPM_TRAIL_OFFSET);
	}

	public final long getLead() {
		return this.getUnsignedInt (this._spm_offset + SPM_LEAD_OFFSET);
	}

	public final int getPathAddressFamilyIndicator() {
		return this.getUnsignedShort (this._spm_offset + SPM_NLA_AFI_OFFSET);
	}

	public final String getPath() {
		InetAddress spm_nla = null;
		try {
			switch (this.getPathAddressFamilyIndicator()) {
			case AFI_IP:
				byte[] in_addr = new byte[ SIZEOF_INADDR ];
				System.arraycopy (this._buf, this._spm_offset + SPM_NLA_OFFSET, in_addr, 0, in_addr.length);
				spm_nla = Inet4Address.getByAddress (in_addr);
				break;
			case AFI_IP6:
				byte[] in6_addr = new byte[ SIZEOF_INADDR6 ];
				System.arraycopy (this._buf, this._spm_offset + SPM_NLA_OFFSET, in6_addr, 0, in6_addr.length);
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
		GlobalSourceId gsi = this.getGlobalSourceId ();
		return  "{" +
			 "\"sourcePort\": " + this.getSourcePort() +
		       ", \"destinationPort\": " + this.getDestinationPort() +
		       ", \"type\": " + this.getTypeName (this.getType()) +
		       ", \"options\": " + this.getOptions() +
		       ", \"checksum\": 0x" + Integer.toHexString(this.getChecksum()) +
		       ", \"gsi\": " + gsi +
		       ", \"tsduLength\": " + this.getTsduLength() +
		       ", \"spmSqn\": " + this.getSequenceNumber() +
		       ", \"spmTrail\": " + this.getTrail() +
		       ", \"spmLead\": " + this.getLead() +
		       ", \"spmNlaAfi\": " + this.getPathAddressFamilyIndicator() +
		       ", \"spmNla\": " + this.getPath() +
		        "}\n";
	}

}
