/* Receive window as a ring buffer.
 */
package hk.miru.javapgm;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

public class ReceiveWindow {
    
        private static Logger LOG = LogManager.getLogger (ReceiveWindow.class.getName());
        private static final Marker RX_WINDOW_MARKER = MarkerManager.getMarker ("RX_WINDOW");

	public enum PacketState {
		PKT_ERROR_STATE,
		PKT_BACK_OFF_STATE,
		PKT_WAIT_NCF_STATE,
		PKT_WAIT_DATA_STATE,
		PKT_HAVE_DATA_STATE,
		PKT_HAVE_PARITY_STATE,
		PKT_COMMIT_DATA_STATE,
		PKT_LOST_DATA_STATE
	}

	public enum Returns {
		RXW_OK,
		RXW_INSERTED,
		RXW_APPENDED,
		RXW_UPDATED,
		RXW_MISSING,
		RXW_DUPLICATE,
		RXW_MALFORMED,
		RXW_BOUNDS,
		RXW_SLOW_CONSUMER,
		RXW_UNKNOWN
	}

	public static final long UINT32_MAX	= 4294967295L;
	public static final int MAX_FRAGMENTS	= 16;
	public static final int MAX_APDU	= MAX_FRAGMENTS * 1500;

	protected TransportSessionId tsi;

	private class State implements ControlBuffer {
		long			nakBackoffExpiration;
		long			nakRepeatExpiration;
		long			repairDataExpiration;
		PacketState		pktState;
		int			nakTransmitCount;
		int			ncfRetryCount;
		int			dataRetryCount;
		boolean			isContiguous;

		public State (PacketState pktState) {
                        assert (null != pktState);
			this.pktState = pktState;
		}

		public State() {
			this (PacketState.PKT_ERROR_STATE);
		}
	}

	protected Queue<SocketBuffer>	nakBackoffQueue;
	protected Queue<SocketBuffer>	waitNakConfirmQueue;
	protected Queue<SocketBuffer>	waitDataQueue;

	protected long			lostCount;
	protected long			fragmentCount;
	protected long			parityCount;
	protected long			committedCount;

	protected int			max_tpdu;
	protected SequenceNumber	lead, trail;
	protected SequenceNumber	rxw_trail, rxw_trail_init;
	protected SequenceNumber	commitLead;
	protected boolean		isConstrained = true;
	protected boolean		isDefined = false;
	protected boolean		hasEvent = false;
	protected boolean		isFecAvailable = false;
	protected long			transmissionGroupSize;
	protected long			tgSqnShift;

	protected long			minFillTime;
	protected long			maxFillTime;
	protected long			minNakTransmitCount;
	protected long			maxNakTransmitCount;
	protected long			cumulativeLosses;
	protected long			bytesDelivered;
	protected long			messagesDelivered;

	protected int			size;
	protected int			alloc;
	protected SocketBuffer[]	pdata = null;

	public Queue<SocketBuffer> getNakBackoffQueue() {
		return this.nakBackoffQueue;
	}

	public static long getNakBackoffExpiration (SocketBuffer skb) {
                assert (null != skb);
		final State state = (State)skb.getControlBuffer();
		return state.nakBackoffExpiration;
	}

	public static void setNakBackoffExpiration (SocketBuffer skb, long expiration) {
                assert (null != skb);
		State state = (State)skb.getControlBuffer();
		state.nakBackoffExpiration = expiration;
	}

	public long firstNakBackoffExpiration() {
		return getNakBackoffExpiration (this.nakBackoffQueue.peek());
	}

	public void setBackoffState (SocketBuffer skb) {
                assert (null != skb);
		setPacketState (skb, PacketState.PKT_BACK_OFF_STATE);
	}

	public Queue<SocketBuffer> getWaitNakConfirmQueue() {
		return this.waitNakConfirmQueue;
	}

	public static long getNakRepeatExpiration (SocketBuffer skb) {
                assert (null != skb);
		final State state = (State)skb.getControlBuffer();
		return state.nakRepeatExpiration;
	}

	public static void setNakRepeatExpiration (SocketBuffer skb, long expiration) {
                assert (null != skb);
		State state = (State)skb.getControlBuffer();
		state.nakRepeatExpiration = expiration;
	}

	public long firstNakRepeatExpiration() {
		return getNakRepeatExpiration (this.waitNakConfirmQueue.peek());
	}

	public void setWaitNakConfirmState (SocketBuffer skb) {
                assert (null != skb);
		setPacketState (skb, PacketState.PKT_WAIT_NCF_STATE);
	}

	public Queue<SocketBuffer> getWaitDataQueue() {
		return this.waitDataQueue;
	}

	public static long getRepairDataExpiration (SocketBuffer skb) {
                assert (null != skb);
		final State state = (State)skb.getControlBuffer();
		return state.repairDataExpiration;
	}

	public long firstRepairDataExpiration() {
		return getRepairDataExpiration (this.waitDataQueue.peek());
	}

	public static void incrementNakTransmitCount (SocketBuffer skb) {
                assert (null != skb);
		State state = (State)skb.getControlBuffer();
		state.nakTransmitCount++;
	}

	public static void incrementNcfRetryCount (SocketBuffer skb) {
                assert (null != skb);
		State state = (State)skb.getControlBuffer();
		state.ncfRetryCount++;
	}

	public static long getNcfRetryCount (SocketBuffer skb) {
                assert (null != skb);
		State state = (State)skb.getControlBuffer();
		return state.ncfRetryCount;
	}

