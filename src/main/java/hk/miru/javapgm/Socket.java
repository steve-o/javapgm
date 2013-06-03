/* PGM socket: manage incoming & outgoing sockets with ambient SPMs,
 * transmit & receive windows.
 */

package hk.miru.javapgm;

import static hk.miru.javapgm.Preconditions.checkArgument;
import static hk.miru.javapgm.Preconditions.checkNotNull;
import static hk.miru.javapgm.ReceiveWindow.Returns.RXW_APPENDED;
import static hk.miru.javapgm.ReceiveWindow.Returns.RXW_BOUNDS;
import static hk.miru.javapgm.ReceiveWindow.Returns.RXW_DUPLICATE;
import static hk.miru.javapgm.ReceiveWindow.Returns.RXW_INSERTED;
import static hk.miru.javapgm.ReceiveWindow.Returns.RXW_MALFORMED;
import static hk.miru.javapgm.ReceiveWindow.Returns.RXW_MISSING;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.ProtocolFamily;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import javax.annotation.Nullable;

public class Socket {
    
        private Logger LOG = LogManager.getLogger (Socket.class.getName());
        private static final Marker NETWORK_MARKER = MarkerManager.getMarker ("NETWORK");
        private static final Marker TX_WINDOW_MARKER = MarkerManager.getMarker ("TX_WINDOW");
        private static final Marker RX_WINDOW_MARKER = MarkerManager.getMarker ("RX_WINDOW");
        private static final Marker RATE_CONTROL_MARKER = MarkerManager.getMarker ("RATE_CONTROL");
        private static final Marker SESSION_MARKER = MarkerManager.getMarker ("SESSION");

        ProtocolFamily family = null;
        TransportSessionId tsi = null;
	int dataDestinationPort = 0;
	int udpEncapsulationUnicastPort = 0;
	int udpEncapsulationMulticastPort = 0;

        boolean isBound = false;
        boolean isConnected = false;
        boolean isDestroyed = false;
        boolean isReset = false;
	boolean shouldAbortOnReset = false;

        boolean canSendData = true;
        boolean canSendNak = true;
	boolean canReceiveData = true;

        public static final int IP_MAX_MEMBERSHIPS = 20;

        public static final int UINT8_MAX = 0xff;
        public static final int UINT16_MAX = 0xffff;
        public static final long UINT32_MAX = 0xffff_ffffL;

        hk.miru.javapgm.GroupSourceRequest send_gsr = null;
	InetAddress send_addr = null;
	MulticastSocket send_sock = null;
        Map<hk.miru.javapgm.GroupSourceRequest, MembershipKey> recv_gsr = new TreeMap<>();
	DatagramChannel recv_sock = null;

        int max_apdu = 0;
	int max_tpdu = 0;
        int max_tsdu = 0;
        int max_tsdu_fragment = 0;
        int iphdr_len = 0;
        int hops = 0;
        int txw_sqns = 0, txw_secs = 0;
        int rxw_sqns = 0, rxw_secs = 0;
	long txw_max_rte = 0, rxw_max_rte = 0;
        long odata_max_rte = 0;
        long rdata_max_rte = 0;

        TransmitWindow window = null;
        RateControl rate_control = null;
        RateControl odata_rate_control = null;
        RateControl rdata_rate_control = null;
        boolean has_controlled_spm = false;
        boolean has_controlled_odata = false;
        boolean has_controlled_rdata = false;

        long lastCommit = 0;

        SequenceNumber spm_sqn = SequenceNumber.ZERO;
        int spm_ambient_interval = 0;
        int[] spm_heartbeat_interval;
        int spm_heartbeat_state = 0;    /* indexof spm_heartbeat_interval */
	long peerExpiration = 0;
        long spmrExpiration = 0;

        Random rand = null;
	long nak_data_retries = 0, nak_ncf_retries = 0;
	long nak_bo_ivl = 0, nak_rpt_ivl = 0, nak_rdata_ivl = 0;
        long next_heartbeat_spm = 0, next_ambient_spm = 0;

	ByteBuffer buffer = null;
	SocketBuffer rx_buffer = null;

	Hashtable<TransportSessionId, Peer> peers_hashtable = null;
	LinkedList<Peer> peers_pending = new LinkedList<>();
	boolean hasPendingRead = false;
	long nextPoll = 0;
/* Workaround Java lack of pass-by-reference for source peer. */
        Peer[] source = new Peer[1];

	public enum IoStatus {
		IO_STATUS_ERROR,
		IO_STATUS_NORMAL,
		IO_STATUS_RESET,
		IO_STATUS_FIN,
		IO_STATUS_EOF,
		IO_STATUS_WOULD_BLOCK,
		IO_STATUS_RATE_LIMITED,
		IO_STATUS_TIMER_PENDING,
		IO_STATUS_CONGESTION
	}

/* Create a PGM socket object.  Can only create UDP encapsulated transports.
 *
 * If send == recv only two sockets need to be created iff ip headers are not
 * required (IPv6).
 *
 * All receiver addresses must be the same family.
 * interface and multiaddr must be the same family.
 * family cannot be AF_UNSPEC!
 */        
	public Socket (ProtocolFamily family) throws IOException
	{
                LOG.debug ("Socket (family: {})", family);
                
                this.family = family;
                this.canSendData = true;
                this.canSendNak = true;
                this.canReceiveData = true;
                this.dataDestinationPort = Packet.DEFAULT_DATA_DESTINATION_PORT;
                this.tsi = new TransportSessionId (null, Packet.DEFAULT_DATA_SOURCE_PORT);

                LOG.trace (NETWORK_MARKER, "Opening UDP encapsulated sockets.");
		this.recv_sock = DatagramChannel.open (this.family);
                LOG.trace (NETWORK_MARKER, "Set socket sharing.");
                this.recv_sock.setOption (StandardSocketOptions.SO_REUSEADDR, true);
                this.recv_sock.configureBlocking (false);

		this.send_sock = new MulticastSocket ();
                
                LOG.debug ("PGM socket successfully created.");
        }

/* Timeout for pending timer */
        public long getTimeRemain() {
                checkArgument (this.isConnected);
                long usecs = timerExpiration();
                return usecs;
        }

/* Timeout for rate limited IO */
        public long getRateRemain() {
                checkArgument (false);
                return 0;
        }
        
        public int getMaximumTpdu() {
                return this.max_tpdu;
        }
        
        public long getPeerExpiration() {
                return this.peerExpiration;
        }
        
        public long getSpmRequestExpiration() {
                return this.spmrExpiration;
        }
        
        public int getReceiveWindowSizeInSequenceNumbers() {
                return this.rxw_sqns;
        }

        public int getReceiveWindowSizeInSeconds() {
                return this.rxw_secs;
        }
        
        public long getMaximumReceiveRate() {
                return this.rxw_max_rte;
        }
        
