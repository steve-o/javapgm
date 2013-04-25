/* Example subscription to a PGM stream.
 */ 

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.net.*;
import java.util.*;

public class testreceive
{
/* Windows does not have convenient adapter names. */
//	String interfaceName = "10.0.9.30";
	String interfaceName = "eth0";
	String networkGroup = "239.192.0.1";
	int udpEncapsulationPort = 3056;
	int dataSourcePort = 0;
	int dataDestinationPort = 7500;
	int max_tpdu = 1500;
	int rxw_sqns = 128;
	int rxw_secs = 0;
	long rxw_max_rte = 0;
	long lastCommit = 0;
	long nextPoll = 0;
	long peerExpiration = 300 * 1000;
	long nak_rpt_ivl = 2 * 1000;
	long nak_bo_ivl = 50;
	boolean canReceiveData = true;
	boolean isReset = false;
	boolean shouldAbortOnReset = false;
	boolean hasPendingRead = false;
	DatagramChannel dc = null;
	ByteBuffer buffer = null;
	SocketBuffer rx_buffer = null;
	InetAddress group = null;
/* Workaround Java lack of pass-by-reference for source peer. */
	Peer[] source = new Peer[1];
	Hashtable<TransportSessionId, Peer> peers = new Hashtable<TransportSessionId, Peer>();
	LinkedList<Peer> peers_pending = new LinkedList<Peer>();

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

	public testreceive (String[] args) throws IOException
	{
		this.group = InetAddress.getByName (this.networkGroup);
		ProtocolFamily pf = this.group instanceof Inet4Address ? StandardProtocolFamily.INET : StandardProtocolFamily.INET6;
		NetworkInterface ni = NetworkInterface.getByName (this.interfaceName);
		if (null == ni) ni = NetworkInterface.getByInetAddress (InetAddress.getByName (this.interfaceName));
		this.dc = DatagramChannel.open (pf)
			.setOption (StandardSocketOptions.SO_REUSEADDR, true)
			.bind (new InetSocketAddress (this.udpEncapsulationPort))
			.setOption (StandardSocketOptions.IP_MULTICAST_IF, ni);
		this.dc.configureBlocking (false);
		MembershipKey key = this.dc.join (this.group, ni);

		this.buffer = ByteBuffer.allocateDirect (this.max_tpdu);

		Runtime.getRuntime().addShutdownHook (new Thread() {
			@Override
			public void run() {
				System.out.println ("fin.");
			}
		});

		Selector selector = Selector.open();
		SelectionKey sk = this.dc.register (selector, SelectionKey.OP_READ);
		while (true) {
			final int keyCount = selector.select (1000);
			if (keyCount > 0) {
				selector.selectedKeys().clear();

				final List<SocketBuffer> skbs = new ArrayList<SocketBuffer>();
				final IoStatus status = receive (skbs);
				switch (status) {
				case IO_STATUS_NORMAL:
					on_data (skbs);
					break;
				case IO_STATUS_TIMER_PENDING:
					System.out.println ("timer pending ...");
					break;
				case IO_STATUS_RATE_LIMITED:
					System.out.println ("rate limited ...");
					break;
				case IO_STATUS_WOULD_BLOCK:
					System.out.println ("would block.");
					break;
				default:
					System.out.println ("unhandled return state: " + status);
					break;
				}
			}
		}
	}

	private void on_data (List<SocketBuffer> skbs) {
		System.out.println ("Received " + skbs.size() + " SKBs");
		int i = 1;
		for (SocketBuffer skb : skbs) {
			System.out.println ("#" + (i++) + " from " + skb.getTransportSessionId() + ": " +
				"\"" + new String (skb.getRawBytes(), skb.getDataOffset(), skb.getLength()) + "\"");
		}
	}