	public static void incrementDataRetryCount (SocketBuffer skb) {
                assert (null != skb);
		State state = (State)skb.getControlBuffer();
		state.dataRetryCount++;
	}

	public static long getDataRetryCount (SocketBuffer skb) {
                assert (null != skb);
		State state = (State)skb.getControlBuffer();
		return state.dataRetryCount;
	}

/* Returns skbuff at given index of the window.
 */        
	private SocketBuffer peek (SequenceNumber sequence) {
/* Pre-conditions */            
                assert (null != sequence);
                
		if (isEmpty())
			return null;

		if (sequence.gte (this.trail) && sequence.lte (this.lead))
		{
/* index into ArrayList must be an int not a long:
 * error: possible loss of precision
 */
			final int index = (int)(sequence.longValue() % getMaxLength());
			SocketBuffer skb = this.pdata[index];
/* Availability only guaranteed inside commit window */
                        if (sequence.lt (this.commitLead)) {
                                assert (null != skb);
                                assert (SocketBuffer.isValid (skb));
                                assert (null != skb.getTransportSessionId());
                        }
			return skb;
		}

		return null;
	}
     
/* Sections of the receive window:
 * 
 *  |     Commit       |   Incoming   |
 *  |<---------------->|<------------>|
 *  |                  |              |
 * trail         commit-lead        lead
 *
 * Commit buffers are currently held by the application, the window trail
 * cannot be advanced if packets remain in the commit buffer.
 *
 * Incoming buffers are waiting to be passed to the application.
 */        
	private int getCommitLength() {
		return this.commitLead.minus (this.trail).intValue();
	}

	private boolean isCommitEmpty() {
		return getCommitLength() == 0;
	}

	private int getIncomingLength() {
		return this.lead.plus (1).minus (this.commitLead).intValue();
	}

	private boolean isIncomingEmpty() {
		return getIncomingLength() == 0;
	}

	private int getMaxLength() {
		return this.alloc;
	}

	private int getLength() {
		return this.lead.plus (1).minus (this.trail).intValue();
	}

	private int getSize() {
		return this.size;
	}

	private boolean isEmpty() {
		return getLength() == 0;
	}