        public boolean setOption (int optname, Object optval) throws java.net.SocketException, IOException {
                if (this.isConnected || this.isDestroyed)
                        return false;

                switch (optname) {

/* RFC2113 IP Router Alert
 */
                case SocketOptions.PGM_IP_ROUTER_ALERT:
                        return false;

/* IPv4:   68 <= tpdu < 65536           (RFC 2765)
 * IPv6: 1280 <= tpdu < 65536           (RFC 2460)
 */
                case SocketOptions.PGM_MTU:
                        checkArgument (optval instanceof Integer);
                        checkArgument ((Integer)optval >= (Packet.SIZEOF_IP_HEADER + Packet.SIZEOF_PGM_HEADER));
                        checkArgument ((Integer)optval <= UINT16_MAX);
                        this.max_tpdu = ((Integer)optval).intValue();
                        return true;

/* true = enable multicast loopback.
 * false = default, to disable.
 */
                case SocketOptions.PGM_MULTICAST_LOOP:
                        checkArgument (optval instanceof Boolean);
                        this.send_sock.setLoopbackMode (((Boolean)optval).booleanValue());
                        this.recv_sock.setOption (StandardSocketOptions.IP_MULTICAST_LOOP, ((Boolean)optval).booleanValue());
                        return true;

/* 0 < hops < 256, hops == -1 use kernel default (ignored).
 */
                case SocketOptions.PGM_MULTICAST_HOPS:
                        checkArgument (optval instanceof Integer);
                        checkArgument ((Integer)optval > 0);
                        checkArgument ((Integer)optval <= UINT8_MAX);
                        this.hops = ((Integer)optval).intValue();
                        this.send_sock.setTimeToLive (hops);
                        return true;

/* IP Type of Service (ToS) or RFC 3246, differentiated services (DSCP)
 */
                case SocketOptions.PGM_TOS:
                        return false;

/* Periodic ambient broadcast SPM interval in milliseconds.
 */
                case SocketOptions.PGM_AMBIENT_SPM:
                        checkArgument (optval instanceof Integer);
                        checkArgument ((Integer)optval > 0);
                        this.spm_ambient_interval = ((Integer)optval).intValue();
                        return true;

/* Sequence of heartbeat broadcast SPMS to flush out original
 */
                case SocketOptions.PGM_HEARTBEAT_SPM:
                        this.spm_heartbeat_interval = Ints.toArray ((List<Integer>)optval);
                        return true;

/* Size of transmit window in sequence numbers.
 * 0 < txw_sqns < one less than half sequence space
 */
                case SocketOptions.PGM_TXW_SQNS:
                        checkArgument (optval instanceof Integer);
                        checkArgument ((Integer)optval > 0);
                        checkArgument ((Integer)optval < ((UINT32_MAX/2)-1));
                        this.txw_sqns = ((Integer)optval).intValue();
                        return true;

/* Size of transmit window in seconds.
 * 0 < secs < ( txw_sqns / txw_max_rte )
 */
                case SocketOptions.PGM_TXW_SECS:
                        checkArgument (optval instanceof Integer);
                        checkArgument ((Integer)optval > 0);
                        this.txw_secs = ((Integer)optval).intValue();
                        return true;

/* Maximum transmit rate.
 * 0 < txw_max_rte < interface capacity
 *  10mb :   1250000
 * 100mb :  12500000
 *   1gb : 125000000
 */
                case SocketOptions.PGM_TXW_MAX_RTE:
                        checkArgument (optval instanceof Integer);
                        checkArgument ((Integer)optval > 0);
                        this.txw_max_rte = ((Integer)optval).intValue();
/* Default to controlling SPM, ODATA, and RDATA packets. */
                        this.has_controlled_odata = true;
                        this.has_controlled_rdata = true;
                        return true;

/* Maximum original data rate.
 * 0 < odata_max_rte < txw_max_rte
 */
                case SocketOptions.PGM_ODATA_MAX_RTE:
                        checkArgument (optval instanceof Integer);
                        checkArgument ((Integer)optval > 0);
                        this.odata_max_rte = ((Integer)optval).intValue();
                        return true;

/* Maximum repair data rate.
 * 0 < rdata_max_rte < txw_max_rte
 */
                case SocketOptions.PGM_RDATA_MAX_RTE:
                        checkArgument (optval instanceof Integer);
                        checkArgument ((Integer)optval > 0);
                        this.rdata_max_rte = ((Integer)optval).intValue();
                        return true;

/* Ignore rate limit for original data packets, i.e. only apply to repairs.
 */
                case SocketOptions.PGM_UNCONTROLLED_ODATA:
                        checkArgument (optval instanceof Boolean);
                        this.has_controlled_odata = !(((Boolean)optval).booleanValue());
                        return true;

/* Ignore rate limit for repair data packets, i.e. only apply to original data.
 */
                case SocketOptions.PGM_UNCONTROLLED_RDATA:
                        checkArgument (optval instanceof Boolean);
                        this.has_controlled_rdata = !(((Boolean)optval).booleanValue());
                        return true;

/* Timeout for peers.
 * 0 < 2 * spm_ambient_interval <= peer_expiry
 */
                case SocketOptions.PGM_PEER_EXPIRY:
                        checkArgument (optval instanceof Integer);
                        this.peerExpiration = (((Integer)optval).intValue());
                        return true;

/* Maximum back off range for listening for multicast SPMR.
 * 0 < spmr_expiry < spm_ambient_interval
 */
                case SocketOptions.PGM_SPMR_EXPIRY:
                        checkArgument (optval instanceof Integer);
                        this.spmrExpiration = (((Integer)optval).intValue());
                        return true;

/* Size of receive window in sequence numbers.
 * 0 < rxw_sqns < one less than half sequence space
 */
                case SocketOptions.PGM_RXW_SQNS:
                        checkArgument (optval instanceof Integer);
                        this.rxw_sqns = (((Integer)optval).intValue());
                        return true;

/* Size of receive window in seconds.
 * 0 < secs < ( rxw_sqns / rxw_max_rte )
 */
                case SocketOptions.PGM_RXW_SECS:
                        checkArgument (optval instanceof Integer);
                        this.rxw_secs = (((Integer)optval).intValue());
                        return true;

/* Maximum receive rate, for determining window size with txw_secs.
 * 0 < rxw_max_rte < interface capacity
 */
                case SocketOptions.PGM_RXW_MAX_RTE:
                        checkArgument (optval instanceof Integer);
                        this.rxw_max_rte = (((Integer)optval).intValue());
                        return true;

/* Maximum NAK back-off value nak_rb_ivl in milliseconds.
 * 0 < nak_rb_ivl <= nak_bo_ivl
 */
                case SocketOptions.PGM_NAK_BO_IVL:
                        checkArgument (optval instanceof Integer);
                        this.nak_bo_ivl = (((Integer)optval).intValue());
                        return true;

/* Repeat interval prior to re-sending a NAK, in milliseconds.
 */
                case SocketOptions.PGM_NAK_RPT_IVL:
                        checkArgument (optval instanceof Integer);
                        this.nak_rpt_ivl = (((Integer)optval).intValue());
                        return true;

/* Interval waiting for repair data, in milliseconds.
 */
                case SocketOptions.PGM_NAK_RDATA_IVL:
                        checkArgument (optval instanceof Integer);
                        this.nak_rdata_ivl = (((Integer)optval).intValue());
                        return true;

/* Limit for data.
 * 0 < nak_data_retries < 256
 */
                case SocketOptions.PGM_NAK_DATA_RETRIES:
                        checkArgument (optval instanceof Integer);
                        this.nak_data_retries = (((Integer)optval).intValue());
                        return true;

/* Limit for NAK confirms.
 * 0 < nak_ncf_retries < 256
 */
                case SocketOptions.PGM_NAK_NCF_RETRIES:
                        checkArgument (optval instanceof Integer);
                        this.nak_ncf_retries = (((Integer)optval).intValue());
                        return true;

/* Enable FEC for this sock, specifically Reed Solmon encoding RS(n,k), common
 * setting is RS(255, 223).
 *
 * Inputs:
 *
 * n = FEC Block size = [k+1, 255]
 * k = original data packets == transmission group size = [2, 4, 8, 16, 32, 64, 128]
 * m = symbol size = 8 bits
 *
 * Outputs:
 *
 * h = 2t = n - k = parity packets
 *
 * When h > k parity packets can be lost.
 */
                case SocketOptions.PGM_USE_FEC:
                        return false;

/* Congestion reporting */
                case SocketOptions.PGM_USE_CR:
                        return false;

/* Congestion control */
                case SocketOptions.PGM_USE_PGMCC:
                        return false;

/* Declare socket only for sending, discard any incoming SPM, ODATA,
 * RDATA, etc, packets.
 */
                case SocketOptions.PGM_SEND_ONLY:
                        checkArgument (optval instanceof Boolean);
                        this.canReceiveData = !(((Boolean)optval).booleanValue());
                        return true;

/* Declare socket only for receiving, no transmit window will be created
 * and no SPM broadcasts sent.
 */
                case SocketOptions.PGM_RECV_ONLY:
                        checkArgument (optval instanceof Boolean);
                        this.canSendData = !(((Boolean)optval).booleanValue());
                        return true;

/* Passive receiving socket, i.e. no back channel to source
 */
                case SocketOptions.PGM_PASSIVE:
                        checkArgument (optval instanceof Boolean);
                        this.canSendNak = !(((Boolean)optval).booleanValue());
                        return true;

/* On unrecoverable data loss stop socket from further transmission and
 * receiving.
 */
                case SocketOptions.PGM_ABORT_ON_RESET:
                        checkArgument (optval instanceof Boolean);
                        this.shouldAbortOnReset = !(((Boolean)optval).booleanValue());
                        return true;

/* Default non-blocking operation on send and receive sockets.
 */
                case SocketOptions.PGM_NOBLOCK:
                        return false;

/* Sending group, singular.  Note that the address is only stored and used
 * later in sendto() calls, this routine only considers the interface.
 */
                case SocketOptions.PGM_SEND_GROUP:
                        checkArgument (optval instanceof hk.miru.javapgm.GroupRequest);
                        {
                                hk.miru.javapgm.GroupRequest gr = (hk.miru.javapgm.GroupRequest)optval;
                                this.send_gsr = new hk.miru.javapgm.GroupSourceRequest (gr.getNetworkInterfaceIndex(), gr.getMulticastAddress(), null);
                                final NetworkInterface send_ni = NetworkInterface.getByIndex (gr.getNetworkInterfaceIndex());
                                this.send_sock.setNetworkInterface (send_ni);
                                LOG.trace (NETWORK_MARKER, "Multicast send interface set to {} index {}",
                                           send_ni, gr.getNetworkInterfaceIndex());
                        }
                        return true;

/* For any-source applications (ASM), join a new group
 */
                case SocketOptions.PGM_JOIN_GROUP:
                        checkArgument (optval instanceof hk.miru.javapgm.GroupRequest);
                        {
                                hk.miru.javapgm.GroupRequest gr = (hk.miru.javapgm.GroupRequest)optval;
                                hk.miru.javapgm.GroupSourceRequest gsr = new hk.miru.javapgm.GroupSourceRequest (gr.getNetworkInterfaceIndex(), gr.getMulticastAddress(), null);
                                MembershipKey key = this.recv_sock.join (gr.getMulticastAddress(), NetworkInterface.getByIndex (gr.getNetworkInterfaceIndex()));
                                this.recv_gsr.put (gsr, key);
                                LOG.trace (NETWORK_MARKER, "Join multicast group {} on interface index {}",
                                           gr.getMulticastAddress(), gr.getNetworkInterfaceIndex());
                        }
                        return true;

/* For any-source applications (ASM), leave a joined group.
 */
                case SocketOptions.PGM_LEAVE_GROUP:
                        checkArgument (optval instanceof hk.miru.javapgm.GroupRequest);
                        {
                                hk.miru.javapgm.GroupRequest gr = (hk.miru.javapgm.GroupRequest)optval;
                                hk.miru.javapgm.GroupSourceRequest gsr = new hk.miru.javapgm.GroupSourceRequest (gr.getNetworkInterfaceIndex(), gr.getMulticastAddress(), null);
                                MembershipKey key = this.recv_gsr.get (gsr);
                                key.drop();
                                this.recv_gsr.remove (gsr);
                        }
                        return false;

/* For any-source applications (ASM), turn off a given source
 */
                case SocketOptions.PGM_BLOCK_SOURCE:
                        checkArgument (optval instanceof hk.miru.javapgm.GroupSourceRequest);
                        {
                                hk.miru.javapgm.GroupSourceRequest gsr = (hk.miru.javapgm.GroupSourceRequest)optval;
                                hk.miru.javapgm.GroupSourceRequest search_gsr = new hk.miru.javapgm.GroupSourceRequest (gsr.getNetworkInterfaceIndex(), gsr.getMulticastAddress(), null);
                                MembershipKey key = this.recv_gsr.get (search_gsr);
                                key.block (gsr.getSourceAddress());
                        }
                        return false;

/* For any-source applications (ASM), re-allow a blocked source
 */
                case SocketOptions.PGM_UNBLOCK_SOURCE:
                        checkArgument (optval instanceof hk.miru.javapgm.GroupSourceRequest);
                        {
                                hk.miru.javapgm.GroupSourceRequest gsr = (hk.miru.javapgm.GroupSourceRequest)optval;
                                hk.miru.javapgm.GroupSourceRequest search_gsr = new hk.miru.javapgm.GroupSourceRequest (gsr.getNetworkInterfaceIndex(), gsr.getMulticastAddress(), null);
                                MembershipKey key = this.recv_gsr.get (search_gsr);
                                key.unblock (gsr.getSourceAddress());
                        }
                        return false;

/* For controlled-source applications (SSM), join each group/source pair.
 *
 * SSM joins are allowed on top of ASM in order to merge a remote source onto the local segment.
 */
                case SocketOptions.PGM_JOIN_SOURCE_GROUP:
                        checkArgument (optval instanceof hk.miru.javapgm.GroupSourceRequest);
                        {
                                hk.miru.javapgm.GroupSourceRequest gsr = (hk.miru.javapgm.GroupSourceRequest)optval;
                                MembershipKey key = this.recv_sock.join (gsr.getMulticastAddress(), NetworkInterface.getByIndex (gsr.getNetworkInterfaceIndex()), gsr.getSourceAddress());
                                LOG.debug ("Join multicast group {} on interface index {} for source {}",
                                          gsr.getMulticastAddress(), gsr.getNetworkInterfaceIndex(), gsr.getSourceAddress());
                                this.recv_gsr.put (gsr, key);
                        }
                        return true;

/* For controlled-source applications (SSM), leave each group/source pair
 */
                case SocketOptions.PGM_LEAVE_SOURCE_GROUP:
                        checkArgument (optval instanceof hk.miru.javapgm.GroupSourceRequest);
                        {
                                hk.miru.javapgm.GroupSourceRequest gsr = (hk.miru.javapgm.GroupSourceRequest)optval;
                                MembershipKey key = this.recv_gsr.get (gsr);
                                key.drop();
                                this.recv_gsr.remove (gsr);
                        }
                        return false;

/* batch block and unblock sources */
                case SocketOptions.PGM_MSFILTER:
                        return false;

/* UDP encapsulation ports */
                case SocketOptions.PGM_UDP_ENCAP_UCAST_PORT:
                        checkArgument (optval instanceof Integer);
                        this.udpEncapsulationUnicastPort = (((Integer)optval).intValue());
                        return true;

                case SocketOptions.PGM_UDP_ENCAP_MCAST_PORT:
                        checkArgument (optval instanceof Integer);
                        this.udpEncapsulationMulticastPort = (((Integer)optval).intValue());
                        return true;

/** Read-only options **/
                case SocketOptions.PGM_MSSS:
                case SocketOptions.PGM_MSS:
                case SocketOptions.PGM_PDU:
                case SocketOptions.PGM_SEND_SOCK:
                case SocketOptions.PGM_RECV_SOCK:
                case SocketOptions.PGM_REPAIR_SOCK:
                case SocketOptions.PGM_PENDING_SOCK:
                case SocketOptions.PGM_ACK_SOCK:
                case SocketOptions.PGM_TIME_REMAIN:
                case SocketOptions.PGM_RATE_REMAIN:
                default:
                        break;
                }

                return false;
        }

/* Bind the sockets to the link layer to start receiving data.
 *
 * Returns TRUE on success, or FALSE on error and sets error appropriately,
 */        
        public boolean bind (hk.miru.javapgm.SocketAddress sockaddr, @Nullable InterfaceRequest send_req, @Nullable InterfaceRequest recv_req)
        {
                checkNotNull (sockaddr);

                if (this.isBound || this.isDestroyed)
                        return false;

/* Sanity checks on state */
                if (this.max_tpdu < (Packet.SIZEOF_IP_HEADER + Packet.SIZEOF_PGM_HEADER)) {
                        LOG.error ("Invalid maximum TPDU size.");
                        return false;
                }
                if (this.canSendData) {
                        if (0 == this.spm_ambient_interval) {
                                LOG.error ("SPM ambient interval not configured.");
                                return false;
                        }
                        if (0 == this.spm_heartbeat_interval.length) {
                                LOG.error ("SPM heartbeat interval not configured.");
                                return false;
                        }
                        if (0 == this.txw_sqns && 0 == this.txw_secs) {
                                LOG.error ("TXW_SQNS not configured.");
                                return false;
                        }
                        if (0 == this.txw_sqns && 0 == this.txw_max_rte) {
                                LOG.error ("TXW_MAX_RTE not configured.");
                                return false;
                        }
                }
                if (this.canReceiveData) {
                        if (0 == this.rxw_sqns && 0 == this.rxw_secs) {
                                LOG.error ("RXW_SQNS not configured.");
                                return false;
                        }
                        if (0 == this.rxw_sqns && 0 == this.rxw_max_rte) {
                                LOG.error ("RXW_MAX_RTE not configured.");
                                return false;
                        }
                        if (0 == this.peerExpiration) {
                                LOG.error ("Peer timeout not configured.");
                                return false;
                        }
                        if (0 == this.spmrExpiration) {
                                LOG.error ("SPM-Request timeout not configured.");
                                return false;
                        }
                        if (0 == this.nak_bo_ivl) {
                                LOG.error ("NAK_BO_IVL not configured.");
                                return false;
                        }
                        if (0 == this.nak_rpt_ivl) {
                                LOG.error ("NAK_RPT_IVL not configured.");
                                return false;
                        }
                        if (0 == this.nak_rdata_ivl) {
                                LOG.error ("NAK_RDATA_IVL not configured.");
                                return false;
                        }
                        if (0 == this.nak_data_retries) {
                                LOG.error ("NAK_DATA_RETRIES not configured.");
                                return false;
                        }
                        if (0 == this.nak_ncf_retries) {
                                LOG.error ("NAK_NCF_RETRIES not configured.");
                                return false;
                        }
                }

                LOG.debug ("bind ()");

                this.tsi = sockaddr.getTransportSessionId();
                this.dataDestinationPort = sockaddr.getPort();
                if (this.tsi.getSourcePort() == 0) {
                        Random rand = new Random();
                        do {
                                this.tsi.setSourcePort (rand.nextInt (UINT16_MAX));
                        } while (this.tsi.getSourcePort() == this.dataDestinationPort);
                }

/* pseudo-random number generator for back-off intervals */
                this.rand = new Random();

/* Determine IP header size for rate regulation engine & stats */
                if (StandardProtocolFamily.INET == this.family)
                        this.iphdr_len = Packet.SIZEOF_IP_HEADER;
                else
                        this.iphdr_len = Packet.SIZEOF_IP6_HEADER;
                LOG.trace (NETWORK_MARKER, "Assuming IP header size of {} bytes", this.iphdr_len);

                ProtocolFamily pgmcc_family = null;
                this.max_tsdu = this.max_tpdu - this.iphdr_len - Packet.calculateOffset (false, pgmcc_family);
                this.max_tsdu_fragment = this.max_tpdu - this.iphdr_len - Packet.calculateOffset (true, pgmcc_family);
                int max_fragments = Packet.PGM_MAX_FRAGMENTS;
                this.max_apdu = Math.min (Packet.PGM_MAX_APDU, max_fragments * this.max_tsdu_fragment);

                if (this.canSendData) {
                        LOG.trace (TX_WINDOW_MARKER, "Create transmit window.");
                        this.window = this.txw_sqns > 0 ?
                                        new TransmitWindow (this.tsi,
                                                            0,
                                                            this.txw_sqns,
                                                            0,
                                                            0) :
                                        new TransmitWindow (this.tsi,
                                                            this.max_tpdu,
                                                            0,
                                                            this.txw_secs,
                                                            this.txw_max_rte);
                        assert (null != this.window);
                }

/* Create peer list */
                if (this.canReceiveData) {
                        this.peers_hashtable = new Hashtable<>();
                }

/* Bind UDP sockets to interfaces, note multicast on a bound interface is
 * fruity on some platforms.  Roughly,  binding to INADDR_ANY provides all
 * data, binding to the multicast group provides only multicast traffic,
 * and binding to the interface address provides only unicast traffic.
 *
 * Multicast routing, IGMP & MLD require a link local address, for IPv4
 * this is provided through MULTICAST_IF and IPv6 through bind, and these
 * may be overridden by per packet scopes.
 *
 * After binding, default interfaces (0.0.0.0) are resolved.
 */
/* TODO: different ports requires a new bound socket */
                InetSocketAddress recv_addr, send_addr;

                if (StandardProtocolFamily.INET6 == this.family) {
/* TODO: No IPv6 wildcard support */
                        recv_addr = new InetSocketAddress ("::", this.udpEncapsulationMulticastPort);
                        send_addr = new InetSocketAddress ("::", 0);
                        LOG.trace (NETWORK_MARKER, "Binding receive socket to IN6ADDR_ANY");
                } else {
                        recv_addr = new InetSocketAddress ("0.0.0.0", this.udpEncapsulationMulticastPort);
                        send_addr = new InetSocketAddress ("0.0.0.0", 0);
                        LOG.trace (NETWORK_MARKER, "Binding receive socket to INADDR_ANY");
                }

/* UDP port */
                try {
                        this.recv_sock.bind (recv_addr);
                        LOG.debug (NETWORK_MARKER, "Bind succeeded on recv_gsr[0] interface {}", recv_addr);
                } catch (IOException ex) {
                        LOG.error ("Binding receive socket to address {}: {}", recv_addr, ex);
                        return false;
                }

/* Java NetworkInterface is overloaded to provide a tree of interfaces, quite
 * unlike any platform it actually runs upon for IPv4 but valid for IPv6.  When
 * multiple addresses are bound to an interface each should have a unique index
 * and thus a unique NetworkInterface instance.
 */
                if (StandardProtocolFamily.INET6 == this.family) {
                        LOG.trace (NETWORK_MARKER, "Binding send socket to interface index {} scope {}",
                                   send_req.getNetworkInterfaceIndex(), send_req.getScopeId());
                } else {
                        LOG.trace (NETWORK_MARKER, "Binding send socket to interface index {}",
                                   send_req.getNetworkInterfaceIndex());
                }

                try {
                        NetworkInterface ni = NetworkInterface.getByIndex (send_req.getNetworkInterfaceIndex());
                        Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
                        while (inetAddresses.hasMoreElements()) {
                                InetAddress addr = inetAddresses.nextElement();
                                if (addr instanceof Inet6Address) {
                                        Inet6Address in6addr = (Inet6Address)addr;
                                        if (in6addr.getScopeId() == send_req.getScopeId()) {
                                                send_addr = new InetSocketAddress (addr, 0);
                                                break;
                                        }
                                } else if (addr instanceof Inet4Address) {
                                        send_addr = new InetSocketAddress (addr, 0);
                                        break;
                                }
                        }
/* Java does not permit bind on a MulticastSocket so defer to NetworkInterface binding */
                        this.send_sock.setNetworkInterface (ni);
                } catch (IOException ex) {
                        LOG.error ("Binding send socket to address {}: {}", send_addr, ex);
                        return false;
                }

/* Resolve bound address if wildcard */
                if (send_addr.getAddress().isAnyLocalAddress()) {
                        InetAddress addr;
                        try {
                                addr = hk.miru.javapgm.NetworkInterface.getMulticastEnabledNodeAddress (this.family);
                        } catch (UnknownHostException | SocketException ex) {
                                LOG.error (ex);
                                return false;
                        }
                        send_addr = new InetSocketAddress (addr, send_addr.getPort());
                }

                LOG.debug (NETWORK_MARKER, "Bind succeeded on send_gsr interface {}", send_addr);

/* Save send side address for broadcasting as source NLA */
                this.send_addr = send_addr.getAddress();

                if (this.canSendData) {
/* Setup rate control */
                        if (this.txw_max_rte > 0) {
                                LOG.trace (RATE_CONTROL_MARKER, "Setting rate regulation to {} bytes per second.",
                                           this.txw_max_rte);
                                this.rate_control = new RateControl (this.txw_max_rte, this.iphdr_len, this.max_tpdu);
                                this.has_controlled_spm = true;     /* Must always be set */
                        } else {
                                this.has_controlled_spm = false;
                        }

                        if (this.odata_max_rte > 0) {
                                LOG.trace (RATE_CONTROL_MARKER, "Setting ODATA rate regulation to {} bytes per second",
                                           this.odata_max_rte);
                                this.odata_rate_control = new RateControl (this.odata_max_rte, this.iphdr_len, this.max_tpdu);
                                this.has_controlled_odata = true;
                        }
                        if (this.rdata_max_rte > 0) {
                                LOG.trace (RATE_CONTROL_MARKER, "Setting RDATA rate regulation to {} bytes per second",
                                           this.rdata_max_rte);
                                this.rdata_rate_control = new RateControl (this.rdata_max_rte, this.iphdr_len, this.max_tpdu);
                                this.has_controlled_rdata = true;
                        }
                }

/* Allocate first incoming packet buffer */
		this.buffer = ByteBuffer.allocateDirect (this.max_tpdu);

/* Bind complete */
                this.isBound = true;

                LOG.debug ("PGM socket successfully bound.");
                return true;
        }