	private IoStatus receive (List<SocketBuffer> skbs) throws IOException {
		IoStatus status = IoStatus.IO_STATUS_WOULD_BLOCK;

		if (this.isReset) {
			if (!this.shouldAbortOnReset)
				this.isReset = !this.isReset;
			return IoStatus.IO_STATUS_RESET;
		}

/* timer status */
		if (timerCheck() &&
		    !timerDispatch())
		{
/* block on send-in-recv */
			status = IoStatus.IO_STATUS_RATE_LIMITED;
		}

		if (0 == ++(this.lastCommit))
			++(this.lastCommit);

		do {
/* second, flush any remaining contiguous messages from previous call(s) */
			if (!this.peers_pending.isEmpty()) {
				if (0 != flushPeersPending (skbs))
					break;
			}
		
			do {
				InetSocketAddress src = (InetSocketAddress)this.dc.receive (this.buffer);
				this.buffer.flip();
				this.rx_buffer = new SocketBuffer (this.buffer.remaining());
				this.rx_buffer.put (this.rx_buffer.getRawBytes().length);
				this.buffer.get (this.rx_buffer.getRawBytes(), 0, this.rx_buffer.getRawBytes().length);
				this.buffer.clear();
				this.rx_buffer.setTimestamp (System.currentTimeMillis());
/* Rx testing */
if (Math.random() < 0.25) {
	System.out.println ("Dropping packet.");
	continue;
}
				if (!Packet.parseUdpEncapsulated (this.rx_buffer))
					break;
				this.source[0] = null;
				if (!onPgm (this.rx_buffer, src.getAddress(), this.group, this.source))
					break;
/* check whether this source has waiting data */
				if (null != this.source[0] && this.source[0].hasPending()) {
					System.out.println ("New pending data.");
					this.peers_pending.addFirst (this.source[0]);
					this.source[0].setPendingLinkData();
				}
/* flush any congtiguous packets generated by the receipt of this packet */
				if (!this.peers_pending.isEmpty()) {
					if (0 != flushPeersPending (skbs))
						break;
				}
			} while (false);
		} while (false);

		if (skbs.isEmpty()) {
			if (this.isReset) {
				if (!this.shouldAbortOnReset)
					this.isReset = !this.isReset;
				return IoStatus.IO_STATUS_RESET;
			}
			if (IoStatus.IO_STATUS_WOULD_BLOCK == status &&
				!this.peers.isEmpty())
			{
				return IoStatus.IO_STATUS_TIMER_PENDING;
			}
			return status;
		}

		return IoStatus.IO_STATUS_NORMAL;
	}

	private boolean onUpstream (
		SocketBuffer skb,
		InetAddress sourceAddress,
		InetAddress destinationAddress
		)
	{
		return false;
	}

	private boolean onPeer (
		SocketBuffer skb,
		InetAddress sourceAddress,
		InetAddress destinationAddress
		)
	{
		return false;
	}

	private boolean onDownstream (
		SocketBuffer skb,
		InetAddress sourceAddress,
		InetAddress destinationAddress,
		Peer[] source
		)
	{
		if (!this.canReceiveData) {
			System.out.println ("Discarded packet for muted receiver.");
			return false;
		}

		if (skb.getHeader().getDestinationPort() != this.dataDestinationPort) {
			System.out.println ("Discarded packet on data-destination port mismatch.");
			return false;
		}

		TransportSessionId tsi = skb.getHeader().getTransportSessionId();
		source[0] = this.peers.get (tsi);
		if (null == source[0]) {
			source[0] = new Peer (tsi, this.max_tpdu, this.rxw_sqns, this.rxw_secs, this.rxw_max_rte);
			this.peers.put (tsi, source[0]);
		}

		source[0].setLastPacketTimestamp (skb.getTimestamp());

		skb.pull (Packet.SIZEOF_PGM_HEADER);

		switch (skb.getHeader().getType()) {
		case Packet.PGM_ODATA:
		case Packet.PGM_RDATA:
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
			if (destinationAddress.isMulticastAddress())
				source[0].setGroupPath (destinationAddress);
			break;

		default:
			System.out.println ("Discarded unsupported PGM type packet.");
			return false;
		}

		return true;
	}

	private boolean onPgm (
		SocketBuffer skb,
		InetAddress sourceAddress,
		InetAddress destinationAddress,
		Peer[] source			/* reference to a peer */
		)
	{
		if (skb.getHeader().isDownstream())
			return this.onDownstream (skb, sourceAddress, destinationAddress, source);
		if (skb.getHeader().getDestinationPort() == this.dataSourcePort)
		{
			if (skb.getHeader().isUpstream() || skb.getHeader().isPeer())
			{
				return this.onUpstream (skb, sourceAddress, destinationAddress);
			}
		}
		else if (skb.getHeader().isPeer())
			return this.onPeer (skb, sourceAddress, destinationAddress);

		System.out.println ("Discarded unknown PGM packet.");
		return false;
	}

