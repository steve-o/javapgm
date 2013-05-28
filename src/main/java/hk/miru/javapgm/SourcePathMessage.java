/* Source path message or SPM packet.  Used for defining and keeping multicast
 * circuit state.
 */
package hk.miru.javapgm;

import static hk.miru.javapgm.Preconditions.checkNotNull;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
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
        private static final int SIZEOF_SPM_HEADER = SPM_OPTIONS_OFFSET;
        private static final int SIZEOF_SPM6_HEADER = SPM6_OPTIONS_OFFSET;

	public SourcePathMessage (SocketBuffer skb, int offset) {
                checkNotNull (skb);
		this._skb = skb;
		this._offset = offset;
	}
        
        public static SocketBuffer create (ProtocolFamily family, int flags) {
                int tpdu_length = Packet.SIZEOF_PGM_HEADER;
                if (StandardProtocolFamily.INET6 == family)
                        tpdu_length += SIZEOF_SPM6_HEADER;
                else
                        tpdu_length += SIZEOF_SPM_HEADER;
                if (Packet.PGM_OPT_FIN == flags)
                {
                        tpdu_length += Packet.SIZEOF_PGM_OPT_LENGTH;
/* End of session */
                        if (Packet.PGM_OPT_FIN == flags)
                                tpdu_length += Packet.SIZEOF_PGM_OPT_HEADER + Packet.SIZEOF_PGM_OPT_FIN;
                }
                SocketBuffer skb = new SocketBuffer (tpdu_length);
		skb.setHeaderOffset (0);
		skb.getHeader().setType (Packet.PGM_SPM);
		skb.reserve (Packet.SIZEOF_PGM_HEADER);
                return skb;
        }

	public final SequenceNumber getSpmSequenceNumber() {
		return SequenceNumber.fromIntBits (this._skb.getSignedInt (this._offset + SPM_SQN_OFFSET));
	}

	public void setSpmSequenceNumber (SequenceNumber spm_sqn) {
                checkNotNull (spm_sqn);
		this._skb.setUnsignedInt (this._offset + SPM_SQN_OFFSET, spm_sqn.longValue());
	}
        
	public final SequenceNumber getSpmTrail() {
		return SequenceNumber.fromIntBits (this._skb.getSignedInt (this._offset + SPM_TRAIL_OFFSET));
	}

	public void setSpmTrail (SequenceNumber spm_trail) {
                checkNotNull (spm_trail);
		this._skb.setUnsignedInt (this._offset + SPM_TRAIL_OFFSET, spm_trail.longValue());
	}
        
	public final SequenceNumber getSpmLead() {
		return SequenceNumber.fromIntBits (this._skb.getSignedInt (this._offset + SPM_LEAD_OFFSET));
	}

	public void setSpmLead (SequenceNumber spm_lead) {
                checkNotNull (spm_lead);
		this._skb.setUnsignedInt (this._offset + SPM_LEAD_OFFSET, spm_lead.longValue());
	}
        
	public final int getSpmNlaAfi() {
		return this._skb.getUnsignedShort (this._offset + SPM_NLA_AFI_OFFSET);
	}
        
        public void setSpmNlaAfi (int afi) {
                this._skb.setUnsignedShort (this._offset + SPM_NLA_AFI_OFFSET, afi);
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
        
        public void setSpmNla (InetAddress spm_nla) {
                checkNotNull (spm_nla);
                if (Inet4Address.class.isInstance (spm_nla)) {
                        setSpmNlaAfi (Packet.AFI_IP);
                        System.arraycopy (spm_nla.getAddress(), 0,
					  this._skb.getRawBytes(), this._offset + SPM_NLA_OFFSET,
					  SIZEOF_INADDR);
                } else if (Inet6Address.class.isInstance (spm_nla)) {
			setSpmNlaAfi (Packet.AFI_IP6);
			System.arraycopy (spm_nla.getAddress(), 0,
					  this._skb.getRawBytes(), this._offset + SPM_NLA_OFFSET,
					  SIZEOF_INADDR6);
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