        public boolean connect() {
                if (this.isConnected || !this.isBound || this.isDestroyed) {
                        return false;
                }

                LOG.debug ("connect ()");

                if (this.canSendData)
                {
/* Announce new sock by sending out SPMs */
                        if (!this.sendSpm (Packet.PGM_OPT_SYN) ||
                            !this.sendSpm (Packet.PGM_OPT_SYN) ||
                            !this.sendSpm (Packet.PGM_OPT_SYN))
                        {
                                LOG.error ("Sending SPM broadcast");
                                return false;
                        }

                        this.nextPoll = this.next_ambient_spm = System.currentTimeMillis() + this.spm_ambient_interval;
                }
                else
                {
                        this.nextPoll = System.currentTimeMillis() + (30 * 1000);
                }

                this.isConnected = true;

/* Cleanup */
                LOG.debug ("PGM socket successfully connected.");
                return true;
        }

        public SelectionKey register (Selector selector, int op) throws ClosedChannelException {
                return this.recv_sock.register (selector, op);
        }

/* Send one APDU, whether it fits within one TPDU or more.
 *
 * On success, returns PGM_IO_STATUS_NORMAL, on block for non-blocking sockets
 * returns PGM_IO_STATUS_WOULD_BLOCK, returns PGM_IO_STATUS_RATE_LIMITED if
 * packet size exceeds the current rate limit.
 */        
        public IoStatus send (byte[] apdu, int offset, int apdu_length) {
                LOG.debug ("send");

/* State */
                if (!this.isBound || this.isDestroyed || apdu_length > this.max_apdu)
                        return IoStatus.IO_STATUS_ERROR;

/* Pass on non-fragment calls */
                if (apdu_length <= this.max_tsdu) {
                        return this.sendOriginalData (apdu, offset, apdu_length);
                } else {
                        return this.send_apdu (apdu, offset, apdu_length);
                }
        }

/* Data incoming on receive sockets, can be from a sender or receiver, or simply bogus.
 * For IPv4 we receive the IP header to handle fragmentation, for IPv6 we cannot, but the
 * underlying stack handles this for us.
 *
 * recvmsgv reads a vector of apdus each contained in a IO scatter/gather array.
 *
 * Can be called due to event from incoming socket(s) or timer induced data loss.
 *
 * On success, returns PGM_IO_STATUS_NORMAL and saves the count of bytes read
 * into _bytes_read.  With non-blocking sockets a block returns
 * PGM_IO_STATUS_WOULD_BLOCK.  When rate limited sending repair data, returns
 * PGM_IO_STATUS_RATE_LIMITED and caller should wait.  During recovery state,
 * returns PGM_IO_STATUS_TIMER_PENDING and caller should also wait.  On
 * unrecoverable dataloss, returns PGM_IO_STATUS_CONN_RESET.  If connection is
 * closed, returns PGM_IO_STATUS_EOF.  On error, returns PGM_IO_STATUS_ERROR.
 */
        