	private boolean isFull() {
		return getLength() == getMaxLength();
	}

/* Constructor for receive window.  Zero-length windows are not permitted.
 */        
	public ReceiveWindow (
		TransportSessionId tsi,
		int tpdu_size,			/* unsigned 16-bit */
		int sqns,			/* unsigned */
		int secs,			/* unsigned */
		long max_rte
		)
	{
/* Pre-conditions */            
                assert (null != tsi);
                assert (tpdu_size > 0);
                if (sqns != 0) {
                        assert (sqns > 0);
                        assert (secs == 0);
                        assert (max_rte == 0);
                } else {
                        assert (secs > 0);
                        assert (max_rte > 0);
                }
                
                LOG.debug ("create (\"tsi\":{} \"max-tpdu\":{} \"sqns\":{} \"secs\":{} \"max-rte\":{} \"ack-c_p\":{})",
                           tsi, max_tpdu, sqns, secs, max_rte, 0);
                
/* Calculate receive window parameters */
                assert (sqns > 0 || (secs > 0 && max_rte > 0));
		final int alloc_sqns = sqns > 0 ? sqns : (int)((secs * max_rte) / tpdu_size);
		this.pdata = new SocketBuffer[alloc_sqns];

		this.tsi = tsi;
		this.max_tpdu = tpdu_size;

/* Empty state:
 *
 * trail = 0, lead = -1
 * commit_trail = commit_lead = rxw_trail = rxw_trail_init = 0
 */
		this.lead = SequenceNumber.MAX_VALUE;
		this.trail = this.lead.plus (1);

		this.commitLead = SequenceNumber.ZERO;
		this.rxw_trail = SequenceNumber.ZERO;
		this.rxw_trail_init = SequenceNumber.ZERO;

/* Limit retransmit requests on late session joining */
                this.isConstrained = true;
                
		this.tgSqnShift = 0;

/* Skbuff array */
		this.alloc = alloc_sqns;

/* Concurrent to permit modification during iteration without a listIterator. */
		this.nakBackoffQueue = new ConcurrentLinkedQueue<> ();
		this.waitNakConfirmQueue = new ConcurrentLinkedQueue<> ();
		this.waitDataQueue = new ConcurrentLinkedQueue<> ();
                
/* Post-conditions */
                assert (getMaxLength() == alloc_sqns);
                assert (getLength() == 0);
                assert (getSize() == 0);
                assert (isEmpty());
                assert (!isFull());
	}

/* Add skb to receive window.  Window has fixed size and will not grow.
 * PGM skbuff data/tail pointers must point to the PGM payload, and hence skb->len
 * is allowed to be zero.
 *
 * If the skb sequence number indicates lost packets placeholders will be defined
 * for each missing entry in the window.
 *
 * Side effects:
 * 1) Sequence number is set in skb from PGM header value.
 * 2) Window may be updated with new skb.
 * 3) Placeholders may be created for detected lost packets.
 * 4) Parity skbs may be shuffled to accomodate original data.
 * 
 * Returns:
 * PGM_RXW_INSERTED - packet filled a waiting placeholder, skb consumed.
 * PGM_RXW_APPENDED - packet advanced window lead, skb consumed.
 * PGM_RXW_MISSING - missing packets detected whilst window lead was adanced, skb consumed.
 * PGM_RXW_DUPLICATE - re-transmission of previously seen packet.
 * PGM_RXW_MALFORMED - corrupted or invalid packet.
 * PGM_RXW_BOUNDS - packet out of window.
 */
	public Returns add (SocketBuffer skb, long now, long nak_rb_expiry)
	{
		Returns status;

/* Pre-conditions */                
                assert (null != skb);
                assert (nak_rb_expiry > 0);
                assert (getMaxLength() > 0);
                assert (SocketBuffer.isValid (skb));
                
                LOG.debug ("add (\"skb\": {}, \"nak_rb_expiry\": {})",
                           skb, nak_rb_expiry);

		skb.setControlBuffer (new State ());
		skb.setSequenceNumber (skb.getAsOriginalData().getDataSequenceNumber());

/* Protocol sanity check: TSDU size */                
		if (skb.getLength() != skb.getHeader().getTsduLength()) {
                        LOG.debug ("SKB length does not match TSDU length.");
			return Returns.RXW_MALFORMED;
		}

/* Protocol sanity check: valid trail pointer wrt. sequence */
		if (skb.getSequenceNumber().minus (skb.getAsOriginalData().getDataTrail()).longValue() >= ((UINT32_MAX/2)-1)) {
                        LOG.debug ("SKB sequence {} outside window horizon by {} wrt trail {}",
                                    skb.getSequenceNumber(),
                                    skb.getSequenceNumber().minus (skb.getAsOriginalData().getDataTrail()),
                                    skb.getAsOriginalData().getDataTrail());
			return Returns.RXW_BOUNDS;
		}

/* Drop unsupported parity packets */
		if (skb.getHeader().isParity()) {
			LOG.debug ("Unsupported parity packet");
			return Returns.RXW_MALFORMED;
		}

/* Verify fragment header for original data */                
		if (skb.isFragment()) {
/* Protocol sanity check: single fragment APDU */                    
			if (skb.getFragmentOption().getApduLength() == skb.getLength()) {
				LOG.debug ("Fragmented message contains only one fragment.");
			}
/* Protocol sanity check: minimum APDU length */                        
			if (skb.getFragmentOption().getApduLength() < skb.getLength()) {
				LOG.debug ("SKB length greated than APDU length.");
				return Returns.RXW_MALFORMED;
			}
/* Protocol sanity check: sequential ordering */                        
			if (skb.getFragmentOption().getFirstSequenceNumber().gt (skb.getSequenceNumber())) {
				LOG.debug ("Fragment sequence number less than first message fragment.");
				return Returns.RXW_MALFORMED;
			}
/* Protocol sanity check: maximum APDU length */                        
			if (skb.getFragmentOption().getApduLength() > MAX_APDU) {
				LOG.debug ("APDU greater than supported length.");
				return Returns.RXW_MALFORMED;
			}
		}

/* First packet of a session defines the window */                
		if (!this.isDefined) {
			define (skb.getSequenceNumber().minus (1));     /* Previous lead needed for append to occur */
		} else {
			updateTrail (skb.getAsOriginalData().getDataTrail());
		}

/* Bounds checking */               
		if (skb.getSequenceNumber().lt (this.commitLead)) {
			if (skb.getSequenceNumber().gte (this.trail)) {
				LOG.debug ("Duplicate packet from window");
				return Returns.RXW_DUPLICATE;
			} else {
				LOG.debug ("Duplicate packet before window");
				return Returns.RXW_BOUNDS;
			}
		}

LOG.debug ("SKB:{} trail:{} commit:{} lead:{} (RXW_TRAIL:{})",
            skb.getSequenceNumber(), this.trail, this.commitLead, this.lead, this.rxw_trail);
		if (skb.getSequenceNumber().lte (this.lead)) {
			this.hasEvent = true;
			return insert (skb);
		}

		if (skb.getSequenceNumber().equals (this.lead.plus (1))) {
			this.hasEvent = true;
			return append (skb, now);
		}

		status = addPlaceholderRange (skb.getSequenceNumber(), now, nak_rb_expiry);
		if (Returns.RXW_APPENDED == status) {
			status = append (skb, now);
			if (Returns.RXW_APPENDED == status)
				status = Returns.RXW_MISSING;
		}

		return status;
	}

/* Trail is the next packet to commit upstream, lead is the leading edge
 * of the receive window with possible gaps inside, rxw_trail is the transmit
 * window trail for retransmit requests.
 */
        
/* Define window by parameters of first data packet.
 */        
	private void define (SequenceNumber lead)
	{
/* Pre-conditions */            
                assert (null != lead);
                assert (isEmpty());
                assert (isCommitEmpty());
                assert (isIncomingEmpty());
                assert (!this.isDefined);
                
		this.lead = lead;
		this.trail = this.lead.plus (1);
		this.rxw_trail_init = this.trail;
		this.rxw_trail = this.rxw_trail_init;
		this.commitLead = this.rxw_trail;
		this.isConstrained = this.isDefined = true;
                
/* Post-conditions */
                assert (isEmpty());
                assert (isCommitEmpty());
                assert (isIncomingEmpty());
                assert (this.isDefined);
                assert (this.isConstrained);
	}

/* Update window with latest transmitted parameters.
 * 
 * Returns count of placeholders added into window, used to starting sending naks.
 */        
	public int update (SequenceNumber txw_lead, SequenceNumber txw_trail, long now, long nak_rb_expiry)
	{
/* Pre-conditions */            
                assert (null != txw_lead);
                assert (null != txw_trail);
                
		LOG.debug ("update (txw-lead:{} txw-trail:{} nak-rb-expiry:{})",
                            txw_lead, txw_trail, nak_rb_expiry);
                
		if (!this.isDefined) {
			define (txw_trail);
			return 0;
		}

		updateTrail (txw_trail);
		return updateLead (txw_lead, now, nak_rb_expiry);
	}

/* Update trailing edge of receive window.
 */        
	private void updateTrail (SequenceNumber txw_trail)
	{
/* Pre-conditions */            
                assert (null != txw_trail);
                
/* Advertised trail is less than the current value */
		if (txw_trail.lte (this.rxw_trail))
			return;

/* Protocol sanity check: advertised trail jumps too far ahead */
		if (txw_trail.minus (this.rxw_trail).longValue() > ((UINT32_MAX/2)-1))
			return;

/* Retransmissions requests are constrained on startup until the advertised trail advances
 * beyond the first data sequence number.
 */
		if (this.isConstrained) {
			if (txw_trail.gt (this.rxw_trail_init))
				this.isConstrained = false;
			else
				return;
		}

		this.rxw_trail = txw_trail;
                
/* New value does not affect window */
                if (this.rxw_trail.lte (this.trail))
                        return;

/* Jump remaining sequence numbers if window is empty */
		if (isEmpty()) {
			final int distance = this.rxw_trail.minus (this.trail).intValue();
			this.trail = this.trail.plus (distance);
			this.commitLead = this.trail;
			this.lead = this.lead.plus (distance);

			this.cumulativeLosses += distance;
                        LOG.trace (RX_WINDOW_MARKER, "Data loss due to trailing edge update, fragment count {}.",
                                  this.fragmentCount);
                        assert (isEmpty());
                        assert (isCommitEmpty());
                        assert (isIncomingEmpty());
			return;
		}

/* Remove all buffers between commit lead and advertised rxw_trail */
		for (SequenceNumber sequence = this.commitLead;
		     this.rxw_trail.gt (sequence) && this.lead.gte (sequence);
		     sequence = sequence.plus (1))
		{
			SocketBuffer skb = peek (sequence);
			State state = (State)skb.getControlBuffer();
			switch (state.pktState) {
			case PKT_HAVE_DATA_STATE:
			case PKT_HAVE_PARITY_STATE:
			case PKT_LOST_DATA_STATE:
				break;
			case PKT_ERROR_STATE:
				System.exit (-1);
			default:
				markLost (sequence);
				break;
			}
		}
	}

/* Add one placeholder to leading edge due to detected lost packet.
 */
	private void addPlaceholder (long now, long nak_rb_expiry)
	{
/* Pre-conditions */
                assert (!isFull());
                
/* Advance lead */
		this.lead = this.lead.plus (1);

		SocketBuffer skb = new SocketBuffer (this.max_tpdu);
		skb.setControlBuffer (new State ());
		skb.setTimestamp (now);
		skb.setSequenceNumber (this.lead);
		State state = (State)skb.getControlBuffer();
		state.nakBackoffExpiration = nak_rb_expiry;

		if (!isFirstOfTransmissionGroup (this.lead)) {
			SocketBuffer first = peek (transmissionGroupSequenceNumber (this.lead));
			if (null != first) {
				State first_state = (State)first.getControlBuffer();
				first_state.isContiguous = false;
			}
		}

/* Add skb to window */
		final int index = (int)(this.lead.longValue() % getMaxLength());
		this.pdata[index] = skb;

		setPacketState (skb, PacketState.PKT_BACK_OFF_STATE);
                
/* Post-conditions */
                assert (getLength() > 0);
                assert (getLength() <= getMaxLength());
                assert (getIncomingLength() > 0);
	}

/* Add a range of placeholders to the window.
 * 
 * Returns:
 * RXW_BOUNDS: Incoming window is bound by commit window.
 * RXW_APPENDED: Place holders added.
 */
	private Returns addPlaceholderRange (SequenceNumber sequence, long now, long nak_rb_expiry)
	{
/* Pre-conditions */            
                assert (null != sequence);
                assert (sequence.gt (this.lead));
                
/* Check bounds of commit window */
		final int commit_length = sequence.plus (1).minus (this.trail).intValue();
		if (!isCommitEmpty() && (commit_length >= getMaxLength())) {
			updateLead (sequence, now, nak_rb_expiry);
			return Returns.RXW_BOUNDS;      /* Effectively a slow consumer */
		}
                
		if (isFull()) {
			LOG.trace (RX_WINDOW_MARKER, "Receive window full on placeholder sequence.");
			removeTrail();
		}
                
/* If packet is non-contiguous to current leading edge add place holders
 * TODO: can be rather inefficient on packet loss looping through dropped sequence numbers
 */
		while (!this.lead.plus (1).equals (sequence)) {
			addPlaceholder (now, nak_rb_expiry);
			if (isFull()) {
				LOG.trace (RX_WINDOW_MARKER, "Receive window full on placeholder sequence.");
				removeTrail();
			}
		}
                
/* Post-conditions */
                assert (!isFull());
                
		return Returns.RXW_APPENDED;
	}

/* Update leading edge of receiving window.
 * 
 * Returns number of place holders added.
 */
	private int updateLead (SequenceNumber txw_lead, long now, long nak_rb_expiry)
	{
		SequenceNumber lead = null;
		int lost = 0;
            
/* Pre-conditions */            
                assert (null != txw_lead);               

/* Advertised lead is less than the current value */
		if (txw_lead.lte (this.lead))
			return 0;

/* Committed packets limit constrain the lead until they are released */
		if (!isCommitEmpty() &&
		    txw_lead.minus (this.trail).intValue() >= getMaxLength())
		{
			lead = this.trail.plus (getMaxLength() - 1);
			if (lead.equals (this.lead))
				return 0;
		}
		else
			lead = txw_lead;

/* Count lost sequences */
		while (!this.lead.equals (lead))
		{
/* Slow consumer or fast producer */
			if (isFull()) {
                                assert (isCommitEmpty());
				LOG.trace (RX_WINDOW_MARKER, "Receive window full on window lead advancement.");
				removeTrail();
			}
			addPlaceholder (now, nak_rb_expiry);
			lost++;
		}

		return lost;
	}

/* Checks whether an APDU is unrecoverable due to lost TPDUs.
 */
	private boolean isApduLost (SocketBuffer skb)
	{
		State state = (State)skb.getControlBuffer();

/* Pre-conditions */                
                assert (null != skb);
                
/* Lost is lost */
		if (PacketState.PKT_LOST_DATA_STATE == state.pktState)
			return true;

/* By definition, a single-TPDU APDU is complete */
		if (!skb.isFragment())
			return false;

		final SequenceNumber apdu_first_sequence = skb.getFragmentOption().getFirstSequenceNumber();

/* By definition, first fragment indicates APDU is available */
		if (apdu_first_sequence.equals (skb.getSequenceNumber()))
			return false;

		final SocketBuffer first_skb = peek (apdu_first_sequence);
/* First fragment out-of-bounds */
		if (null == first_skb)
			return true;

		state = (State)first_skb.getControlBuffer();
		if (PacketState.PKT_LOST_DATA_STATE == state.pktState)
			return true;

		return false;
	}

/* Returns true if skb is a parity packet with packet length not
 * matching the transmission group length without the variable-packet-length
 * flag set.
 */        
	private boolean isInvalidVarPktLen (SocketBuffer skb)
	{
                assert (null != skb);
/* FEC not available, always return valid. */
		return false;
	}

