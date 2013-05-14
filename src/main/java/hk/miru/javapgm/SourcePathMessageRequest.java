/* SPM-request or SPMR packet.  Used for early access to source NLA.
 */
package hk.miru.javapgm;

public class SourcePathMessageRequest {

	protected SocketBuffer	_skb = null;
	protected int		_offset = 0;

	public SourcePathMessageRequest (SocketBuffer skb, int offset) {
		this._skb = skb;
		this._offset = offset;
	}

	public static SocketBuffer create() {
		SocketBuffer skb = new SocketBuffer (Packet.SIZEOF_PGM_HEADER);
		skb.setHeaderOffset (0);
		skb.getHeader().setType (Packet.PGM_SPMR);
		skb.pull (Packet.SIZEOF_PGM_HEADER);
		return skb;
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
		        "}\n";
	}
}

/* eof */