	public IoStatus receive (List<SocketBuffer> skbs) throws IOException {
		IoStatus status = IoStatus.IO_STATUS_WOULD_BLOCK;

/* State */
                if (!this.isBound || this.isDestroyed)
                        return IoStatus.IO_STATUS_ERROR;
                
/* Pre-conditions */
                assert (null != this.buffer);
                assert (this.max_tpdu > 0);
                if (this.canReceiveData) {
                        assert (null != this.peers_hashtable);
                        assert (this.nak_bo_ivl > 1);
                }
                
/* Receiver */                
		if (this.isReset) {
			if (!this.shouldAbortOnReset)
				this.isReset = !this.isReset;
			return IoStatus.IO_STATUS_RESET;
		}

/* Timer status */
		if (timerCheck() &&
		    !timerDispatch())
		{
/* Block on send-in-recv */
			status = IoStatus.IO_STATUS_RATE_LIMITED;
		}
/* NAK status */
                else if (this.canSendData)
                {
                        if (!this.window.isRetransmitEmpty()) {
                                if (!onDeferredNak())
                                        status = IoStatus.IO_STATUS_RATE_LIMITED;
                        }
                }

		if (0 == ++(this.lastCommit))
			++(this.lastCommit);

		do {
/* second, flush any remaining contiguous messages from previous call(s) */
			if (null != this.peers_pending && !this.peers_pending.isEmpty()) {
				if (0 != flushPeersPending (skbs))
					break;
			}

			do {
				InetSocketAddress src = (InetSocketAddress)this.recv_sock.receive (this.buffer);
/* No datagram was immediately available. */
                                if (null == src)
                                        break;
				this.buffer.flip();
				this.rx_buffer = new SocketBuffer (this.buffer.remaining());
                                this.rx_buffer.setSocket (this);
				this.rx_buffer.setTimestamp (System.currentTimeMillis());
				this.rx_buffer.put (this.rx_buffer.getRawBytes().length);
				this.buffer.get (this.rx_buffer.getRawBytes(), 0, this.rx_buffer.getRawBytes().length);
				this.buffer.clear();
/* Rx testing */
                                if (false && (Math.random() < 0.25)) {
                                        LOG.debug ("Simulated packet loss");
                                        continue;
                                }
				if (!Packet.parseUdpEncapsulated (this.rx_buffer))
					break;
				this.source[0] = null;
				if (!onPgm (this.rx_buffer, src.getAddress(), this.recv_gsr.keySet().iterator().next().getMulticastAddress(), this.source))
					break;
/* Check whether this source has waiting data */
				if (null != this.source[0] && this.source[0].hasPending()) {
					LOG.trace (RX_WINDOW_MARKER, "New pending data.");
					this.peers_pending.addFirst (this.source[0]);
					this.source[0].setPendingLinkData();
				}
/* Flush any congtiguous packets generated by the receipt of this packet */
				if (null != this.peers_pending && !this.peers_pending.isEmpty()) {
					if (0 != flushPeersPending (skbs))
						break;
				}
			} while (false);
		} while (false);

		if (skbs.isEmpty()) {
/* Report data loss */                    
			if (this.isReset) {
				if (!this.shouldAbortOnReset)
					this.isReset = !this.isReset;
				return IoStatus.IO_STATUS_RESET;
			}
			if (IoStatus.IO_STATUS_WOULD_BLOCK == status &&
                                (this.canSendData ||
                                 this.canReceiveData && !this.peers_hashtable.isEmpty()))
			{
				return IoStatus.IO_STATUS_TIMER_PENDING;
			}
			return status;
		}

		return IoStatus.IO_STATUS_NORMAL;
	}

/* Upstream = receiver to source, peer-to-peer = receive to receiver
 *
 * NB: SPMRs can be upstream or peer-to-peer, if the packet is multicast then its
 *     a peer-to-peer message, if its unicast its an upstream message.
 *
 * Returns TRUE on valid processed packet, returns FALSE on discarded packet.
 */        
	private boolean onUpstream (
		SocketBuffer skb,
		InetAddress sourceAddress,
		InetAddress destinationAddress
		)
	{
		if (!this.canSendData) {
			LOG.trace (NETWORK_MARKER, "Discarded packet for muted source.");
			return false;
		}

/* Unicast upstream message, note that dport & sport are reversed */                
		if (skb.getHeader().getSourcePort() != this.dataDestinationPort) {
/* It is an upstream/peer-to-peer for another session */                    
			LOG.trace (NETWORK_MARKER, "Discarded packet on data-destination port mismatch.");
			LOG.debug ("Data-destination port: {}", skb.getHeader().getDestinationPort());
			return false;
		}
                
                if (!skb.getHeader().getTransportSessionId().getGlobalSourceId().equals (this.tsi.getGlobalSourceId())) {
/* It is an upstream/peer-to-peer for another session */                    
                        LOG.trace (NETWORK_MARKER, "Discarded packet on GSI mismatch.");
                        return false;
                }

/* Advance SKB pointer to PGM type header */                
		skb.pull (Packet.SIZEOF_PGM_HEADER);

		switch (skb.getHeader().getType()) {
		case Packet.PGM_NAK:
			if (!onNak (skb))
				return false;
                        break;

                case Packet.PGM_NNAK:
                        if (!onNullNak (skb))
                                return false;
                        break;

                case Packet.PGM_SPMR:
                        if (!onSpmRequest (null, skb))
                                return false;
                        break;

                case Packet.PGM_ACK:
                        if (!onAck (skb))
                                return false;
                        break;
                    
                case Packet.PGM_POLR:
		default:
			LOG.trace (NETWORK_MARKER, "Discarded unsupported PGM type packet.");
			return false;
		}

		return true;
	}

        
/* Peer to peer message, either multicast NAK or multicast SPMR.
 *
 * Returns TRUE on valid processed packet, returns FALSE on discarded packet.
 */        
	private boolean onPeer (
		SocketBuffer skb,
		InetAddress sourceAddress,
		InetAddress destinationAddress
		)
	{
		return false;
	}

/* Source to receiver message
 *
 * Returns TRUE on valid processed packet, returns FALSE on discarded packet.
 */        
	private boolean onDownstream (
		SocketBuffer skb,
		InetAddress sourceAddress,
		InetAddress destinationAddress,
		Peer[] source
		)
	{
/* Pre-conditions */
                assert (null != skb);
                assert (null != sourceAddress);
                assert (null != destinationAddress);
                assert (null != source);
            
                if (LOG.isDebugEnabled())
                        LOG.debug ("onDownstream");
                
		if (!this.canReceiveData) {
			LOG.trace (NETWORK_MARKER, "Discarded packet for muted receiver.");
			return false;
		}

/* PGM packet DPORT contains our sock DPORT */                
		if (skb.getHeader().getDestinationPort() != this.dataDestinationPort) {
			LOG.trace (NETWORK_MARKER, "Discarded packet on data-destination port mismatch.");
			LOG.debug ("Data-destination port: {}", skb.getHeader().getDestinationPort());
			return false;
		}

/* Search for TSI peer context or create a new one */                
		TransportSessionId tsi = skb.getHeader().getTransportSessionId();
		source[0] = this.peers_hashtable.get (tsi);
		if (null == source[0]) {
			source[0] = new Peer (this, tsi, sourceAddress, destinationAddress, skb.getTimestamp());
			this.peers_hashtable.put (tsi, source[0]);
		}

		source[0].setLastPacketTimestamp (skb.getTimestamp());

		skb.pull (Packet.SIZEOF_PGM_HEADER);

/* Handle PGM packet type */                
		switch (skb.getHeader().getType()) {
		case Packet.PGM_RDATA:
		case Packet.PGM_ODATA:
			if (!this.onData (source[0], skb))
				return false;
			break;

		case Packet.PGM_NCF:
			if (!this.onNakConfirm (source[0], skb))
				return false;
			break;

		case Packet.PGM_SPM:
			if (!this.onSourcePathMessage (source[0], skb))
				return false;
/* Update group NLA if appropriate */                        
			if (destinationAddress.isMulticastAddress())
				source[0].setGroupAddress (destinationAddress);
			break;

                case Packet.PGM_POLL:
		default:
			LOG.trace (NETWORK_MARKER, "Discarded unsupported PGM type packet.");
			return false;
		}

		return true;
	}

/* Process a pgm packet
 *
 * Returns TRUE on valid processed packet, returns FALSE on discarded packet.
 */        
	private boolean onPgm (
		SocketBuffer skb,
		InetAddress sourceAddress,
		InetAddress destinationAddress,
		Peer[] source			/* reference to a peer */
		)
	{
/* Pre-conditions */
                assert (null != skb);
                assert (null != sourceAddress);
                assert (null != destinationAddress);
                assert (null != source);
                
                if (LOG.isDebugEnabled())
                        LOG.debug ("onPgm");
                
		if (skb.getHeader().isDownstream())
			return this.onDownstream (skb, sourceAddress, destinationAddress, source);
		if (skb.getHeader().getDestinationPort() == this.tsi.getSourcePort())
		{
			if (skb.getHeader().isUpstream() || skb.getHeader().isPeer())
			{
				return this.onUpstream (skb, sourceAddress, destinationAddress);
			}
		}
		else if (skb.getHeader().isPeer())
			return this.onPeer (skb, sourceAddress, destinationAddress);

		LOG.trace (NETWORK_MARKER, "Discarded unknown PGM packet.");
		return false;
	}

/* SPM indicate start of a session, continued presence of a session, or flushing final packets
 * of a session.
 *
 * Returns TRUE on valid packet, FALSE on invalid packet or duplicate SPM sequence number.
 */        
	private boolean onSourcePathMessage (
		Peer source,
		SocketBuffer skb
		)
	{
		LOG.debug ("onSourcePathMessage");

		SourcePathMessage spm = new SourcePathMessage (skb, skb.getDataOffset());

/* check for advancing sequence number, or first SPM */
/* BUG: cannot join old networks */
		if (spm.getSpmSequenceNumber().gte (source.getSpmSequenceNumber()))
		{
/* copy NLA for replies */
			source.setNetworkLayerAddress (spm.getSpmNla());

/* save sequence number */
			source.setSpmSequenceNumber (spm.getSpmSequenceNumber());

/* update receive window */
			final long nak_rb_expiry = skb.getTimestamp() + calculateNakRandomBackoffInterval();
			final int naks = source.update (spm.getSpmLead(),
							spm.getSpmTrail(),
							skb.getTimestamp(),
							nak_rb_expiry);
			if (naks > 0) {
				if (this.nextPoll > nak_rb_expiry)
					this.nextPoll = nak_rb_expiry;
			}

/* mark receiver window for flushing on next recv() */
			if (source.hasDataLoss() &&
			    !source.hasPendingLinkData())
			{
				this.isReset = true;
				source.clearDataLoss();
				setPendingPeer (source);
			}
		}
		else
		{	/* does not advance SPM sequence number */
			LOG.trace (NETWORK_MARKER, "Discarded duplicate SPM.");
			return false;
		}

/* either way bump expiration timer */
		source.setExpiration (skb.getTimestamp() + this.peerExpiration);
		source.clearSpmrExpiration();
		return true;
	}
        
/* NCF confirming receipt of a NAK from this sock or another on the LAN segment.
 *
 * Packet contents will match exactly the sent NAK, although not really that helpful.
 *
 * If NCF is valid, returns TRUE.  on error, FALSE is returned.
 */        
	private boolean onNakConfirm (
		Peer source,
		SocketBuffer skb
		)
	{
                LOG.debug ("onNakConfirm");
		return true;
	}

/* ODATA or RDATA packet with any of the following options:
 *
 * OPT_FRAGMENT - this TPDU part of a larger APDU.
 *
 * Ownership of skb is taken and must be passed to the receive window or destroyed.
 *
 * Returns TRUE is skb has been replaced, FALSE is remains unchanged and can be recycled.
 */        
	private boolean onData (
		Peer source,
		SocketBuffer skb
		)
	{
		int msgCount = 0;
		boolean flushNaks = false;

		LOG.debug ("onData");

		final long nakBackoffExpiration = skb.getTimestamp() + calculateNakRandomBackoffInterval();

		skb.setOriginalDataOffset (skb.getDataOffset());

		final int opt_total_length = skb.getAsOriginalData().getOptionTotalLength();

/* advance data pointer to payload */
		skb.pull (OriginalData.SIZEOF_DATA_HEADER + opt_total_length);

		if (opt_total_length > 0)
			Packet.parseOptionExtensions (skb, skb.getDataOffset());

		final ReceiveWindow.Returns addStatus = source.add (skb, skb.getTimestamp(), nakBackoffExpiration);
LOG.debug ("ReceiveWindow.add returned " + addStatus);

		switch (addStatus) {
		case RXW_MISSING:
			flushNaks = true;
		case RXW_INSERTED:
		case RXW_APPENDED:
			msgCount++;
			break;

		case RXW_DUPLICATE:
		case RXW_MALFORMED:
		case RXW_BOUNDS:
			return false;
		}

		if (flushNaks) {
/* flush out 1st time nak packets */
			if (flushNaks && this.nextPoll > nakBackoffExpiration)
				this.nextPoll = nakBackoffExpiration;
		}
		return true;
	}        
        
/* A deferred request for RDATA, now processing in the timer thread, we check the transmit
 * window to see if the packet exists and forward on, maintaining a lock until the queue is
 * empty.
 *
 * Returns TRUE on success, returns FALSE if operation would block.
 */        
        private boolean onDeferredNak()
        {
/* We can flush queue and block all odata, or process one set, or process each
 * sequence number individually.
 */

/* Parity packets are re-numbered across the transmission group with index h, sharing the space
 * with the original packets.  Beyond the transmission group size (k), the PGM option OPT_PARITY_GRP
 * provides the extra offset value.
 */

/* Peek from the retransmit queue so we can eliminate duplicate NAKs up until the repair packet
 * has been retransmitted.
 */
                SocketBuffer skb = this.window.tryPeekRetransmit();
                if (null != skb) {
                        if (!sendRepairData (skb.get())) {
                                skb.free();
                                return false;
                        }
                        skb.free();
/* Now remove sequence number from retransmit queue, re-enabling NAK processing for this sequence number */                        
                        this.window.removeRetransmit();
                }
                return true;
        }
        
/* SPMR indicates if multicast to cancel own SPMR, or unicast to send SPM.
 *
 * Rate limited to 1/IHB_MIN per TSI (13.4).
 *
 * If SPMR was valid, returns TRUE, if invalid returns FALSE.
 */        
        private boolean onSpmRequest (@Nullable Peer peer, SocketBuffer skb)
        {
/* Pre-conditions */
                assert (null != skb);
                
                LOG.debug ("onSpmRequest (skb:{})", skb);
                
                if (null == peer) {
                        if (!sendSpm (0)) {
                                LOG.trace (NETWORK_MARKER, "Failed to send SPM on SPM-Request.");
                        }
                } else {
                        LOG.trace (RX_WINDOW_MARKER, "Suppressing SPMR due to peer multicast SPMR.");
                        peer.clearSpmrExpiration();
                }
                return true;
        }

/* NAK requesting RDATA transmission for a sending sock, only valid if
 * sequence number(s) still in transmission window.
 *
 * We can potentially have different IP versions for the NAK packet to the send group.
 *
 * TODO: fix IPv6 AFIs
 *
 * Take in a NAK and pass off to an asynchronous queue for another thread to process
 *
 * If NAK is valid, returns TRUE.  on error, FALSE is returned.
 */        
        private boolean onNak (SocketBuffer skb)
        {
/* Pre-conditions */
                assert (null != skb);
                
                LOG.debug ("onNak (skb:{})", skb);

		Nak nak = new Nak (skb, skb.getDataOffset());
                
/* NAK_SRC_NLA contains our sock unicast NLA */
                if (!nak.getNakSourceNla().equals (this.send_addr)) {
                        LOG.trace (NETWORK_MARKER, "NAK rejected for unmatched NLA: {}", nak.getNakSourceNla());
                        return false;
                }
                
/* NAK_GRP_NLA contains our sock multicast group */
                if (!nak.getNakGroupNla().equals (this.send_gsr.getMulticastAddress())) {
                        LOG.trace (NETWORK_MARKER, "NAK rejected as targeted for different multicast group: {}", nak.getNakGroupNla());
                        return false;
                }
                
/* Create queue object */
                List<SequenceNumber> sqn_list = new ArrayList<> ();
                sqn_list.add (nak.getNakSequenceNumber());
                
/* Check NAK list */
                if (skb.getHeader().isOptionExtensionPresent()) {
                        SequenceNumber[] nak_list = nak.getNakListOption();
                        sqn_list.addAll (Arrays.asList (nak_list));
                }
                
                LOG.debug ("nak_sqn {}", sqn_list);
                
/* NAK list numbers */
                if (sqn_list.size() > 62) {
                        LOG.trace (NETWORK_MARKER, "Malformed NAK rejected on sequence list overrun, {} reported NAKs.", sqn_list.size());
                        return false;
                }
                
/* Send NAK confirm packet immediately, then defer to timer thread for a.s.a.p
 * delivery of the actual RDATA packets.  Blocking send for NCF is ignored as RDATA
 * broadcast will be sent later.
 */                
                if (sqn_list.size() > 1) {
                        sendNakConfirmList (nak.getNakSourceNla(), nak.getNakGroupNla(), sqn_list);
                } else {
                        sendNakConfirm (nak.getNakSourceNla(), nak.getNakGroupNla(), sqn_list.get (0));
                }

/* Queue retransmit requests */
                for (SequenceNumber nak_sqn : sqn_list) {
                        if (!this.window.pushRetransmit (nak_sqn)) {
                                LOG.trace (TX_WINDOW_MARKER, "Failed to push retransmit request for {}.", nak_sqn);
                        }
                }                
                return true;
        }
        
/* Null-NAK, or N-NAK propogated by a DLR for hand waving excitement
 *
 * If NNAK is valid, returns TRUE.  on error, FALSE is returned.
 */        
        private boolean onNullNak (SocketBuffer skb)
        {
                return false;
        }
        
/* ACK, sent upstream by one selected ACKER for congestion control feedback.
 *
 * If ACK is valid, returns TRUE.  on error, FALSE is returned.
 */        
        private boolean onAck (SocketBuffer skb)
        {
                return false;
        }
        
/* copy any contiguous buffers in the peer list to the provided buffer.
 */
	private int flushPeersPending (
			List<SocketBuffer> skbs
			)
	{
LOG.debug ("flushPeersPending");
		int bytes_read = 0;
		int data_read = 0;
		ListIterator<Peer> it = this.peers_pending.listIterator();
		while (it.hasNext()) {
			Peer peer = it.next();
			if (peer.hasLastCommit() && peer.getLastCommit() < this.lastCommit)
				peer.removeCommit();
			final int peer_bytes = peer.read (skbs);
			if (peer.hasDataLoss())
			{
				this.isReset = true;
				peer.clearDataLoss();
			}
			if (peer_bytes > 0) {
				bytes_read += peer_bytes;
				data_read++;
				peer.setLastCommit (this.lastCommit);
			} else
				peer.setLastCommit (0);
			if (this.isReset)
				return -1;
/* clear this reference and move to next */
			this.peers_pending.remove();
			peer.clearPendingLinkData();
		}
		return bytes_read;
	}