	private boolean onData (
		Peer source,
		SocketBuffer skb
		)
	{
		int msgCount = 0;
		boolean flushNaks = false;

		System.out.println ("onData");

		final long nakBackoffExpiration = skb.getTimestamp() + calculateNakRandomBackoffInterval();

		skb.setOriginalDataOffset (skb.getDataOffset());

		final int opt_total_length = skb.getAsOriginalData().getOptionTotalLength();

/* advance data pointer to payload */
		skb.pull (OriginalData.SIZEOF_DATA_HEADER + opt_total_length);

		if (opt_total_length > 0)
			Packet.parseOptionExtensions (skb, skb.getDataOffset());

		final ReceiveWindow.Returns addStatus = source.add (skb, skb.getTimestamp(), nakBackoffExpiration);
System.out.println ("ReceiveWindow.add returned " + addStatus);

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

	private boolean onNakConfirm (
		Peer source,
		SocketBuffer skb
		)
	{
		return true;
	}

	private boolean onSourcePathMessage (
		Peer source,
		SocketBuffer skb
		)
	{
		System.out.println ("onSourcePathMessage");

		SourcePathMessage spm = new SourcePathMessage (skb, skb.getDataOffset());

/* check for advancing sequence number, or first SPM */
/* BUG: cannot join old networks */
		if (spm.getSpmSequenceNumber().gte (source.getSpmSequenceNumber()))
		{
/* copy NLA for replies */
			source.setNetworkLayerAddress (spm.getNetworkLayerAddress());

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
			System.out.println ("Discarded duplicate SPM.");
			return false;
		}

/* either way bump expiration timer */
		source.setExpiration (skb.getTimestamp() + this.peerExpiration);
		source.clearSpmrExpiration();
		return true;
	}

/* copy any contiguous buffers in the peer list to the provided buffer.
 */
	private int flushPeersPending (
			List<SocketBuffer> skbs
			)
	{
System.out.println ("flushPeersPending");
		int bytes_read = 0;
		int data_read = 0;
		ListIterator it = this.peers_pending.listIterator();
		while (it.hasNext()) {
			Peer peer = (Peer)it.next();
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
System.out.println ("now: " + now + " next: " + ((this.nextPoll - now) / 1000));
		return hasExpired;
	}

	private boolean timerDispatch()
	{
		final long now = System.currentTimeMillis();
		long nextExpiration = 0;

		System.out.println ("timerDispatch");

		if (true) {
			if (!checkPeerState (now))
				return false;
			nextExpiration = minReceiverExpiration (now + this.peerExpiration);
		}

		if (false) {
		}
		else
			this.nextPoll = nextExpiration;

		return true;
	}

	private boolean checkPeerState (long now)
	{
		System.out.println ("checkPeerState");

		if (this.peers.isEmpty())
			return true;

		for (Enumeration<Peer> it = this.peers.elements(); it.hasMoreElements();)
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
			    now >= peer.firstNakRepairDataExpiration())
			{
				nakRepairDataState (peer, now);
			}

/* expired, remove from hash table and linked list */
			if (now >= peer.getExpiration())
			{
				if (peer.hasPendingLinkData())
				{
					System.out.println ("Peer expiration postponed due to committing data.");
					peer.setExpiration (peer.getExpiration() + this.peerExpiration);
				}
				else if (peer.hasCommitData())
				{
					System.out.println ("Peer expiration postoned due to comitted data.");
					peer.setExpiration (peer.getExpiration() + this.peerExpiration);
				}
				else
				{
					System.out.println ("Peer expired.");
					this.peers.remove (peer);
					peer = null;
				}
			}
		}

/* check for waiting contiguous packets */
		if (!this.peers_pending.isEmpty() && !this.hasPendingRead)
		{
			System.out.println ("Signal receiver thread.");
			this.hasPendingRead = true;
		}

		return true;
	}

	private long minReceiverExpiration (long expiration)
	{
		System.out.println ("minReceiverExpiration");

		if (this.peers.isEmpty())
			return expiration;

		for (Enumeration<Peer> it = this.peers.elements(); it.hasMoreElements();)
		{
			Peer peer = it.nextElement();
			if (peer.hasSpmrExpiration() &&
			    expiration >= peer.getSpmrExpiration())
			{
				expiration = peer.getSpmrExpiration();
			}

			if (!peer.getNakBackoffQueue().isEmpty() &&
			    expiration >= peer.firstNakBackoffExpiration())
			{
				expiration = peer.firstNakBackoffExpiration();
			}

			if (!peer.getWaitNakConfirmQueue().isEmpty() &&
			    expiration >= peer.firstNakRepeatExpiration())
			{
				expiration = peer.firstNakRepeatExpiration();
			}

			if (!peer.getWaitDataQueue().isEmpty() &&
			    expiration >= peer.firstNakRepairDataExpiration())
			{
				expiration = peer.firstNakRepairDataExpiration();
			}
		}

		return expiration;
	}

