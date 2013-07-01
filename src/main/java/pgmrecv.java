/* Example subscription to a PGM stream.
 */ 

import hk.miru.javapgm.SocketBuffer;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class pgmrecv {   
/* Windows does not have convenient adapter names. */
	String interfaceName = "10.67.4.75";
//	String interfaceName = "10.0.9.30";
//	String interfaceName = "eth0";
	String multicastGroup = "239.192.0.1";
	int udpEncapsulationPort = 3056;
	int dataDestinationPort = 7500;
	int max_tpdu = 1500;
	int rxw_sqns = 128;
	int rxw_secs = 0;
	int peerExpiration = 300 * 1000 * 1000;
        int spmrExpiration = 250;
	int nak_bo_ivl = 50;
	int nak_rpt_ivl = 2 * 1000 * 1000;
        int nak_rdata_ivl = 2 * 1000 * 1000;
	int nak_data_retries = 50;
	int nak_ncf_retries = 50;
        int multicastHops = 16;

        hk.miru.javapgm.Socket sock = null;

	public pgmrecv (String[] args) throws IOException, NoSuchAlgorithmException
	{               
		InetAddress group = InetAddress.getByName (this.multicastGroup);
		ProtocolFamily pf = group instanceof Inet4Address ? StandardProtocolFamily.INET : StandardProtocolFamily.INET6;                
                this.sock = new hk.miru.javapgm.Socket (pf);

                System.out.println ("Create PGM/UDP socket.");
                this.sock.setOption (hk.miru.javapgm.SocketOptions.PGM_UDP_ENCAP_UCAST_PORT, new Integer (this.udpEncapsulationPort));
                this.sock.setOption (hk.miru.javapgm.SocketOptions.PGM_UDP_ENCAP_MCAST_PORT, new Integer (this.udpEncapsulationPort));
                
/* Set PGM parameters */                
                this.sock.setOption (hk.miru.javapgm.SocketOptions.PGM_RECV_ONLY, new Boolean (true));
                this.sock.setOption (hk.miru.javapgm.SocketOptions.PGM_PASSIVE, new Boolean (false));
                this.sock.setOption (hk.miru.javapgm.SocketOptions.PGM_MTU, new Integer (this.max_tpdu));
                this.sock.setOption (hk.miru.javapgm.SocketOptions.PGM_RXW_SQNS, new Integer (this.rxw_sqns));
                this.sock.setOption (hk.miru.javapgm.SocketOptions.PGM_PEER_EXPIRY, new Integer (this.peerExpiration));
                this.sock.setOption (hk.miru.javapgm.SocketOptions.PGM_SPMR_EXPIRY, new Integer (this.spmrExpiration));
                this.sock.setOption (hk.miru.javapgm.SocketOptions.PGM_NAK_BO_IVL, new Integer (this.nak_bo_ivl));
                this.sock.setOption (hk.miru.javapgm.SocketOptions.PGM_NAK_RPT_IVL, new Integer (this.nak_rpt_ivl));
                this.sock.setOption (hk.miru.javapgm.SocketOptions.PGM_NAK_RDATA_IVL, new Integer (this.nak_rdata_ivl));
                this.sock.setOption (hk.miru.javapgm.SocketOptions.PGM_NAK_DATA_RETRIES, new Integer (this.nak_data_retries));
                this.sock.setOption (hk.miru.javapgm.SocketOptions.PGM_NAK_NCF_RETRIES, new Integer (this.nak_ncf_retries));
                
/* Create global session identifier */                
                hk.miru.javapgm.GlobalSourceId gsi = new hk.miru.javapgm.GlobalSourceId();
                hk.miru.javapgm.SocketAddress sockaddr = new hk.miru.javapgm.SocketAddress (gsi, this.dataDestinationPort);
         
/* Assign socket to specific address */
		NetworkInterface ni = NetworkInterface.getByName (this.interfaceName);
		if (null == ni) ni = NetworkInterface.getByInetAddress (InetAddress.getByName (this.interfaceName));                
                int scope_id = 0;
                if (pf == StandardProtocolFamily.INET6) {
                        scope_id = ((Inet6Address)group).getScopeId();
                }
                hk.miru.javapgm.InterfaceRequest if_req = new hk.miru.javapgm.InterfaceRequest (ni.getIndex(), scope_id);
                if (!this.sock.bind (sockaddr, if_req, if_req)) {
                        System.err.println ("Binding PGM socket failed.");
                        return;
                }
                
/* Join IP multicast groups */
                hk.miru.javapgm.GroupRequest gr = new hk.miru.javapgm.GroupRequest (ni.getIndex(), group);
                this.sock.setOption (hk.miru.javapgm.SocketOptions.PGM_JOIN_GROUP, gr);
                this.sock.setOption (hk.miru.javapgm.SocketOptions.PGM_SEND_GROUP, gr);
                
/* Set IP parameters */
                this.sock.setOption (hk.miru.javapgm.SocketOptions.PGM_MULTICAST_LOOP, new Boolean (false));
                this.sock.setOption (hk.miru.javapgm.SocketOptions.PGM_MULTICAST_HOPS, new Integer (this.multicastHops));
                
                if (!this.sock.connect()) {
                        System.err.println ("Connecting PGM socket failed.");
                        return;
                }

		Runtime.getRuntime().addShutdownHook (new Thread() {
			@Override
			public void run() {
				System.out.println ("fin.");
			}
		});

		Selector selector = Selector.open();
                SelectionKey sk = this.sock.register (selector, SelectionKey.OP_READ);
                long timeout = 0;
		while (true) {
                        final List<SocketBuffer> skbs = new ArrayList<>();
			final hk.miru.javapgm.Socket.IoStatus status = this.sock.receive (skbs);
			switch (status) {
			case IO_STATUS_NORMAL:
                                on_data (skbs);
				break;
			case IO_STATUS_TIMER_PENDING:
                                timeout = this.sock.getTimeRemain();
/* Workaround lack of functional goto in Java */                                    
			case IO_STATUS_RATE_LIMITED:
                                if (hk.miru.javapgm.Socket.IoStatus.IO_STATUS_RATE_LIMITED == status) {
                                        timeout = this.sock.getRateRemain();
                                }
			case IO_STATUS_WOULD_BLOCK:
                                if (hk.miru.javapgm.Socket.IoStatus.IO_STATUS_WOULD_BLOCK == status) {
                                        timeout = 0;
                                }
                                if (selector.select (timeout) > 0) {
                                        selector.selectedKeys().clear();
                                }
                                break;
			default:
				System.err.format ("unhandled return state: %s%n", status);
				break;
			}
		}
	}

	private void on_data (List<SocketBuffer> skbs) {
		System.out.format ("Received %d SKB%s%n", skbs.size(), skbs.size() > 1 ? "s" : "");
		int i = 1;
		for (SocketBuffer skb : skbs) {
			System.out.format ("#%d from %s: \"%s\"%n",
                                   i++, skb.getTransportSessionId(), new String (skb.getRawBytes(), skb.getDataOffset(), skb.getLength()));
		}
	}

	public static void main (String[] args) throws IOException, NoSuchAlgorithmException
	{
		new pgmrecv (args);
	}
}

/* eof */