	private boolean hasPayloadOption (SocketBuffer skb)
	{
                assert (null != skb);
		return skb.isFragment() || skb.getHeader().isOptionEncoded();
	}

/* Returns true if skb options are invalid when compared to the transmissino group
 */        
	private boolean isInvalidPayloadOption (SocketBuffer skb)
	{
                assert (null != skb);
/* FEC not available, always return valid. */
		return false;
	}

/* Insert skb into window range, discard if duplicate.  Window will have placeholder,
 * parity of data packet already matching sequence.
 * 
 * Returns:
 * PGM_RXW_INSERTED - packet filled a waiting placeholder, skb consumed.
 * PGM_RXW_DUPLICATE - re-transmission of previously seen packet.
 * PGM_RXW_MALFORMED - corrupted or invalid packet.
 * PGM_RXW_BOUNDS - packet out of window.
 */
	private Returns insert (SocketBuffer skb)
	{
		SocketBuffer placeholder = null;
		State state = null;

/* Pre-conditions */            
                assert (null != skb);
                assert (!isIncomingEmpty());

		if (isInvalidVarPktLen (skb) || isInvalidPayloadOption (skb)) {
			LOG.debug ("Invalid packet");
			return Returns.RXW_MALFORMED;
		}

		if (skb.getHeader().isParity()) {
			return Returns.RXW_MALFORMED;
		} else {
			final int index = (int)(skb.getSequenceNumber().longValue() % getMaxLength());
			placeholder = this.pdata[index];
			state = (State)placeholder.getControlBuffer();
			if (state.pktState == PacketState.PKT_HAVE_DATA_STATE)
				return Returns.RXW_DUPLICATE;
		}

/* APDU fragments are already declared lost */
		if (skb.isFragment() && isApduLost (skb)) {
			markLost (placeholder.getSequenceNumber());
			return Returns.RXW_BOUNDS;
		}

/* Verify placeholder state */                
		switch (state.pktState) {
		case PKT_BACK_OFF_STATE:
		case PKT_WAIT_NCF_STATE:
		case PKT_WAIT_DATA_STATE:
		case PKT_LOST_DATA_STATE:
			break;
		case PKT_HAVE_PARITY_STATE:
			shuffleParity (placeholder);
			break;
		default:
			System.exit (-1);
			break;
		}

/* Statistics */

/* Replace placeholder skb with incoming skb */
		final int index = (int)(skb.getSequenceNumber().longValue() % getMaxLength());
		this.pdata[index] = skb;
		setPacketState (skb, PacketState.PKT_HAVE_DATA_STATE);
		this.size += skb.getLength();

		return Returns.RXW_INSERTED;
	}

/* Shuffle parity packet at skb.sequence to any other needed spot.
 */        
	private void shuffleParity (SocketBuffer skb)
	{
                assert (null != skb);
/* no-op */
	}

/* skb advances the window lead.
 * 
 * Returns:
 * PGM_RXW_APPENDED - packet advanced window lead, skb consumed.
 * PGM_RXW_MALFORMED - corrupted or invalid packet.
 * PGM_RXW_BOUNDS - packet out of window.
 */
	private Returns append (SocketBuffer skb, long now)
	{
/* Pre-conditions */            
                assert (null != skb);
                
		if (isInvalidVarPktLen (skb) || isInvalidPayloadOption (skb)) {
			LOG.debug ("Invalid packet");
			return Returns.RXW_MALFORMED;
		}

		if (isFull()) {
			if (isCommitEmpty()) {
				LOG.trace (RX_WINDOW_MARKER, "Receive window full on new data, pulling trail.");
				removeTrail();
			} else {
				LOG.debug ("Receive window full with commit data.");
				return Returns.RXW_BOUNDS;      /* Constrained by commit window */
			}
		}

/* Advance leading edge */
		this.lead = this.lead.plus (1);

/* APDU fragments are already declared lost */
		if (skb.isFragment() && isApduLost (skb)) {
			SocketBuffer lost_skb = new SocketBuffer (this.max_tpdu);
			lost_skb.setControlBuffer (new State ());
			lost_skb.setTimestamp (now);
			lost_skb.setSequenceNumber (skb.getSequenceNumber());

/* Add lost-placeholder skb to window */
			final int index = (int)(lost_skb.getSequenceNumber().longValue() % getMaxLength());
			this.pdata[index] = skb;

			setPacketState (skb, PacketState.PKT_LOST_DATA_STATE);
			LOG.debug ("APDU already declared lost, ignoring TPDU.");
			return Returns.RXW_BOUNDS;
		}

/* Add skb to window */
		if (skb.getHeader().isParity()) {
			return Returns.RXW_MALFORMED;
		} else {
			final int index = (int)(skb.getSequenceNumber().longValue() % getMaxLength());
			this.pdata[index] = skb;
			setPacketState (skb, PacketState.PKT_HAVE_DATA_STATE);
		}

/* Statistics */
		this.size += skb.getLength();
			
		return Returns.RXW_APPENDED;
	}

/* Remove references to all commit packets not in the same transmission group
 * as the commit-lead
 */
	public void removeCommit()
	{
		final SequenceNumber tg_sqn_of_commit_lead = transmissionGroupSequenceNumber (this.commitLead);

		while (!isCommitEmpty() &&
			!tg_sqn_of_commit_lead.equals (transmissionGroupSequenceNumber (this.trail)))
		{
			removeTrail();
		}
	}

