/* Daytime broadcast service.  A long running PGM send-only socket with
 * reliability.  Periodically broadcasts a timestamp.
 */ 

import static hk.miru.javapgm.Socket.IoStatus.IO_STATUS_RATE_LIMITED;
import static hk.miru.javapgm.Socket.IoStatus.IO_STATUS_TIMER_PENDING;
import static hk.miru.javapgm.Socket.IoStatus.IO_STATUS_WOULD_BLOCK;
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
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class daytime {   
/* Windows does not have convenient adapter names. */
	String interfaceName = "10.67.4.75";
//	String interfaceName = "10.0.9.30";
//	String interfaceName = "eth0";
	String multicastGroup = "239.192.0.1";
	int udpEncapsulationPort = 3056;
	int dataDestinationPort = 7500;
	int max_tpdu = 1500;
        int txw_max_rte = 400 * 1000;       /* Very conservative rate, 2.5mb/s */
	int txw_sqns = 128;
	int txw_secs = 0;
        int ambientSpm = 30 * 1000;
        List<Integer> heartbeatSpm = Arrays.asList (new Integer[]{100, 100, 100, 100, 1300, 7 * 1000, 16 * 1000, 25 * 1000, 30 * 1000});
        int multicastHops = 16;
        DateFormat TIME_FORMAT = DateFormat.getDateTimeInstance (DateFormat.FULL, DateFormat.LONG);

        hk.miru.javapgm.Socket sock = null;
        boolean isTerminated = false;

	public daytime (String[] args) throws IOException, NoSuchAlgorithmException, InterruptedException
	{               
		InetAddress group = InetAddress.getByName (this.multicastGroup);
		ProtocolFamily pf = group instanceof Inet4Address ? StandardProtocolFamily.INET : StandardProtocolFamily.INET6;                
                this.sock = new hk.miru.javapgm.Socket (pf);

                System.out.println ("PGM daytime service");
                this.sock.setOption (hk.miru.javapgm.SocketOptions.PGM_UDP_ENCAP_UCAST_PORT, new Integer (this.udpEncapsulationPort));
                this.sock.setOption (hk.miru.javapgm.SocketOptions.PGM_UDP_ENCAP_MCAST_PORT, new Integer (this.udpEncapsulationPort));
                
/* Set PGM parameters */                
                this.sock.setOption (hk.miru.javapgm.SocketOptions.PGM_SEND_ONLY, new Boolean (true));
                this.sock.setOption (hk.miru.javapgm.SocketOptions.PGM_MTU, new Integer (this.max_tpdu));
                this.sock.setOption (hk.miru.javapgm.SocketOptions.PGM_TXW_SQNS, new Integer (this.txw_sqns));
                this.sock.setOption (hk.miru.javapgm.SocketOptions.PGM_TXW_MAX_RTE, new Integer (this.txw_max_rte));
                this.sock.setOption (hk.miru.javapgm.SocketOptions.PGM_AMBIENT_SPM, new Integer (this.ambientSpm));
                this.sock.setOption (hk.miru.javapgm.SocketOptions.PGM_HEARTBEAT_SPM, this.heartbeatSpm);
                
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

/* Service loop */
		Selector selector = Selector.open();
                SelectionKey sk = this.sock.register (selector, SelectionKey.OP_READ);
                final List<SocketBuffer> skbs = new ArrayList<>();
                do {
                        final String date = this.TIME_FORMAT.format (new Date());
                        hk.miru.javapgm.Socket.IoStatus status = this.sock.send (date.getBytes(), 0, date.getBytes().length);
                        if (hk.miru.javapgm.Socket.IoStatus.IO_STATUS_NORMAL != status) {
                                System.err.println ("PGM send failed.");
                        }
/* NAK routine */
                        long now = System.currentTimeMillis();
                        long sleepUntil = now + 1000;
                        while (now < sleepUntil) {
                                status = this.sock.receive (skbs);
                                now = System.currentTimeMillis();
                                long timeout = sleepUntil - now;
                                switch (status) {
                                case IO_STATUS_TIMER_PENDING:
                                        timeout = Math.min (this.sock.getTimeRemain(), timeout);
/* Workaround lack of functional goto in Java */                                    
                                case IO_STATUS_RATE_LIMITED:
                                        if (hk.miru.javapgm.Socket.IoStatus.IO_STATUS_RATE_LIMITED == status) {
                                                timeout = Math.min (this.sock.getRateRemain(), timeout);
                                        }
                                case IO_STATUS_WOULD_BLOCK:
                                        if (timeout > 0 && selector.select (timeout) > 0) {
                                                selector.selectedKeys().clear();
                                        }
                                        now = System.currentTimeMillis();
                                        break;
                                default:
                                        System.err.format ("unhandled return state: %s%n", status);
                                        break;
                                }
                        }
                } while (!isTerminated);
                
                System.out.println ("finished.");
	}

	public static void main (String[] args) throws IOException, NoSuchAlgorithmException, InterruptedException
	{
		new daytime (args);
	}
}

/* eof */