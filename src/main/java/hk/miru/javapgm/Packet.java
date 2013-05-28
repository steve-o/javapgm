/* Abstract base class for parsing PGM packet formats.
 */
package hk.miru.javapgm;

import static hk.miru.javapgm.Preconditions.checkNotNull;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;

public class Packet {

        private static Logger LOG = LogManager.getLogger (Packet.class.getName());
    
/* address family indicator, rfc 1700 (ADDRESS FAMILY NUMBERS) */        
	public static final int AFI_IP	= 1;    /* IP (IP version 4) */
	public static final int AFI_IP6	= 2;    /* IP6 (IP version 6) */

/* UDP ports for UDP encapsulation, as per IBM WebSphere MQ */
	public static final int DEFAULT_UDP_ENCAP_UCAST_PORT	= 3055;
	public static final int DEFAULT_UDP_ENCAP_MCAST_PORT	= 3056;       
        
/* PGM default ports */        
	public static final int DEFAULT_DATA_DESTINATION_PORT	= 7500;
	public static final int DEFAULT_DATA_SOURCE_PORT	= 0;
        
/* DoS limitation to protocol (MS08-036, KB950762) */        
        public static final int PGM_MAX_APDU            = 65535;
        
/* Cisco default: 24 (max 8200), Juniper & H3C default: 16, SmartPGM: 64 */        
        public static final int PGM_MAX_FRAGMENTS       = 16;
        
	public static final int SIZEOF_INADDR		= 4;
	public static final int SIZEOF_INADDR6		= 16;
	public static final int SIZEOF_IP_HEADER	= 20;
	public static final int SIZEOF_IP6_HEADER	= 40;
	public static final int SIZEOF_PGM_HEADER	= 16;
        public static final int SIZEOF_PGM_DATA         = 8;
        public static final int SIZEOF_PGM_OPT_HEADER	= 3;
        public static final int SIZEOF_PGM_OPT_LENGTH	= 4;
        public static final int SIZEOF_PGM_OPT_FRAGMENT	= 13;
        public static final int SIZEOF_PGM_OPT_FIN      = 1;
        public static final int SIZEOF_PGM_OPT_PGMCC_DATA	= 13;
        public static final int SIZEOF_PGM_OPT6_PGMCC_DATA	= 25;

	public static final int PGM_SPM			= 0x00; /* 8.1: source path message */
	public static final int PGM_POLL		= 0x01; /* 14.7.1: poll request */
	public static final int PGM_POLR		= 0x02; /* 14.7.2: poll response */
	public static final int PGM_ODATA		= 0x04; /* 8.2: original data */
	public static final int PGM_RDATA		= 0x05; /* 8.2: repair data */
	public static final int PGM_NAK			= 0x08; /* 8.3: NAK or negative acknowledgement */
	public static final int PGM_NNAK		= 0x09; /* 8.3: N-NAK or null negative acknowledgement */
	public static final int PGM_NCF			= 0x0a; /* 8.3: NCF or NAK confirmation */
	public static final int PGM_SPMR		= 0x0c; /* 13.6: SPM request */
        public static final int PGM_ACK                 = 0x0d; /* PGMCC: congestion control ACK */
	public static final int PGM_MAX			= 0xff; 

	public static final int PGM_OPT_LENGTH		= 0x00; /* options length */
	public static final int PGM_OPT_FRAGMENT	= 0x01; /* fragmentation */
	public static final int PGM_OPT_NAK_LIST	= 0x02; /* list of nak entries */
	public static final int PGM_OPT_JOIN		= 0x03; /* late joining */
	public static final int PGM_OPT_REDIRECT	= 0x07; /* redirect */
	public static final int PGM_OPT_SYN		= 0x0d; /* synchronisation */
	public static final int PGM_OPT_FIN		= 0x0e; /* session end */
	public static final int PGM_OPT_RST		= 0x0f; /* session reset */
        
        public static final int PGM_OPT_PARITY_PRM	= 0x08; /* forward error correction parameters */
        public static final int PGM_OPT_PARITY_GRP	= 0x09; /*   group number */
        public static final int PGM_OPT_CURR_TGSIZE	= 0x0a; /*   group size */
        
        public static final int PGM_OPT_CR		= 0x10; /* congestion report */
        public static final int PGM_OPT_CRQST		= 0x11; /* congestion report request */
        
        public static final int PGM_OPT_PGMCC_DATA	= 0x12;
        public static final int PGM_OPT_PGMCC_FEEDBACK	= 0x13;
        
        public static final int PGM_OPT_NAK_BO_IVL	= 0x04; /* nak back-off interval */
        public static final int PGM_OPT_NAK_BO_RNG	= 0x05; /* nak back-off range */
        public static final int PGM_OPT_NBR_UNREACH	= 0x0b; /* neighbour unreachable */
        public static final int PGM_OPT_PATH_NLA	= 0x0c; /* path nla */
        