	public boolean hasCommitData()
	{
		return (this.committedCount > 0);
	}

/* Flush packets but instead of calling on_data append the contiguous data packets
 * to the provided scatter/gather vector.
 *
 * When transmission groups are enabled, packets remain in the windows tagged committed
 * until the transmission group has been completely committed.  This allows the packet
 * data to be used in parity calculations to recover the missing packets.
 *
 * Returns -1 on nothing read, returns length of bytes read, 0 is a valid read length.
 */
	public int read (List<SocketBuffer> skbs)
	{
		int bytes_read = -1;
                
/* Pre-conditions */
                assert (null != skbs);
  //              assert (skbs.capacity() > 0);
                
                LOG.debug ("read (\"skbs.size\": {})", skbs.size());
                
		if (isIncomingEmpty())
			return bytes_read;
                
		SocketBuffer skb = peek (this.commitLead);
                assert (null != skb);
                
		State state = (State)skb.getControlBuffer();
		switch (state.pktState) {
		case PKT_HAVE_DATA_STATE:
			bytes_read = incomingRead (skbs);
			break;
		case PKT_LOST_DATA_STATE:
/* Do not purge in situ sequence */
			if (isCommitEmpty()) {
				LOG.trace (RX_WINDOW_MARKER, "Removing lost trail from window");
				removeTrail();
			} else {
				LOG.trace (RX_WINDOW_MARKER, "Locking trail at commit window");
			}
/* Fall through */
		case PKT_BACK_OFF_STATE:
		case PKT_WAIT_NCF_STATE:
		case PKT_WAIT_DATA_STATE:
		case PKT_HAVE_PARITY_STATE:
			break;

		case PKT_COMMIT_DATA_STATE:
		case PKT_ERROR_STATE:
		default:
			System.exit (-1);
			break;
		}

		return bytes_read;
	}

/* Remove lost sequences from the trailing edge of the window.  lost sequence
 * at lead of commit window invalidates all parity-data packets as any
 * transmission group is now unrecoverable.
 *
 * Returns number of sequences purged.
 */
	private int removeTrail()
	{
/* Pre-conditions */
                assert (!isEmpty());
            
		SocketBuffer skb = peek (this.trail);
		clearPacketState (skb);
		this.size -= skb.getLength();
/* Remove reference to skb */
		skb = null;
		final boolean data_loss = this.trail.equals (this.commitLead);
		this.trail = this.trail.plus (1);
		if (data_loss) {
/* Data-loss */
			this.commitLead = this.commitLead.plus (1);
			this.cumulativeLosses++;
			LOG.trace (RX_WINDOW_MARKER, "Data loss due to pulled trailing edge, fragment count {}", this.fragmentCount);
			return 1;
		}
		return 0;
	}

/* Read contiguous APDU-grouped sequences from the incoming window.
 *
 * Side effects:
 *
 * 1) increments statics for window messages and bytes read.
 *
 * Returns count of bytes read.
 */
	private int incomingRead (List<SocketBuffer> skbs)
	{
		int bytes_read = 0;
		int data_read = 0;
                
/* Pre-conditions */
                assert (null != skbs);
//                assert (skbs.capacity() > 0);
                assert (!isIncomingEmpty());
                
		LOG.debug ("incomingRead (\"skbs.size\": {})", skbs.size());

		do {
			SocketBuffer skb = peek (this.commitLead);
                        assert (null != skb);
			if (isApduComplete (skb.isFragment() ? skb.getFragmentOption().getFirstSequenceNumber() : skb.getSequenceNumber()))
			{
				bytes_read += incomingReadApdu (skbs);
				data_read  ++;
			} else {
				break;
			}
		} while (!isIncomingEmpty());

		this.bytesDelivered    += bytes_read;
		this.messagesDelivered += data_read;
		return data_read > 0 ? bytes_read : -1;
	}

/* Returns TRUE if transmission group is lost.
 * 
 * Checking is lightly limited to bounds.
 */
	private boolean isTgSqnLost (SequenceNumber tg_sqn)
	{
/* Pre-conditions */            
                assert (null != tg_sqn);
                assert (packetSequence (tg_sqn) == 0);
                
		if (isEmpty())
			return true;

		if (tg_sqn.lt (this.trail))
			return true;

		return false;
	}

/* Check every TPDU in an APDU and verify that the data has arrived
 * and is available to commit to the application.
 *
 * If APDU sits in a transmission group that can be reconstructed use parity
 * data then the entire group will be decoded and any missing data packets
 * replaced by the recovery calculation.
 *
 * Packets with single fragment fragment headers must be normalised as regular
 * packets before calling.
 *
 * APDUs exceeding PGM_MAX_FRAGMENTS or PGM_MAX_APDU length will be discarded.
 *
 * Returns FALSE if APDU is incomplete or longer than max_len sequences.
 */
	private boolean isApduComplete (SequenceNumber firstSequence)
	{
/* Pre-conditions */            
                assert (null != firstSequence);
                
                LOG.debug ("isApduComplete (\"firstSequence\": {})", firstSequence);
                
		SocketBuffer skb = peek (firstSequence);
		if (null == skb)
			return false;

		final long apdu_size = skb.isFragment()? skb.getFragmentOption().getApduLength() : skb.getLength();
		final SequenceNumber tg_sqn = transmissionGroupSequenceNumber (firstSequence);

/* Protocol sanity check: maximum length */
		if (apdu_size > MAX_APDU) {
			markLost (firstSequence);
			return false;
		}

		int contiguous_tpdus = 0;
		int contiguous_size = 0;

		for (SequenceNumber sequence = firstSequence;
		     null != skb;
		     skb = peek (sequence = sequence.plus (1)))
		{
			State state = (State)skb.getControlBuffer();

			if (PacketState.PKT_HAVE_DATA_STATE != state.pktState)
			{
				return false;
			}

/* Single packet APDU, already complete */
			if (PacketState.PKT_HAVE_DATA_STATE == state.pktState && !skb.isFragment())
				return true;

/* Protocol sanity check: matching first sequence reference */
			if (!skb.getFragmentOption().getFirstSequenceNumber().equals (firstSequence)) {
				markLost (firstSequence);
				return false;
			}

/* Protocol sanity check: matching apdu length */
			if (skb.getFragmentOption().getApduLength() != apdu_size) {
				markLost (firstSequence);
				return false;
			}

/* Protocol sanity check: maximum number of fragments per apdu */
			if (++contiguous_tpdus > MAX_FRAGMENTS) {
				markLost (firstSequence);
				return false;
			}

			contiguous_size += skb.getLength();
			if (apdu_size == contiguous_size)
				return true;
			else if (apdu_size < contiguous_size) {
				markLost (firstSequence);
				return false;
			}
		}

/* Pending */
		return false;
	}

/* Read one APDU consisting of one or more TPDUs.  Target array is guaranteed
 * to be big enough to store complete APDU.
 */
	private int incomingReadApdu (List<SocketBuffer> skbs)
	{
		int contiguous_length = 0;
		int count = 0;
                
/* Pre-conditions */
                assert (null != skbs);
                
		LOG.debug ("incomingReadApdu (\"skbs.size\": {})", skbs.size());
                
		SocketBuffer skb = peek (this.commitLead);
                assert (null != skb);
                
		final long apdu_len = skb.isFragment() ? skb.getFragmentOption().getApduLength() : skb.getLength();
                assert (apdu_len >= skb.getLength());

		do {
			setPacketState (skb, PacketState.PKT_COMMIT_DATA_STATE);
			skbs.add (skb);
			contiguous_length += skb.getLength();
			this.commitLead = this.commitLead.plus (1);
			if (apdu_len == contiguous_length)
				break;
			skb = peek (this.commitLead);
		} while (apdu_len > contiguous_length);

/* Post-conditions */
                assert (!isCommitEmpty());
                
		return contiguous_length;
	}

/* Returns transmission group sequence (TG_SQN) from sequence (SQN).
 */
	private SequenceNumber transmissionGroupSequenceNumber (SequenceNumber sequence)
	{
/* Pre-conditions */            
                assert (null != sequence);
                
		final long tg_sqn_mask = 0xffffffff << this.tgSqnShift;
		return SequenceNumber.valueOf (sequence.longValue() & tg_sqn_mask);
	}

/* Returns packet number (PKT_SQN) from sequence (SQN).
 */        
	private int packetSequence (SequenceNumber sequence)
	{
/* Pre-conditions */            
                assert (null != sequence);
                
		final long tg_sqn_mask = 0xffffffff << this.tgSqnShift;
		return (int)(sequence.longValue() & ~tg_sqn_mask);
	}

/* Returns true when the sequence is the first of a transmission group.
 */        
	private boolean isFirstOfTransmissionGroup (SequenceNumber sequence)
	{
/* Pre-conditions */            
                assert (null != sequence);
                
		return packetSequence (sequence) == 0;
	}

/* Returns true when the sequence is the last of a transmission group.
 */        
	private boolean isLastOfTransmissionGroup (SequenceNumber sequence)
	{
/* Pre-conditions */            
                assert (null != sequence);
                
		return packetSequence (sequence) == (this.transmissionGroupSize - 1);
	}

/* Set PGM skbuff to new FSM state.
 */        
	private void setPacketState (SocketBuffer skb, PacketState newState)
	{
/* Pre-conditions */            
                assert (null != skb);
                assert (null != newState);
                
		State state = (State)skb.getControlBuffer();

/* Remove current state */                
		if (PacketState.PKT_ERROR_STATE != state.pktState)
			clearPacketState (skb);

		switch (newState) {
		case PKT_BACK_OFF_STATE:
			this.nakBackoffQueue.offer (skb);
			break;
		case PKT_WAIT_NCF_STATE:
			this.waitNakConfirmQueue.offer (skb);
			break;
		case PKT_WAIT_DATA_STATE:
			this.waitDataQueue.offer (skb);
			break;
		case PKT_HAVE_DATA_STATE:
			this.fragmentCount++;
			break;
		case PKT_HAVE_PARITY_STATE:
			this.parityCount++;
			break;
		case PKT_COMMIT_DATA_STATE:
			this.committedCount++;
			break;
		case PKT_LOST_DATA_STATE:
			this.lostCount++;
			this.cumulativeLosses++;
			this.hasEvent = true;
			break;
		case PKT_ERROR_STATE:
			break;
		default:
			System.exit (-1);
		}

		state.pktState = newState;
	}

/* Remove current state from sequence.
 */        
	private void clearPacketState (SocketBuffer skb)
	{
/* Pre-conditions */            
                assert (null != skb);
                
		State state = (State)skb.getControlBuffer();

		switch (state.pktState) {
		case PKT_BACK_OFF_STATE:
			this.nakBackoffQueue.remove (skb);
			break;
		case PKT_WAIT_NCF_STATE:
			this.waitNakConfirmQueue.remove (skb);
			break;
		case PKT_WAIT_DATA_STATE:
			this.waitDataQueue.remove (skb);
			break;
		case PKT_HAVE_DATA_STATE:
			this.fragmentCount--;
			break;
		case PKT_HAVE_PARITY_STATE:
			this.parityCount--;
			break;
		case PKT_COMMIT_DATA_STATE:
			this.committedCount--;
			break;
		case PKT_LOST_DATA_STATE:
			this.lostCount--;
			break;
		case PKT_ERROR_STATE:
			break;
		default:
			System.exit (-1);
		}

		state.pktState = PacketState.PKT_ERROR_STATE;
	}

/* Mark an existing sequence lost due to failed recovery.
 */
	public void markLost (SequenceNumber sequence)
	{
/* Pre-conditions */            
                assert (null != sequence);
                assert (!isEmpty());
                
		LOG.debug ("markLost (\"sequence\": {})", sequence);
                
		SocketBuffer skb = peek (sequence);
                assert (null != skb);
                
		setPacketState (skb, PacketState.PKT_LOST_DATA_STATE);
	}

/* Received a uni/multicast ncf, search for a matching nak & tag or extend window if
 * beyond lead
 *
 * Returns:
 * PGM_RXW_BOUNDS - sequence is outside of window, or window is undefined.
 * PGM_RXW_UPDATED - receiver state updated, waiting for data.
 * PGM_RXW_DUPLICATE - data already exists at sequence.
 * PGM_RXW_APPENDED - lead is extended with state set waiting for data.
 */        
	private int confirm (SequenceNumber sequence)
	{
                assert (null != sequence);
		return -1;
	}

/* Update an incoming sequence with state transition to WAIT-DATA.
 *
 * Returns:
 * PGM_RXW_UPDATED - receiver state updated, waiting for data.
 * PGM_RXW_DUPLICATE - data already exists at sequence.
 */        
	private int recoveryUpdate (SequenceNumber sequence)
	{
                assert (null != sequence);
		return -1;
	}

/* Append an skb to the incoming window with WAIT-DATA state.
 *
 * Returns:
 * PGM_RXW_APPENDED - lead is extended with state set waiting for data.
 * PGM_RXW_BOUNDS   - constrained by commit window
 */        
	private int recoveryAppend()
	{
		return -1;
	}