	private boolean timerCheck()
	{
		final long now = System.currentTimeMillis();
		final boolean hasExpired = now >= this.nextPoll;
LOG.debug ("now: {} next: {}", now, (this.nextPoll - now) / 1000);
		return hasExpired;
	}

/* Return next timer expiration in milliseconds for Java, not microseconds per C.
 */
        private long timerExpiration()
        {
                final long now = System.currentTimeMillis();
                final long expiration = this.nextPoll > now ? (this.nextPoll - now) : 0;
                return expiration;
        }

/* Call all timers, assume that time_now has been updated by either pgm_timer_prepare
 * or pgm_timer_check and no other method calls here.
 *
 * Returns TRUE on success, returns FALSE on blocked send-in-receive operation.
 */
	private boolean timerDispatch()
	{
		final long now = System.currentTimeMillis();
		long nextExpiration = 0;

		LOG.debug ("timerDispatch");

/* Find which timers have expired and call each */
		if (this.canReceiveData) {
			if (!checkPeerState (now))
				return false;
			nextExpiration = minReceiverExpiration (now + this.peerExpiration);
		}

		if (this.canSendData) {
/* SPM broadcast */
                        final int spmHeartbeatState = this.spm_heartbeat_state;
                        final long nextHeartbeatSpm = this.next_heartbeat_spm;
                        
                        final long nextAmbientSpm = this.next_ambient_spm;
                        long nextSpm = spmHeartbeatState > 0 ? Math.min (nextHeartbeatSpm, nextAmbientSpm) : nextAmbientSpm;

LOG.debug ("nextHeartbeatSpm:{} nextAmbientSpm:{}", nextHeartbeatSpm, nextAmbientSpm);                        
                        
                        if (now >= nextSpm && !sendSpm (0))
                                return false;
                        
/* Ambient timing not so important so base next event off current time */
                        if (now >= nextAmbientSpm) {
                                this.next_ambient_spm = now + this.spm_ambient_interval;
                                nextSpm = spmHeartbeatState > 0 ? Math.min (nextHeartbeatSpm, this.next_ambient_spm) : this.next_ambient_spm;
                        }
                        
/* Heartbeat timing is often high resolution so base times to last event */
                        if (spmHeartbeatState > 0 && now >= nextHeartbeatSpm) {
                                int newHeartbeatState = spmHeartbeatState;
                                long newHeartbeatSpm = nextHeartbeatSpm;
                                do {
                                        newHeartbeatSpm += this.spm_heartbeat_interval[newHeartbeatState++];
                                        if (newHeartbeatState == this.spm_heartbeat_interval.length) {
                                                newHeartbeatState = 0;
                                                newHeartbeatSpm = now + this.spm_ambient_interval;
                                                break;
                                        }
                                } while (now >= newHeartbeatSpm);
/* Check for reset heartbeat */
                                if (nextHeartbeatSpm == this.next_heartbeat_spm) {
                                        this.spm_heartbeat_state = newHeartbeatState;
                                        this.next_heartbeat_spm = newHeartbeatSpm;
                                        nextSpm = Math.min (this.next_ambient_spm, newHeartbeatSpm);
                                } else
                                        nextSpm = Math.min (this.next_ambient_spm, this.next_heartbeat_spm);
                                this.nextPoll = nextExpiration > 0 ? Math.min (nextExpiration, nextSpm) : nextSpm;
                                return true;
                        }
                        
                        nextExpiration = nextExpiration > 0 ? Math.min (nextExpiration, nextSpm) : nextSpm;

/* Check for reset */
                        this.nextPoll = this.nextPoll > now ? Math.min (this.nextPoll, nextExpiration) : nextExpiration;
LOG.debug ("nextExpiration:{} this.nextPoll:{}", nextExpiration, this.nextPoll);                        
		}
		else
			this.nextPoll = nextExpiration;

		return true;
	}

/* Check this peer for NAK state timers, uses the tail of each queue for the nearest
 * timer execution.
 *
 * Returns TRUE on complete sweep, returns FALSE if operation would block.
 */        
	private boolean checkPeerState (long now)
	{
		LOG.debug ("checkPeerState");

		if (this.peers_hashtable.isEmpty())
			return true;

		for (Enumeration<Peer> it = this.peers_hashtable.elements(); it.hasMoreElements();)
		{
			Peer peer = it.nextElement();
			if (peer.hasSpmrExpiration() &&
			    now >= peer.getSpmrExpiration())
			{
				if (!sendSpmr (peer))
					return false;
				peer.clearSpmrExpiration();
			}

			if (!peer.getNakBackoffQueue().isEmpty() &&
			    now >= peer.firstNakBackoffExpiration())
			{
				if (!nakBackoffState (peer, now))
					return false;
			}

			if (!peer.getWaitNakConfirmQueue().isEmpty() &&
			    now >= peer.firstNakRepeatExpiration())
			{
				nakRepeatState (peer, now);
			}

			if (!peer.getWaitDataQueue().isEmpty() &&
			    now >= peer.firstRepairDataExpiration())
			{
				nakRepairDataState (peer, now);
			}

/* expired, remove from hash table and linked list */
			if (now >= peer.getExpiration())
			{
				if (peer.hasPendingLinkData())
				{
					LOG.trace (SESSION_MARKER, "Peer expiration postponed due to committing data.");
					peer.setExpiration (peer.getExpiration() + this.peerExpiration);
				}
				else if (peer.hasCommitData())
				{
					LOG.trace (SESSION_MARKER, "Peer expiration postoned due to comitted data.");
					peer.setExpiration (peer.getExpiration() + this.peerExpiration);
				}
				else
				{
					LOG.trace (SESSION_MARKER, "Peer expired, tsi {}", peer.getTransportSessionId());
					this.peers_hashtable.remove (peer.getTransportSessionId());
					peer = null;
				}
			}
		}

/* check for waiting contiguous packets */
		if (!this.peers_pending.isEmpty() && !this.hasPendingRead)
		{
			LOG.debug ("Signal receiver thread.");
			this.hasPendingRead = true;
		}

		return true;
	}

/* Find the next state expiration time among the socks peers.
 *
 * On success, returns the earliest of the expiration parameter or next
 * peer expiration time.
 */        
	private long minReceiverExpiration (long expiration)
	{
		LOG.debug ("minReceiverExpiration");

		if (this.peers_hashtable.isEmpty())
			return expiration;

		for (Enumeration<Peer> it = this.peers_hashtable.elements(); it.hasMoreElements();)
		{
			Peer peer = it.nextElement();
			if (peer.hasSpmrExpiration() &&
			    expiration >= peer.getSpmrExpiration())
			{
LOG.debug ("Next expiration: SPMR");
				expiration = peer.getSpmrExpiration();
			}

			if (!peer.getNakBackoffQueue().isEmpty() &&
			    expiration >= peer.firstNakBackoffExpiration())
			{
LOG.debug ("Next expiration: NAK backoff");
				expiration = peer.firstNakBackoffExpiration();
			}

			if (!peer.getWaitNakConfirmQueue().isEmpty() &&
			    expiration >= peer.firstNakRepeatExpiration())
			{
LOG.debug ("Next expiration: NAK repeat");
				expiration = peer.firstNakRepeatExpiration();
			}

			if (!peer.getWaitDataQueue().isEmpty() &&
			    expiration >= peer.firstRepairDataExpiration())
			{
LOG.debug ("Next expiration: RDATA");
				expiration = peer.firstRepairDataExpiration();
			}
		}

		return expiration;
	}

/* Check all receiver windows for packets in BACK-OFF_STATE, on expiration send a NAK.
 * update sock::next_nak_rb_timestamp for next expiration time.
 *
 * Peer object is locked before entry.
 *
 * Returns TRUE on success, returns FALSE if operation would block.
 */        
	private boolean nakBackoffState (Peer peer, long now)
	{
		int droppedInvalid = 0;

		LOG.debug ("nakBackoffState");

		Queue<SocketBuffer> nakBackoffQueue = peer.getNakBackoffQueue();
		if (nakBackoffQueue.isEmpty()) {
			LOG.trace (RX_WINDOW_MARKER, "Backoff queue is empty in nak_rb_state.");
			return true;
		}

		final boolean isValidNla = peer.hasValidNla();

		{
			ArrayList<SequenceNumber> nakList = new ArrayList<>();

/* select NAK generation */

LOG.debug ("nakBackoffQueue contains {} SKBs.", nakBackoffQueue.size());
			for (Iterator<SocketBuffer> it = nakBackoffQueue.iterator(); it.hasNext();)
			{
				SocketBuffer skb = it.next();

				if (now >= ReceiveWindow.getNakBackoffExpiration (skb))
				{
					if (!isValidNla) {
						droppedInvalid++;
						peer.markLost (skb.getSequenceNumber());
						setPendingPeer (peer);
						continue;
					}

					peer.setWaitNakConfirmState (skb);
					nakList.add (skb.getSequenceNumber());
					ReceiveWindow.incrementNakTransmitCount (skb);

					ReceiveWindow.setNakRepeatExpiration (skb, now + this.nak_rpt_ivl);
					LOG.trace (NETWORK_MARKER, "nak_rpt_expiry in {} seconds.", ((ReceiveWindow.getNakRepeatExpiration (skb) - now) / 1000));
					if (this.nextPoll > ReceiveWindow.getNakRepeatExpiration (skb))
						this.nextPoll = ReceiveWindow.getNakRepeatExpiration (skb);

					if (nakList.size() == 63) {
						if (!sendNakList (peer, nakList))
							return false;
						nakList.clear();
					}
				}
				else
				{	/* packet expires some time later */
LOG.debug ("SKB expiration now + {}", (ReceiveWindow.getNakBackoffExpiration (skb) - now));
					break;
				}
			}

			if (!nakList.isEmpty()) {
				if (nakList.size() > 1 && !sendNakList (peer, nakList))
					return false;
				else if (!sendNak (peer, nakList.get (0)))
					return false;
			}
		}

		if (droppedInvalid > 0)
		{
			LOG.trace (RX_WINDOW_MARKER, "Dropped {} messages due to invalid NLA.", droppedInvalid);

			if (peer.hasDataLoss() &&
			    !peer.hasPendingLinkData())
			{
				this.isReset = true;
				peer.clearDataLoss();
				setPendingPeer (peer);
			}
		}

		if (!nakBackoffQueue.isEmpty()) {
			final long secs = (peer.firstNakBackoffExpiration() - now) / 1000;
			LOG.trace (NETWORK_MARKER, "Next expiration set in {} seconds.", secs);
		} else {
			LOG.trace (RX_WINDOW_MARKER, "NAK backoff queue empty.");
		}
		return true;
	}

/* Check WAIT_NCF_STATE, on expiration move back to BACK-OFF_STATE, on exceeding NAK_NCF_RETRIES
 * cancel the sequence number.
 */        
	private void nakRepeatState (Peer peer, long now)
	{
		int droppedInvalid = 0;
		int dropped = 0;

		LOG.debug ("NakRepeatState");

		Queue<SocketBuffer> waitNakConfirmQueue = peer.getWaitNakConfirmQueue();

		final boolean isValidNla = peer.hasValidNla();

LOG.debug ("waitNcfQueue contains {} SKBs.", waitNakConfirmQueue.size());
		for (Iterator<SocketBuffer> it = waitNakConfirmQueue.iterator(); it.hasNext();)
		{
			SocketBuffer skb = it.next();

/* check this packet for state expiration */
			if (now >= ReceiveWindow.getNakRepeatExpiration (skb))
			{
				if (!isValidNla) {
					droppedInvalid++;
					peer.markLost (skb.getSequenceNumber());
/* mark receiver window for flushing on next recv() */
					setPendingPeer (peer);
					continue;
				}

				ReceiveWindow.incrementNcfRetryCount (skb);
				if (ReceiveWindow.getNcfRetryCount (skb) >= this.nak_ncf_retries)
				{
					dropped++;
					cancel (peer, skb, now);
				}
				else
				{
/* retry */
					ReceiveWindow.setNakBackoffExpiration (skb, now + calculateNakRandomBackoffInterval());
					peer.setBackoffState (skb);
					LOG.trace (RX_WINDOW_MARKER, "NCF retry #{} attempt {}/{}.",
                                                   skb.getSequenceNumber(),
                                                   ReceiveWindow.getNcfRetryCount (skb),
                                                   this.nak_ncf_retries);
				}
			}
			else
			{
/* packet expires some time later */
				final long seconds = (ReceiveWindow.getNakRepeatExpiration (skb) - now) / 1000;
				LOG.trace (RX_WINDOW_MARKER, "NCF retry #{} is delayed {} seconds.",
                                           skb.getSequenceNumber(), seconds);
			}
		}

		if (droppedInvalid > 0)
			LOG.trace (RX_WINDOW_MARKER, "Dropped {} message due to invalid NLA.", droppedInvalid);

		if (dropped > 0)
			LOG.trace (RX_WINDOW_MARKER, "Dropped messages due to NCF cancellation.", dropped);

		if (peer.hasDataLoss() &&
		    !peer.hasPendingLinkData())
		{
			this.isReset = true;
			peer.clearDataLoss();
			setPendingPeer (peer);
		}

		if (!waitNakConfirmQueue.isEmpty())
		{
			if (peer.firstNakRepeatExpiration() > now) {
				final long seconds = (peer.firstNakRepeatExpiration() - now) / 1000;
				LOG.trace (NETWORK_MARKER, "Next expiration set in {} seconds.", seconds);
			} else {
				final long seconds = (now - peer.firstNakRepeatExpiration()) / 1000;
				LOG.trace (NETWORK_MARKER, "Next expiration set in -{} seconds.", seconds);
			}
		}
		else
		{
			LOG.trace (RX_WINDOW_MARKER, "Wait NCF queue empty.");
		}
	}

/* Check WAIT_DATA_STATE, on expiration move back to BACK-OFF_STATE, on exceeding NAK_DATA_RETRIES
 * canel the sequence number.
 */        
	private void nakRepairDataState (Peer peer, long now)
	{
		int droppedInvalid = 0;
		int dropped = 0;

		LOG.debug ("nakRepairDataState");

		Queue<SocketBuffer> waitDataQueue = peer.getWaitDataQueue();

/* Have not learned this peers NLA */                
		final boolean isValidNla = peer.hasValidNla();

LOG.debug ("waitDataQueue contains {} SKBs.", waitDataQueue.size());
		for (Iterator<SocketBuffer> it = waitDataQueue.iterator(); it.hasNext();)
		{
			SocketBuffer skb = it.next();

/* Check this packet for state expiration */
			if (now >= ReceiveWindow.getRepairDataExpiration (skb))
			{
				if (!isValidNla) {
					droppedInvalid++;
					peer.markLost (skb.getSequenceNumber());
/* Mark receiver window for flushing on next recv() */
					setPendingPeer (peer);
					continue;
				}

				ReceiveWindow.incrementDataRetryCount (skb);
				if (ReceiveWindow.getDataRetryCount (skb) >= this.nak_data_retries)
				{
					dropped++;
					cancel (peer, skb, now);
				}
				else
				{
/* Retry back to backoff state */
					ReceiveWindow.setNakBackoffExpiration (skb, now + calculateNakRandomBackoffInterval());
					peer.setBackoffState (skb);
					LOG.trace (RX_WINDOW_MARKER, "Data retry #{} attempt {}/{}.",
                                                   skb.getSequenceNumber(),
                                                   ReceiveWindow.getDataRetryCount (skb),
                                                   this.nak_data_retries);
				}
			}
			else
			{
/* Packet expires some time later */
				break;
			}
		}

		if (droppedInvalid > 0)
			LOG.trace (RX_WINDOW_MARKER, "Dropped {} message due to invalid NLA.", droppedInvalid);

		if (dropped > 0)
			LOG.trace (RX_WINDOW_MARKER, "Dropped {} messages due to data cancellation.", dropped);

		if (peer.hasDataLoss() &&
		    !peer.hasPendingLinkData())
		{
			this.isReset = true;
			peer.clearDataLoss();
			setPendingPeer (peer);
		}

		if (!waitDataQueue.isEmpty()) {
			final long seconds = (peer.firstRepairDataExpiration() - now) / 1000;
			LOG.trace (NETWORK_MARKER, "Next expiration set in {} seconds.", seconds);
		} else {
			LOG.trace (RX_WINDOW_MARKER, "Wait data queue empty.");
		}
	}

/* Set receiver in pending event queue
 */        
	private void setPendingPeer (Peer peer)
	{
/* Pre-conditions */
                assert (null != peer);
                
		if (peer.hasPendingLinkData()) return;
		this.peers_pending.addFirst (peer);
		peer.setPendingLinkData();
	}

/* Send SPM-request to a new peer, this packet type has no contents
 *
 * On success, TRUE is returned, if operation would block FALSE is
 * returned.
 */        
	private boolean sendSpmr (Peer peer)
	{
		LOG.debug ("sendSpmr");

		SocketBuffer skb = SourcePathMessageRequest.create();
		Header header = skb.getHeader();
		header.setGlobalSourceId (peer.getTransportSessionId().getGlobalSourceId());
/* dport & sport reversed communicating upstream */
		header.setSourcePort (this.dataDestinationPort);
		header.setDestinationPort (peer.getSourcePort());
		header.setChecksum (Packet.doChecksum (skb.getRawBytes()));

/* Send multicast SPMR TTL 1 to our peers listening on the same groups */
		DatagramPacket pkt = new DatagramPacket (skb.getRawBytes(),
							 skb.getDataOffset(),
							 skb.getLength(),
							 null,
							 this.udpEncapsulationMulticastPort);
                try {
                        this.send_sock.setTimeToLive (1);
                        for (GroupSourceRequest gsr : this.recv_gsr.keySet()) {
                                pkt.setAddress (gsr.getMulticastAddress());
/* Ignore errors on peer multicast */
                                this.send_sock.send (pkt);
                        }
                } catch (java.io.IOException e) {}

/* Send unicast SPMR with regular TTL */
                try {
                        pkt.setAddress (peer.getNetworkLayerAddress());
			this.send_sock.send (pkt);
		} catch (java.io.IOException e) {
			LOG.error (e.toString());
			return false;
		}

                return true;
	}

/* Ambient/heartbeat SPM's
 *
 * Heartbeat: ihb_tmr decaying between ihb_min and ihb_max 2x after last packet
 *
 * On success, TRUE is returned, if operation would block, FALSE is returned.
 */        
        private boolean sendSpm (int flags)
        {
                LOG.debug ("sendSpm");

                SocketBuffer skb = SourcePathMessage.create (this.family, flags);
                Header header = skb.getHeader();
                SourcePathMessage spm = new SourcePathMessage (skb, skb.getDataOffset());
		header.setGlobalSourceId (this.tsi.getGlobalSourceId());
                header.setSourcePort (this.tsi.getSourcePort());
                header.setDestinationPort (this.dataDestinationPort);

/* SPM */
                spm.setSpmSequenceNumber (this.spm_sqn);
                spm.setSpmTrail (this.window.getTrail());
                spm.setSpmLead (this.window.getLead());
/* Our NLA */
                spm.setSpmNla (this.send_addr);

/* Checksum optional for SPMs */
		header.setChecksum (Packet.doChecksum (skb.getRawBytes()));

		DatagramPacket pkt = new DatagramPacket (skb.getRawBytes(),
							 0,
							 skb.getRawBytes().length,
							 this.send_gsr.getMulticastAddress(),
							 this.udpEncapsulationMulticastPort);
		try {
			this.send_sock.send (pkt);
		} catch (java.io.IOException e) {
			LOG.error (e.toString());
			return false;
		}
/* Advance SPM sequence only on successful transmission */
                this.spm_sqn = this.spm_sqn.plus (1);
                return true;
        }

/* Send selective NAK for one sequence number.
 *
 * On success, TRUE is returned, returns FALSE if would block on operation.
 */        
	private boolean sendNak (Peer peer, SequenceNumber sequence)
	{
		LOG.debug ("sendNak");

		SocketBuffer skb = Nak.create (peer.getNetworkLayerAddress(), peer.getGroupAddress());
		Header header = skb.getHeader();
		Nak nak = new Nak (skb, skb.getDataOffset());
		header.setGlobalSourceId (peer.getTransportSessionId().getGlobalSourceId());

/* dport & sport swap over for a nak */
		header.setSourcePort (this.dataDestinationPort);
		header.setDestinationPort (peer.getSourcePort());

/* NAK */
		nak.setNakSequenceNumber (sequence);

/* source nla */
		nak.setNakSourceNla (peer.getNetworkLayerAddress());

/* group nla: we match the NAK NLA to the same as advertised by the source, we might
 * be listening to multiple multicast groups
 */
		nak.setNakGroupNla (peer.getGroupAddress());

		header.setChecksum (Packet.doChecksum (skb.getRawBytes()));

		DatagramPacket pkt = new DatagramPacket (skb.getRawBytes(),
							 0,
							 skb.getRawBytes().length,
							 peer.getNetworkLayerAddress(),
							 this.udpEncapsulationUnicastPort);
		try {
			this.send_sock.send (pkt);
			LOG.debug ("Sent NAK to {}", peer.getNetworkLayerAddress());
			LOG.debug ("NAK: {}", skb);
			return true;
		} catch (java.io.IOException e) {
			LOG.error (e.toString());
			return false;
		}
	}

/* A NAK packet with a OPT_NAK_LIST option extension
 *
 * On success, TRUE is returned.  on error, FALSE is returned.
 */        
	private boolean sendNakList (Peer peer, List<SequenceNumber> sqn_list)
	{
                if (LOG.isDebugEnabled())
                        LOG.debug ("sendNakList (source:{} sqn-list:{})", peer.getNetworkLayerAddress(), sqn_list);

		SocketBuffer skb = Nak.create (peer.getNetworkLayerAddress(), peer.getGroupAddress(), sqn_list.size());
		Header header = skb.getHeader();
		Nak nak = new Nak (skb, skb.getDataOffset());
		header.setGlobalSourceId (peer.getTransportSessionId().getGlobalSourceId());

/* dport & sport swap over for a nak */
		header.setSourcePort (this.dataDestinationPort);
		header.setDestinationPort (peer.getSourcePort());
		header.setOptions (Packet.PGM_OPT_PRESENT | Packet.PGM_OPT_NETWORK);

/* NAK */
		nak.setNakSequenceNumber (sqn_list.get (0));

/* source nla */
		nak.setNakSourceNla (peer.getNetworkLayerAddress());

/* group nla */
		nak.setNakGroupNla (peer.getGroupAddress());

/* OPT_NAK_LIST */
		nak.setNakListOption (sqn_list.subList (1, sqn_list.size()).toArray (new SequenceNumber[0]));

		header.setChecksum (Packet.doChecksum (skb.getRawBytes()));

		DatagramPacket pkt = new DatagramPacket (skb.getRawBytes(),
							 0,
							 skb.getRawBytes().length,
							 peer.getNetworkLayerAddress(),
							 this.udpEncapsulationUnicastPort);
		try {
			this.send_sock.send (pkt);
			return true;
		} catch (java.io.IOException e) {
			LOG.error (e.toString());
			return false;
		}
	}

/* Send a NAK confirm (NCF) message with provided sequence number list.
 *
 * On success, TRUE is returned, returns FALSE if operation would block.
 */        
        private boolean sendNakConfirm (
                InetAddress nak_src_nla,
                InetAddress nak_grp_nla,
                SequenceNumber sequence)
        {
/* Pre-conditions */
                assert (null != nak_src_nla);
                assert (null != nak_grp_nla);
                
                if (LOG.isDebugEnabled())
                        LOG.debug ("sendNakConfirm");

		SocketBuffer skb = NakConfirm.create (nak_src_nla, nak_grp_nla);
		Header header = skb.getHeader();
		NakConfirm ncf = new NakConfirm (skb, skb.getDataOffset());
		header.setGlobalSourceId (this.tsi.getGlobalSourceId());
		header.setSourcePort (this.tsi.getSourcePort());
		header.setDestinationPort (this.dataDestinationPort);

/* NCF */
		ncf.setNakSequenceNumber (sequence);

/* source nla */
		ncf.setNakSourceNla (nak_src_nla);

/* group nla */
		ncf.setNakGroupNla (nak_grp_nla);

		header.setChecksum (Packet.doChecksum (skb.getRawBytes()));

		DatagramPacket pkt = new DatagramPacket (skb.getRawBytes(),
							 0,
							 skb.getRawBytes().length,
							 this.send_gsr.getMulticastAddress(),
							 this.udpEncapsulationMulticastPort);
		try {
			this.send_sock.send (pkt);
			LOG.debug ("Sent NCF to {}", this.send_gsr.getMulticastAddress());
			LOG.debug ("NCF: {}", skb);
			return true;
		} catch (java.io.IOException e) {
			LOG.error (e.toString());
			return false;
		}
        }

/* A NCF packet with a OPT_NAK_LIST option extension
 *
 * On success, TRUE is returned.  on error, FALSE is returned.
 */        
	private boolean sendNakConfirmList (
                InetAddress nak_src_nla,
                InetAddress nak_grp_nla,
                List<SequenceNumber> sqn_list
                )
	{
                if (LOG.isDebugEnabled())
        		LOG.debug ("sendNakConfirmList");

		SocketBuffer skb = NakConfirm.create (nak_src_nla, nak_grp_nla, sqn_list.size());
		Header header = skb.getHeader();
		NakConfirm ncf = new NakConfirm (skb, skb.getDataOffset());
		header.setGlobalSourceId (this.tsi.getGlobalSourceId());
		header.setGlobalSourceId (this.tsi.getGlobalSourceId());
		header.setSourcePort (this.tsi.getSourcePort());
		header.setDestinationPort (this.dataDestinationPort);
		header.setOptions (Packet.PGM_OPT_PRESENT | Packet.PGM_OPT_NETWORK);

/* NCF */
		ncf.setNakSequenceNumber (sqn_list.get (0));

/* source nla */
		ncf.setNakSourceNla (nak_src_nla);

/* group nla */
		ncf.setNakGroupNla (nak_grp_nla);

/* OPT_NAK_LIST */
		ncf.setNakListOption (sqn_list.subList (1, sqn_list.size()).toArray (new SequenceNumber[0]));

		header.setChecksum (Packet.doChecksum (skb.getRawBytes()));

		DatagramPacket pkt = new DatagramPacket (skb.getRawBytes(),
							 0,
							 skb.getRawBytes().length,
							 this.send_gsr.getMulticastAddress(),
							 this.udpEncapsulationMulticastPort);
		try {
			this.send_sock.send (pkt);
			return true;
		} catch (java.io.IOException e) {
			LOG.error (e.toString());
			return false;
		}
	}
        
/* Cancel any pending heartbeat SPM and schedule a new one
 */
        private void resetHeartbeatSpm (long now)
        {
                final long nextPoll = this.nextPoll;
                final long spmHeartbeatInterval = this.spm_heartbeat_interval[this.spm_heartbeat_state = 1];
                this.next_heartbeat_spm = now + spmHeartbeatInterval;
                if (nextPoll > this.next_heartbeat_spm) {
                        this.nextPoll = this.next_heartbeat_spm;
                }
        }

/* Send one PGM original data packet, callee owned memory.
 *
 * On success, returns PGM_IO_STATUS_NORMAL, on block for non-blocking sockets
 * returns PGM_IO_STATUS_WOULD_BLOCK, returns PGM_IO_STATUS_RATE_LIMITED if
 * packet size exceeds the current rate limit.
 */        
        private IoStatus sendOriginalData (byte[] tsdu, int offset, int tsdu_length)
        {
/* Pre-conditions */
                assert (tsdu_length <= this.max_tsdu);

                LOG.debug ("sendOriginalData");

		SocketBuffer skb = OriginalData.create (this.family, tsdu_length);
                skb.setSocket (this);
                skb.setTimestamp (System.currentTimeMillis());
		Header header = skb.getHeader();
		OriginalData odata = skb.getAsOriginalData();
		header.setGlobalSourceId (this.tsi.getGlobalSourceId());
		header.setSourcePort (this.tsi.getSourcePort());
		header.setDestinationPort (this.dataDestinationPort);

/* ODATA */
		odata.setDataSequenceNumber (this.window.getNextLead());
                odata.setDataTrail (this.window.getTrail());
                odata.setData (tsdu, offset, tsdu_length);

		header.setChecksum (Packet.doChecksum (skb.getRawBytes()));

/* Add to transmit window, skb::data set to payload */
                this.window.add (skb);

		DatagramPacket pkt = new DatagramPacket (skb.getRawBytes(),
							 0,
							 skb.getRawBytes().length,
							 this.send_gsr.getMulticastAddress(),
							 this.udpEncapsulationMulticastPort);
		try {
			this.send_sock.send (pkt);
		} catch (java.io.IOException e) {
			LOG.error (e.toString());
		}
                
/* Success */
/* SPM heartbeats decay from last sent data packet */
                resetHeartbeatSpm (skb.getTimestamp());
                
/* Save unfolded odata for retransmissions */
/* Increment socket statistics */              

                return IoStatus.IO_STATUS_NORMAL;
        }

