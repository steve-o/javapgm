/* Repair data or RDATA packet. 
 */
package hk.miru.javapgm;

public class RepairData extends OriginalData {

	public RepairData (SocketBuffer skb, int offset) {
                super (skb, offset);
		skb.getHeader().setType (Packet.PGM_RDATA);
	}        
}

/* eof */