	private boolean nakBackoffState (Peer peer, long now)
	{
		int droppedInvalid = 0;

		System.out.println ("nakBackoffState");

		Queue<SocketBuffer> nakBackoffQueue = peer.getNakBackoffQueue();
		if (nakBackoffQueue.isEmpty()) {
			System.out.println ("Backoff queue is empty in nak_rb_state.");
			return true;
		}

		final boolean isValidNla = peer.hasValidNla();

		{
			ArrayList<SequenceNumber> nakList = new ArrayList<SequenceNumber>();

/* select NAK generation */

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

					ReceiveWindow.setNakBackoffExpiration (skb, now + this.nak_rpt_ivl);
					System.out.println ("nak_rpt_expiry in " + ((ReceiveWindow.getNakBackoffExpiration (skb) - now) / 1000) + " seconds.");
					if (this.nextPoll > ReceiveWindow.getNakBackoffExpiration (skb))
						this.nextPoll = ReceiveWindow.getNakBackoffExpiration (skb);

					if (nakList.size() == 63) {
						if (!sendNakList (peer, nakList))
							return false;
						nakList.clear();
					}
				}
				else
				{	/* packet expires some time later */
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
			System.out.println ("Dropped " + droppedInvalid + " messages due to invalid NLA.");

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
			System.out.println ("Next expiration set in " + secs + " seconds.");
		} else {
			System.out.println ("NAK backoff queue empty.");
		}
		return true;
	}

	private void nakRepeatState (Peer peer, long now)
	{
		int droppedInvalid = 0;
		int dropped = 0;

		System.out.println ("NakRepeatState");

		Queue<SocketBuffer> waitNakConfirmQueue = peer.getWaitNakConfirmQueue();

		final boolean isValidNla = peer.hasValidNla();

		if (droppedInvalid > 0)
			System.out.println ("Dropped " + droppedInvalid + " message due to invalid NLA.");

		if (dropped > 0)
			System.out.println ("Dropped " + dropped + " messages due to NCF cancellation.");

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
				System.out.println ("Next expiration set in " + seconds + " seconds.");
			} else {
				final long seconds = (now - peer.firstNakRepeatExpiration()) / 1000;
				System.out.println ("Next expiration set in -" + seconds + " seconds.");
			}
		}
		else
		{
			System.out.println ("Wait NCF queue empty.");
		}
	}

	private void nakRepairDataState (Peer peer, long now)
	{
		int droppedInvalid = 0;
		int dropped = 0;

		System.out.println ("nakRepairDataState");

		Queue<SocketBuffer> waitDataQueue = peer.getWaitDataQueue();

		final boolean isValidNla = peer.hasValidNla();

		if (droppedInvalid > 0)
			System.out.println ("Dropped " + droppedInvalid + " message due to invalid NLA.");

		if (dropped > 0)
			System.out.println ("Dropped " + dropped + " messages due to data cancellation.");

		if (peer.hasDataLoss() &&
		    !peer.hasPendingLinkData())
		{
			this.isReset = true;
			peer.clearDataLoss();
			setPendingPeer (peer);
		}

		if (!waitDataQueue.isEmpty()) {
			final long seconds = (peer.firstNakRepairDataExpiration() - now) / 1000;
			System.out.println ("Next expiration set in " + seconds + " seconds.");
		} else {
			System.out.println ("Wait data queue empty.");
		}
	}

	private void setPendingPeer (Peer peer)
	{
		if (peer.hasPendingLinkData()) return;
		this.peers_pending.addFirst (peer);
		peer.setPendingLinkData();
	}

	private boolean sendSpmr (Peer peer)
	{
		System.out.println ("sendSpmr");
		return true;
	}

	private boolean sendNak (Peer peer, SequenceNumber sequence)
	{
		System.out.println ("sendNak");
		return true;
	}

	private boolean sendNakList (Peer peer, ArrayList<SequenceNumber> sqn_list)
	{
		System.out.println ("sendNakList");
		return true;
	}

/* The java Math library function Math.random() generates a double value in the
 * range [0,1). Notice this range does not include the 1.
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

	public static void main (String[] args) throws IOException
	{
		new testreceive (args);
	}
}

/* eof */