        private int calculateMaximumTsdu (boolean canFragment) {
                int max_tsdu = canFragment ? this.max_tsdu_fragment : this.max_tsdu;
                return max_tsdu;
        }

/* Send PGM original data, callee owned memory.  If larger than maximum TPDU
 * size will be fragmented.
 *
 * On success, returns PGM_IO_STATUS_NORMAL, on block for non-blocking sockets
 * returns PGM_IO_STATUS_WOULD_BLOCK, returns PGM_IO_STATUS_RATE_LIMITED if
 * packet size exceeds the current rate limit.
 */        
        private IoStatus send_apdu (byte[] apdu, int offset, int apdu_length)
        {
                SocketBuffer skb = null;
                int data_bytes_offset = 0;

                do {
                        int tsdu_length = Math.min (calculateMaximumTsdu (true), apdu_length - data_bytes_offset);

                        skb = OriginalData.create (this.family, tsdu_length);
                        skb.setSocket (this);
                        skb.setTimestamp (System.currentTimeMillis());
                        Header header = skb.getHeader();
                        OriginalData odata = skb.getAsOriginalData();
                        header.setGlobalSourceId (this.tsi.getGlobalSourceId());
                        header.setSourcePort (this.tsi.getSourcePort());
                        header.setDestinationPort (this.dataDestinationPort);

/* ODATA */
                        odata.setDataSequenceNumber (this.window.getNextLead());
                        odata.setDataTrail (this.window.getTrail());
                        odata.setData (apdu, offset + data_bytes_offset, tsdu_length);

                        header.setChecksum (Packet.doChecksum (skb.getRawBytes()));

/* Add to transmit window, skb::data set to payload */
                        this.window.add (skb);

                        DatagramPacket pkt = new DatagramPacket (skb.getRawBytes(),
                                                                 0,
                                                                 skb.getRawBytes().length,
                                                                 this.send_gsr.getMulticastAddress(),
                                                                 this.udpEncapsulationMulticastPort);
                        try {
                                this.send_sock.send (pkt);
                        } catch (java.io.IOException e) {
                                LOG.error (e.toString());
/* Fall through silently on other errors */
                        }

                        data_bytes_offset += tsdu_length;

                } while (data_bytes_offset < apdu_length);
                assert (data_bytes_offset == apdu_length);

/* Success */
/* SPM heartbeats decay from last sent data packet */
                resetHeartbeatSpm (skb.getTimestamp());
/* Increment socket statistics */                
                return IoStatus.IO_STATUS_NORMAL;
        }
        
/* Send repair packet.
 *
 * on success, TRUE is returned.  on error, FALSE is returned.
 */        
        private boolean sendRepairData (SocketBuffer skb)
        {
/* Pre-conditions */
                assert (null != skb);

                LOG.debug ("sendRepairData");               
                
/* Rate check including rdata specific limits */
/* Update previous odata/rdata contents */
		Header header = skb.getHeader();
		RepairData rdata = skb.getAsRepairData();
/* RDATA */
                rdata.setDataTrail (this.window.getTrail());

                header.clearChecksum();
                header.setChecksum (Packet.doChecksum (skb.getRawBytes()));
                
/* Congestion control */

		DatagramPacket pkt = new DatagramPacket (skb.getRawBytes(),
							 0,
							 skb.getRawBytes().length,
							 this.send_gsr.getMulticastAddress(),
							 this.udpEncapsulationMulticastPort);
		try {
			this.send_sock.send (pkt);
		} catch (java.io.IOException e) {
			LOG.error (e.toString());
		}                
                
/* Reset SPM timer */
                this.spm_heartbeat_state = 1;
                this.next_heartbeat_spm = System.currentTimeMillis() + this.spm_heartbeat_interval[this.spm_heartbeat_state++];
                
                return true;
        }

/* Mark sequence as recovery failed.
 */        
	private void cancel (Peer peer, SocketBuffer skb, long now)
	{
		LOG.trace (RX_WINDOW_MARKER, "Lost data #{} due to cancellation.", skb.getSequenceNumber());

		peer.markLost (skb.getSequenceNumber());

/* mark receiver window for flushing on next recv() */
		setPendingPeer (peer);
	}

/* The java Math library function Math.random() generates a double value in the
 * range [0,1]. Notice this range does not include the 1.
 * Reference: http://stackoverflow.com/a/363732/175849
 */
	private long randomIntRange (long begin, long end)
	{
		return begin + (long)(Math.random() * ((end - begin) + 1));
	}

/* calculate NAK_RB_IVL as random time interval 1 - NAK_BO_IVL.
 */
	private long calculateNakRandomBackoffInterval()
	{
		return randomIntRange (1 /* us */, this.nak_bo_ivl);
	}
}

/* eof */