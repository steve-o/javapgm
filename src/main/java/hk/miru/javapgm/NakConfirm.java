/* NAK confirm packet or NCF is sent on immediate receipt of a NAK to suppress
 * other peer subscribers from sending their own NAK.
 */
package hk.miru.javapgm;

import java.net.InetAddress;

public class NakConfirm extends Nak {

        public NakConfirm (SocketBuffer skb, int offset) {
                super (skb, offset);
        }
        
        public static SocketBuffer create (InetAddress nak_src_nla, InetAddress nak_grp_nla, int count) {
                SocketBuffer skb = Nak.create(nak_src_nla, nak_grp_nla, count);
                skb.getHeader().setType (Packet.PGM_NCF);
                return skb;
        }

	public static SocketBuffer create (InetAddress nak_src_nla, InetAddress nak_grp_nla) {
		return create (nak_src_nla, nak_grp_nla, 1);
	}        
}

/* eof */