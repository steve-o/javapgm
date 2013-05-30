/* Negative acknowledgement or NAK packet.  Sent by receiver to source to re-
 * request delivery of a lost or corrupt packet.
 */
package hk.miru.javapgm;

import static hk.miru.javapgm.Preconditions.checkArgument;
import static hk.miru.javapgm.Preconditions.checkNotNull;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@SuppressWarnings("unused")
public class Nak {
        private static Logger LOG = LogManager.getLogger (Nak.class.getName());
    
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
	private static final int SIZEOF_PGM_NAK6		= 44;
	private static final int SIZEOF_PGM_OPT_LENGTH		= 4;
	private static final int SIZEOF_PGM_OPT_HEADER		= 3;
	private static final int SIZEOF_PGM_OPT_RESERVED	= 1;
	private static final int SIZEOF_PGM_SQN			= 4;

	public Nak (SocketBuffer skb, int offset) {
                checkNotNull (skb);
		this._skb = skb;
		this._offset = offset;
	}

	public static SocketBuffer create (InetAddress nak_src_nla, InetAddress nak_grp_nla, int count) {
                checkNotNull (nak_src_nla);
                checkNotNull (nak_grp_nla);
                checkArgument (count > 0 && count <= 63);
		int tpdu_length = Packet.SIZEOF_PGM_HEADER + SIZEOF_PGM_NAK;
		if (Inet6Address.class.isInstance (nak_src_nla))
			tpdu_length += SIZEOF_PGM_NAK6 - SIZEOF_PGM_NAK;
		if (count > 1)
			tpdu_length += SIZEOF_PGM_OPT_LENGTH +
				       SIZEOF_PGM_OPT_HEADER +
				       SIZEOF_PGM_OPT_RESERVED +
				       ( (count-1) * SIZEOF_PGM_SQN );
		SocketBuffer skb = new SocketBuffer (tpdu_length);
		skb.setHeaderOffset (0);
		skb.getHeader().setType (Packet.PGM_NAK);
		skb.reserve (Packet.SIZEOF_PGM_HEADER);
		return skb;
	}

	public static SocketBuffer create (InetAddress nak_src_nla, InetAddress nak_grp_nla) {
		return create (nak_src_nla, nak_grp_nla, 1);
	}

	public final SequenceNumber getNakSequenceNumber() {
		return SequenceNumber.fromIntBits (this._skb.getSignedInt (this._offset + NAK_SQN_OFFSET));
	}

	public void setNakSequenceNumber (SequenceNumber nak_sqn) {
                checkNotNull (nak_sqn);
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
                checkNotNull (nak_src_nla);
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
                checkNotNull (nak_grp_nla);
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

/* TODO: TLC wanted */
        public SequenceNumber[] getNakListOption() {
                SequenceNumber[] sqn_list = null;
		int opt_nak_list_offset;
		switch (getNakSourceNlaAfi()) {
		case Packet.AFI_IP:
			opt_nak_list_offset = NAK_OPTIONS_OFFSET;
			break;
		case Packet.AFI_IP6:
			opt_nak_list_offset = NAK6_OPTIONS_OFFSET;
			break;
		default:
			return sqn_list;
		}
                OptionHeader optHeader = new OptionHeader (this._skb, this._offset + opt_nak_list_offset);
                if (!optHeader.isLengthOption()) {
                        LOG.info ("Malformed NAK rejected on unexpected primary PGM option type.");
                        return sqn_list;
                }
                OptionLength optLength = new OptionLength (this._skb, optHeader.getOffset());
                OptionNakList optNakList = null;
                do {
                        optHeader = new OptionHeader (this._skb, optHeader.getOffset() + optHeader.getLength());
                        if (optHeader.isNakListOption()) {
                                optNakList = new OptionNakList (this._skb, optHeader.getOffset(), optHeader.getLength());
                                break;
                        }
                } while (!optHeader.isLastOption());
                if (null != optNakList) {
                        sqn_list = new SequenceNumber[optNakList.getSequenceCount()];
            		for (int i = 0; i < sqn_list.length; i++)
                                sqn_list[i] = optNakList.getOptionSequence (i);
                }
                return sqn_list;
        }
        
	public void setNakListOption (SequenceNumber[] sqn_list) {
                checkArgument (sqn_list.length > 0 && sqn_list.length <= 62);
		int opt_nak_list_offset;
		switch (getNakSourceNlaAfi()) {
		case Packet.AFI_IP:
			opt_nak_list_offset = NAK_OPTIONS_OFFSET;
			break;
		case Packet.AFI_IP6:
			opt_nak_list_offset = NAK6_OPTIONS_OFFSET;
			break;
		default:
			return;
		}
		OptionLength optLength = OptionLength.create (this._skb, this._offset + opt_nak_list_offset);
		OptionNakList optNakList = OptionNakList.create (this._skb, optLength.getOffset() + optLength.getLength(), sqn_list.length);
		for (int i = 0; i < sqn_list.length; i++)
			optNakList.setOptionSequence (i, sqn_list[i]);
		new OptionHeader (this._skb, optNakList.getOffset()).setLastOption();
		optLength.setTotalLength (optLength.getLength() + optNakList.getLength());
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
		       ", \"nakSqn\": " + getNakSequenceNumber() +
		       ", \"nakSrcNlaAfi\": " + getNakSourceNlaAfi() +
		       ", \"nakSrcNla\": " + getNakSourceNla() +
		       ", \"nakGrpNlaAfi\": " + getNakGroupNlaAfi() +
		       ", \"nakGrpNla\": " + getNakGroupNla() +
		        "}";
	}
}

/* eof */