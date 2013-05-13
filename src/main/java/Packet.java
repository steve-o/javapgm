/* Abstract base class for parsing PGM packet formats.
 */

public class Packet {

	public static final int AFI_IP	= 1;
	public static final int AFI_IP6	= 2;

	public static final int SIZEOF_INADDR		= 4;
	public static final int SIZEOF_INADDR6		= 16;
	public static final int SIZEOF_PGM_HEADER	= 16;

	public static final int PGM_SPM			= 0x00;
	public static final int PGM_POLL		= 0x01;
	public static final int PGM_POLR		= 0x02;
	public static final int PGM_ODATA		= 0x04;
	public static final int PGM_RDATA		= 0x05;
	public static final int PGM_NAK			= 0x08;
	public static final int PGM_NNAK		= 0x09;
	public static final int PGM_NCF			= 0x0a;
	public static final int PGM_SPMR		= 0x0c;
	public static final int PGM_MAX			= 0xff;

	public static final int PGM_OPT_PARITY		= 0x80;
	public static final int PGM_OPT_VAR_PKTLEN	= 0x40;
	public static final int PGM_OP_ENCODED		= 0x08;
	public static final int PGM_OPT_NETWORK		= 0x02;
	public static final int PGM_OPT_PRESENT		= 0x01;

	public static final int PGM_SPORT_OFFSET		= 0;
	public static final int PGM_DPORT_OFFSET		= 2;
	public static final int PGM_TYPE_OFFSET			= 4;
	public static final int PGM_OPTIONS_OFFSET		= 5;
	public static final int PGM_CHECKSUM_OFFSET		= 6;
	public static final int PGM_GSI_OFFSET			= 8;
	public static final int PGM_TSDU_LENGTH_OFFSET	= 14;
	public static final int PGM_TYPE_DATA_OFFSET	= SIZEOF_PGM_HEADER;

	public static boolean parseUdpEncapsulated (SocketBuffer skb) {
		if (skb.getRawBytes().length < SIZEOF_PGM_HEADER) {
			System.out.println ("UDP payload too small for PGM packet at " + skb.getRawBytes().length + " bytes, expecting at least " + SIZEOF_PGM_HEADER + " bytes.");
			return false;
		}

		skb.setHeaderOffset (skb.getDataOffset());
		return parse (skb);
	}

	private static boolean parse (SocketBuffer skb) {
		if (skb.getHeader().hasChecksum()) {
			final int source_checksum = skb.getHeader().getChecksum();
			skb.getHeader().clearChecksum();
			final int calculated_checksum = doChecksum (skb.getRawBytes());
			if (source_checksum != calculated_checksum) {
				System.out.println ("PGM packet checksum mismatch, reported 0x" + String.format ("%x", source_checksum) + " whilst calculated 0x" + String.format ("%x", calculated_checksum) + ".");
				return false;
			}
		} else {
			if (PGM_ODATA == skb.getHeader().getType()) {
				System.out.println ("PGM checksum missing whilst mandatory for ODATA packets.");
				return false;
			}
			if (PGM_RDATA == skb.getHeader().getType()) {
				System.out.println ("PGM checksum missing whilst mandatory for RDATA packets.");
				return false;
			}
			System.out.println ("No PGM checksum.");
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