        public static final int PGM_OPT_INVALID		= 0x7f; /* option invalidated */
        
	public static final int PGM_OPT_PARITY		= 0x80;
	public static final int PGM_OPT_VAR_PKTLEN	= 0x40;
	public static final int PGM_OP_ENCODED		= 0x08;
	public static final int PGM_OPT_NETWORK		= 0x02;
	public static final int PGM_OPT_PRESENT		= 0x01;

	public static final int PGM_SPORT_OFFSET	= 0;
	public static final int PGM_DPORT_OFFSET	= 2;
	public static final int PGM_TYPE_OFFSET		= 4;
	public static final int PGM_OPTIONS_OFFSET	= 5;
	public static final int PGM_CHECKSUM_OFFSET	= 6;
	public static final int PGM_GSI_OFFSET		= 8;
	public static final int PGM_TSDU_LENGTH_OFFSET	= 14;
	public static final int PGM_TYPE_DATA_OFFSET	= SIZEOF_PGM_HEADER;

        public static int calculateOffset (boolean canFragment, @Nullable ProtocolFamily pgmcc_family) {
                int data_size = SIZEOF_PGM_HEADER + SIZEOF_PGM_DATA;
                int pkt_size = data_size;
                if (canFragment || (null != pgmcc_family))
                        pkt_size += SIZEOF_PGM_OPT_LENGTH + SIZEOF_PGM_OPT_HEADER;
                if (canFragment)
                        pkt_size += SIZEOF_PGM_OPT_FRAGMENT;
                if (StandardProtocolFamily.INET == pgmcc_family)
                        pkt_size += SIZEOF_PGM_OPT_PGMCC_DATA;
                else if (StandardProtocolFamily.INET6 == pgmcc_family)
                        pkt_size += SIZEOF_PGM_OPT6_PGMCC_DATA;
                return pkt_size;
        }
        
	public static boolean parseUdpEncapsulated (SocketBuffer skb) {
                checkNotNull (skb);
		if (skb.getRawBytes().length < SIZEOF_PGM_HEADER) {
                        LOG.debug ("UDP payload too small for PGM packet at {} bytes, expecting at least {} bytes.", skb.getRawBytes().length, SIZEOF_PGM_HEADER);
			return false;
		}

		skb.setHeaderOffset (skb.getDataOffset());
		return parse (skb);
	}

	private static boolean parse (SocketBuffer skb) {
                checkNotNull (skb);
		if (skb.getHeader().hasChecksum()) {
			final int source_checksum = skb.getHeader().getChecksum();
			skb.getHeader().clearChecksum();
			final int calculated_checksum = doChecksum (skb.getRawBytes());
			if (source_checksum != calculated_checksum) {
                                LOG.debug ("PGM packet checksum mismatch, reported {} whilst calculated {}.", String.format ("%#x", source_checksum), String.format ("%#x", calculated_checksum));
				return false;
			}
		} else {
			if (PGM_ODATA == skb.getHeader().getType()) {
                                LOG.debug ("PGM checksum missing whilst mandatory for ODATA packets.");
				return false;
			}
			if (PGM_RDATA == skb.getHeader().getType()) {
                                LOG.debug ("PGM checksum missing whilst mandatory for RDATA packets.");
				return false;
			}
                        LOG.debug ("No PGM checksum.");
		}

		skb.setTransportSessionId (new TransportSessionId (skb.getHeader().getGlobalSourceId(), skb.getHeader().getSourcePort()));
		return true;
	}

	public static boolean parseOptionExtensions (SocketBuffer skb, int offset) {
		OptionHeader optHeader = new OptionHeader (skb, offset);
		do {
			optHeader = new OptionHeader (skb, optHeader.getOffset() + optHeader.getLength());
			if (optHeader.isFragmentOption()) {
				skb.setFragmentOptionOffset (optHeader.getOffset());
			}
		} while (!optHeader.isLastOption());
		return true;
	}

	public static int doChecksum (byte[] buf) {
		int sum = 0;
		int i = 0, len = buf.length;
		while (len > 1) {
			final int word16 = ((buf[i] & 0xff) << 8) + (buf[i + 1] & 0xff);
			sum += word16;
			len -= 2; i += 2;
		}
/* trailing odd byte */
		if (len > 0)
			sum += buf[i];
/* fold */
		while ((sum >> 16) > 0)
			sum = (sum & 0xffff) + (sum >> 16);
/* 0-value special case of no checksum */
		return (sum == 0xffff) ? (int)sum : (int)(~sum & 0xffff);
	}
}

/* eof */