	public boolean hasEvent()
	{
		return this.hasEvent;
	}

	public void clearEvent()
	{
		this.hasEvent = false;
	}

	public long getCumulativeLosses()
	{
		return this.cumulativeLosses;
	}

/* Dump window state */        
        @Override
	public String toString() {
		return	"{" +
				  "\"tsi\": \"" + this.tsi + "\"" +
				", \"nakBackoffQueue\": { \"length\": " + this.nakBackoffQueue.size() + " }" +
				", \"waitNakConfirmQueue\": { \"length\": " + this.waitNakConfirmQueue.size() + " }" +
				", \"waitDataQueue\": { \"length\": " + this.waitDataQueue.size() + " }" +
				", \"lostCount\": " + this.lostCount + "" +
				", \"fragmentCount\": " + this.fragmentCount + "" +
				", \"committedCount\": " + this.committedCount + "" +
				", \"max_tpdu\": " + this.max_tpdu + "" +
				", \"lead\": " + this.lead + "" +
				", \"trail\": " + this.trail + "" +
				", \"RXW_TRAIL\": " + this.rxw_trail + "" +
				", \"RXW_TRAIL_INIT\": " + this.rxw_trail_init + "" +
				", \"commitLead\": " + this.commitLead + "" +
				", \"isConstrained\": " + this.isConstrained + "" +
				", \"isDefined\": " + this.isDefined + "" +
				", \"hasEvent\": " + this.hasEvent + "" +
				", \"cumulativeLosses\": " + this.cumulativeLosses + "" +
				", \"bytesDelivered\": " + this.bytesDelivered + "" +
				", \"messagesDelivered\": " + this.messagesDelivered + "" +
				", \"size\": " + this.size + "" +
				", \"alloc\": " + this.alloc + "" +
			"}";
	}
}

/* eof */