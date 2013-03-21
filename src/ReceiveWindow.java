/* Receive window as a ring buffer.
 */
import java.io.*;
import java.net.*;
import java.util.*;

public class ReceiveWindow {

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

	public static final long UINT32_MAX		= 4294967295L;
	public static final int MAX_FRAGMENTS		= 16;
	public static final int MAX_APDU		= MAX_FRAGMENTS * 1500;

	protected TransportSessionId		tsi;

	private class RxPacket {
		OriginalDataPacket		packet;
		long				nakRandomBackoffExpiry;
		long				nakRepeatExpiry;
		long				nakRepairDataExpiry;
		PacketState			state;
		int				nakTransmitCount;
		int				nakConfirmRetryCount;
		int				dataRetryCount;
		boolean				isContiguous;

		public RxPacket (OriginalDataPacket packet, PacketState state)
		{
			this.packet = packet;
			this.state = state;
		}

		public RxPacket (PacketState state)
		{
			this.packet = null;
			this.state = state;
		}
	}

	protected Queue<RxPacket>		backoffQueue;
	protected Queue<RxPacket>		waitNakConfirmQueue;
	protected Queue<RxPacket>		waitDataQueue;

	protected long				lostCount;
	protected long				fragmentCount;
	protected long				parityCount;
	protected long				committedCount;

	protected int				max_tpdu;
	protected SequenceNumber		lead, trail;
	protected SequenceNumber		rxw_trail, rxw_trail_init;
	protected SequenceNumber		commitLead;
	protected boolean			isConstrained = true;
	protected boolean			isDefined = false;
	protected boolean			hasEvent = false;
	protected boolean			isFecAvailable = false;
	protected long				transmissionGroupSize;
	protected long				tgSqnShift;

/* TODO: set from socket. */
	protected int				nak_bo_ivl = 50 * 1000;

	protected long				minFillTime;
	protected long				maxFillTime;
	protected long				minNakTransmitCount;
	protected long				maxNakTransmitCount;
	protected long				cumulativeLosses;
	protected long				bytesDelivered;
	protected long				messagesDelivered;

	protected long				size;
	protected long				alloc;
	protected ArrayList<RxPacket>		pdata = null;

	private RxPacket peek (SequenceNumber sequence)
	{
		if (this.isEmpty())
			return null;

		if (sequence.gte (this.trail) && sequence.lte (this.lead))
		{
/* index into ArrayList must be an int not a long:
 * error: possible loss of precision
 */
			final int index = (int)(sequence.longValue() % this.maxLength());
			RxPacket packet = this.pdata.get (index);
			return packet;
		}

		return null;
	}

	private long commitLength()
	{
		return (long)(this.commitLead.longValue() - this.trail.longValue());
	}

	private boolean commitIsEmpty()
	{
		return this.commitLength() == 0;
	}

	private long incomingLength()
	{
		return (long)((1 + this.lead.longValue()) - this.commitLead.longValue());
	}

	private boolean incomingIsEmpty()
	{
		return this.incomingLength() == 0;
	}

	private long maxLength()
	{
		return this.alloc;
	}

	private long length()
	{
		return (long)((1 + this.lead.longValue()) - this.trail.longValue());
	}

	private long size()
	{
		return this.size;
	}

	private boolean isEmpty()
	{
		return this.length() == 0;
	}

	private boolean isFull()
	{
		return this.length() == this.maxLength();
	}

	private long lead()
	{
		return this.lead.longValue();
	}

	private long nextLead()
	{
		return (long)(lead() + 1);
	}

	public ReceiveWindow (
		TransportSessionId tsi,
		int tpdu_size,			/* unsigned 16-bit */
		int sqns,			/* unsigned */
		int secs,			/* unsigned */
		long max_rte
		)
	{
		final int alloc_sqns = sqns > 0 ? sqns : (int)((secs * max_rte) / tpdu_size);
		this.pdata = new ArrayList<RxPacket> (alloc_sqns);

		this.tsi = tsi;
		this.max_tpdu = tpdu_size;

/* empty state:
 *
 * trail = 0, lead = -1
 * commit_trail = commit_lead = rxw_trail = rxw_trail_init = 0
 */
		this.lead = new SequenceNumber (-1);
		this.trail = new SequenceNumber ((long)(this.lead.longValue() + 1));

		this.commitLead = new SequenceNumber (0);
		this.rxw_trail = new SequenceNumber (0);
		this.rxw_trail_init = new SequenceNumber (0);

		this.tgSqnShift = 0;

/* RxPacket array */
		this.alloc = alloc_sqns;
	}

/* Returns:
 * PGM_RXW_INSERTED - packet filled a waiting placeholder, skb consumed.
 * PGM_RXW_APPENDED - packet advanced window lead, skb consumed.
 * PGM_RXW_MISSING - missing packets detected whilst window lead was adanced, skb consumed.
 * PGM_RXW_DUPLICATE - re-transmission of previously seen packet.
 * PGM_RXW_MALFORMED - corrupted or invalid packet.
 * PGM_RXW_BOUNDS - packet out of window.
 */
	public Returns add (OriginalDataPacket packet)
	{
		System.out.println ("add ( " +
					"\"packet\": " + packet + "" +
					" )");

		Returns status;
		final SequenceNumber sequence = new SequenceNumber (packet.getSequenceNumber());

/* protocol sanity check: valid trail pointer wrt. sequence */
		if (sequence.longValue() - packet.getTrail() >= ((UINT32_MAX/2)-1))
			return Returns.RXW_BOUNDS;

/* drop parity packets */
		if (packet.isParity()) {
			System.out.println ("Unsupported parity packet");
			return Returns.RXW_MALFORMED;
		}

		if (!this.isDefined)
			this.define (new SequenceNumber (sequence.longValue() - 1));
		else
			this.updateTrail (new SequenceNumber (packet.getTrail()));

		if (sequence.lt (this.commitLead)) {
			if (sequence.gte (this.trail))
				return Returns.RXW_DUPLICATE;
			else
				return Returns.RXW_BOUNDS;
		}

System.out.println ("sequence: " + sequence.longValue() + ", lead: " + this.lead.longValue());
		if (sequence.lte (this.lead)) {
			this.hasEvent = true;
			return this.insert (packet);
		}

		if (sequence.longValue() == this.nextLead()) {
			this.hasEvent = true;
			return this.append (packet);
		}

		status = this.addPlaceholderRange (sequence);
		if (Returns.RXW_APPENDED == status) {
			status = this.append (packet);
			if (Returns.RXW_APPENDED == status)
				status = Returns.RXW_MISSING;
		}

		return status;
	}

	private void define (SequenceNumber lead)
	{
System.out.println ("defining window");
		this.lead.assign (lead);
		this.trail.assign (this.lead.longValue() + 1);
		this.rxw_trail_init.assign (this.trail);
		this.rxw_trail.assign (this.rxw_trail_init);
		this.commitLead.assign (this.rxw_trail);
		this.isConstrained = this.isDefined = true;
	}

	private void updateTrail (SequenceNumber txw_trail)
	{
System.out.println ("updating trail");
/* advertised trail is less than the current value */
		if (txw_trail.lte (this.rxw_trail))
			return;

/* protocol sanity check: advertised trail jumps too far ahead */
		if (txw_trail.longValue() - this.rxw_trail.longValue() > ((UINT32_MAX/2)-1))
			return;

/* retransmissions requests are constrained on startup until the advertised trail advances
 * beyond the first data sequence number.
 */
		if (this.isConstrained) {
			if (txw_trail.gt (this.rxw_trail_init))
				this.isConstrained = false;
			else
				return;
		}

		this.rxw_trail.assign (txw_trail);

/* jump remaining sequence numbers if window is empty */
		if (this.isEmpty()) {
			final long distance = (long)(this.rxw_trail.longValue() - this.trail.longValue());
			this.trail.assign (this.trail.longValue() + distance);
			this.commitLead.assign (this.trail);
			this.lead.assign (this.lead.longValue() + distance);

			this.cumulativeLosses += distance;
			return;
		}

/* remove all buffers between commit lead and advertised rxw_trail */
		for (SequenceNumber sequence = this.commitLead;
		     this.rxw_trail.gt (sequence) && this.lead.gte (sequence);
		     sequence.increment())
		{
			RxPacket packet = this.peek (sequence);
			switch (packet.state) {
			case PKT_HAVE_DATA_STATE:
			case PKT_HAVE_PARITY_STATE:
			case PKT_LOST_DATA_STATE:
				break;
			case PKT_ERROR_STATE:
				System.exit (-1);
			default:
				this.markLost (sequence);
				break;
			}
		}
	}

/* add one placeholder to leading edge due to detected lost packet.
 */
	private void addPlaceholder()
	{
/* advance lead */
		this.lead.increment();

		RxPacket packet = new RxPacket (PacketState.PKT_BACK_OFF_STATE);
		packet.nakRandomBackoffExpiry = this.calculateNakRandomBackoffInterval();

		if (!this.isFirstOfTransmissionGroup (this.lead.longValue())) {
			RxPacket first = this.peek (new SequenceNumber (this.transmissionGroupSequenceNumber (this.lead)));
			if (null != first)
				first.isContiguous = false;
		}

/* add skb to window */
		final int index = (int)(this.lead.longValue() % this.maxLength());
		this.pdata.add (index, packet);
	}

/* Returns:
 * RXW_BOUNDS: Incoming window is bound by commit window.
 * RXW_APPENDED: Place holders added.
 */
	private Returns addPlaceholderRange (SequenceNumber sequence)
	{
/* check bounds of commit window */
		final long newCommitSequence = (long)((1 + sequence.longValue()) - this.trail.longValue());
		if (!this.commitIsEmpty() && (newCommitSequence >= this.maxLength())) {
			this.updateLead (sequence);
			return Returns.RXW_BOUNDS;
		}
		if (this.isFull()) {
			System.out.println ("Receive window full on placeholder sequence.");
			this.removeTrail();
		}
/* if packet is non-contiguous to current leading edge add place holders
 * TODO: can be rather inefficient on packet loss looping through dropped sequence numbers
 */
		while (this.nextLead() != sequence.longValue()) {
			this.addPlaceholder();
			if (this.isFull()) {
				System.out.println ("Receive window full on placeholder sequence.");
				this.removeTrail();
			}
		}
		return Returns.RXW_APPENDED;
	}

/* Returns number of place holders added.
 */
	private int updateLead (SequenceNumber lead)
	{
		return 0;
	}

	private boolean isApduLost (OriginalDataPacket data)
	{
		return true;
	}

	private void findMissing()
	{
	}

	private boolean isInvalidVarPktLen (OriginalDataPacket data)
	{
/* FEC not available, always return valid. */
		return false;
	}

	private boolean hasPayloadOp()
	{
/* ignore fragments */
		return false;
	}

	private boolean isInvalidPayloadOp (OriginalDataPacket data)
	{
/* FEC not available, always return valid. */
		return false;
	}

/* Returns:
 * PGM_RXW_INSERTED - packet filled a waiting placeholder, skb consumed.
 * PGM_RXW_DUPLICATE - re-transmission of previously seen packet.
 * PGM_RXW_MALFORMED - corrupted or invalid packet.
 * PGM_RXW_BOUNDS - packet out of window.
 */
	private Returns insert (OriginalDataPacket data)
	{
		RxPacket packet = null;

		if (this.isInvalidVarPktLen (data) || this.isInvalidPayloadOp (data)) {
			System.out.println ("Invalid packet");
			return Returns.RXW_MALFORMED;
		}

		if (data.isParity()) {
			return Returns.RXW_MALFORMED;
		} else {
			final int index = (int)(data.getSequenceNumber() % this.maxLength());
			packet = this.pdata.get (index);
			if (packet.state == PacketState.PKT_HAVE_DATA_STATE)
				return Returns.RXW_DUPLICATE;
		}

/* APDU fragments are already declared lost */
		if (data.isFragment() && this.isApduLost (data)) {
			this.markLost (new SequenceNumber (data.getSequenceNumber()));
			return Returns.RXW_BOUNDS;
		}

		switch (packet.state) {
		case PKT_BACK_OFF_STATE:
		case PKT_WAIT_NCF_STATE:
		case PKT_WAIT_DATA_STATE:
		case PKT_LOST_DATA_STATE:
			break;
		case PKT_HAVE_PARITY_STATE:
			this.shuffleParity();
			break;
		default:
			System.exit (-1);
			break;
		}

/* statistics */

/* replace placeholder skb with incoming skb */
		final int index = (int)(data.getSequenceNumber() % this.maxLength());
		this.pdata.add (index, new RxPacket (data, PacketState.PKT_HAVE_DATA_STATE));
		this.size += data.getTsduLength();

		return Returns.RXW_INSERTED;
	}

	private void shuffleParity()
	{
	}

/* Returns:
 * PGM_RXW_APPENDED - packet advanced window lead, skb consumed.
 * PGM_RXW_MALFORMED - corrupted or invalid packet.
 * PGM_RXW_BOUNDS - packet out of window.
 */
	private Returns append (OriginalDataPacket data)
	{
		if (this.isInvalidVarPktLen (data) || this.isInvalidPayloadOp (data)) {
			System.out.println ("Invalid packet");
			return Returns.RXW_MALFORMED;
		}

		if (this.isFull()) {
			if (this.commitIsEmpty()) {
				System.out.println ("Receive window full on new data.");
				this.removeTrail();
			} else {
				return Returns.RXW_BOUNDS;
			}
		}

/* advance leading edge */
		this.lead.increment();

/* APDU fragments are already declared lost */
		if (data.isFragment() && this.isApduLost (data)) {
			return Returns.RXW_BOUNDS;
		}

/* add skb to window */
		if (data.isParity()) {
			return Returns.RXW_MALFORMED;
		} else {
			final int index = (int)(data.getSequenceNumber() % this.maxLength());
			this.pdata.add (index, new RxPacket (data, PacketState.PKT_HAVE_DATA_STATE));
		}

/* statistics */
		this.size += data.getTsduLength();
			
		return Returns.RXW_APPENDED;
	}

/* remove references to all commit packets not in the same transmission group
 * as the commit-lead
 */
	public void removeCommit()
	{
		final long tg_sqn_of_commit_lead = this.transmissionGroupSequenceNumber (this.commitLead);
		while (!this.commitIsEmpty() &&
			tg_sqn_of_commit_lead != this.transmissionGroupSequenceNumber (this.trail))
		{
			this.removeTrail();
		}
	}

/* flush packets but instead of calling on_data append the contiguous data packets
 * to the provided scatter/gather vector.
 *
 * when transmission groups are enabled, packets remain in the windows tagged committed
 * until the transmission group has been completely committed.  this allows the packet
 * data to be used in parity calculations to recover the missing packets.
 *
 * returns -1 on nothing read, returns length of bytes read, 0 is a valid read length.
 */
	public int read (byte[] buffer, int buffer_length)
	{
System.out.println ("read");
		int bytes_read = -1;
		if (this.incomingIsEmpty())
			return bytes_read;
		RxPacket packet = this.peek (this.commitLead);
		switch (packet.state) {
		case PKT_HAVE_DATA_STATE:
			bytes_read = this.incomingRead (buffer, buffer_length);
			break;
		case PKT_LOST_DATA_STATE:
/* do not purge in situ sequence */
			if (this.commitIsEmpty()) {
				System.out.println ("Removing lost trail from window");
				this.removeTrail();
			} else {
				System.out.println ("Locking trail at commit window");
			}
/* fall through */
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

/* remove lost sequences from the trailing edge of the window.  lost sequence
 * at lead of commit window invalidates all parity-data packets as any
 * transmission group is now unrecoverable.
 *
 * returns number of sequences purged.
 */
	private int removeTrail()
	{
		RxPacket packet = this.peek (this.trail);
		this.clearState (packet);
		this.size -= packet.packet.getTsduLength();
		this.trail.increment();
		if (this.trail.equals (this.commitLead)) {
/* data-loss */
			this.commitLead.increment();
			this.cumulativeLosses++;
			System.out.println ("Data loss due to pulled trailing edge, fragment count " + this.fragmentCount);
			return 1;
		}
		return 0;
	}

/* read contiguous APDU-grouped sequences from the incoming window.
 *
 * side effects:
 *
 * 1) increments statics for window messages and bytes read.
 *
 * returns count of bytes read.
 */
	private int incomingRead (byte[] buffer, int buffer_length)
	{
		System.out.println ("incomingRead");
		int bytes_read = 0;
		int data_read = 0;

		do {
			RxPacket packet = this.peek (this.commitLead);
			if (this.isApduComplete (new SequenceNumber (packet.packet.getFirstSequenceNumber()))) {
				bytes_read += this.incomingReadApdu (buffer, bytes_read, buffer_length - bytes_read);
				data_read  ++;
			} else {
				break;
			}
		} while (bytes_read <= buffer_length && !this.incomingIsEmpty());

		this.bytesDelivered    += bytes_read;
		this.messagesDelivered += data_read;
		return data_read > 0 ? bytes_read : -1;
	}

	private boolean isTgSqnLost (long tg_sqn)
	{
		return true;
	}

	private void reconstruct (long tg_sqn)
	{
	}

/* check every TPDU in an APDU and verify that the data has arrived
 * and is available to commit to the application.
 *
 * if APDU sits in a transmission group that can be reconstructed use parity
 * data then the entire group will be decoded and any missing data packets
 * replaced by the recovery calculation.
 *
 * packets with single fragment fragment headers must be normalised as regular
 * packets before calling.
 *
 * APDUs exceeding PGM_MAX_FRAGMENTS or PGM_MAX_APDU length will be discarded.
 *
 * returns FALSE if APDU is incomplete or longer than max_len sequences.
 */
	private boolean isApduComplete (SequenceNumber firstSequence)
	{
		RxPacket packet = this.peek (firstSequence);
		if (null == packet)
			return false;

		final long apdu_size = packet.packet.getApduLength();
		final long tg_sqn = this.transmissionGroupSequenceNumber (firstSequence);

/* protocol sanity check: maximum length */
		if (apdu_size > MAX_APDU) {
			this.markLost (firstSequence);
			return false;
		}

		int contiguous_tpdus = 0;
		int contiguous_size = 0;

		for (SequenceNumber sequence = firstSequence;
		     null != packet;
		     packet = this.peek (sequence.increment()))
		{
			if (PacketState.PKT_HAVE_DATA_STATE != packet.state)
			{
				return false;
			}

/* single packet APDU, already complete */
			if (PacketState.PKT_HAVE_DATA_STATE == packet.state && !packet.packet.isFragment())
				return true;

/* protocol sanity check: matching first sequence reference */
			if (packet.packet.getFirstSequenceNumber() != firstSequence.longValue()) {
				this.markLost (firstSequence);
				return false;
			}

/* protocol sanity check: matching apdu length */
			if (packet.packet.getApduLength() != apdu_size) {
				this.markLost (firstSequence);
				return false;
			}

/* protocol sanity check: maximum number of fragments per apdu */
			if (++contiguous_tpdus > MAX_FRAGMENTS) {
				this.markLost (firstSequence);
				return false;
			}

			contiguous_size += packet.packet.getTsduLength();
			if (apdu_size == contiguous_size)
				return true;
			else if (apdu_size < contiguous_size) {
				this.markLost (firstSequence);
				return false;
			}
		}

/* pending */
		return false;
	}

/* read one APDU consisting of one or more TPDUs.  target array is guaranteed
 * to be big enough to store complete APDU.
 */
	private int incomingReadApdu (byte[] buffer, int offset, int length)
	{
		System.out.println ("incomingReadApdu");
		int contiguous_length = 0;
		int count = 0;
		RxPacket packet = this.peek (this.commitLead);
		final long apdu_len = packet.packet.getApduLength();

		do {
			this.setState (packet, PacketState.PKT_COMMIT_DATA_STATE);
			System.arraycopy (packet.packet.getData(), 0,
					  buffer, offset,
					  packet.packet.getTsduLength());
			contiguous_length += packet.packet.getTsduLength();
			this.commitLead.increment();
			if (apdu_len == contiguous_length)
				break;
			packet = this.peek (this.commitLead);
		} while (apdu_len > contiguous_length);

		return contiguous_length;
	}

/* returns transmission group sequence (TG_SQN) from sequence (SQN).
 */
	private long transmissionGroupSequenceNumber (SequenceNumber sequence)
	{
		final long tg_sqn_mask = 0xffffffff << this.tgSqnShift;
		return sequence.longValue() & tg_sqn_mask;
	}

	private long packetSequence (long sequence)
	{
		return 0;
	}

	private boolean isFirstOfTransmissionGroup (long sequence)
	{
		return this.packetSequence (sequence) == 0;
	}

	private boolean isLastOfTransmissionGroup (long sequence)
	{
		return this.packetSequence (sequence) == this.transmissionGroupSize - 1;
	}

	private void setState (RxPacket packet, PacketState newState)
	{
		if (PacketState.PKT_ERROR_STATE != packet.state)
			this.clearState (packet);

		switch (newState) {
		case PKT_BACK_OFF_STATE:
			this.backoffQueue.offer (packet);
			break;
		case PKT_WAIT_NCF_STATE:
			this.waitNakConfirmQueue.offer (packet);
			break;
		case PKT_WAIT_DATA_STATE:
			this.waitDataQueue.offer (packet);
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

		packet.state = newState;
	}

	private void clearState (RxPacket packet)
	{
		switch (packet.state) {
		case PKT_BACK_OFF_STATE:
			this.backoffQueue.remove (packet);
			break;
		case PKT_WAIT_NCF_STATE:
			this.waitNakConfirmQueue.remove (packet);
			break;
		case PKT_WAIT_DATA_STATE:
			this.waitDataQueue.remove (packet);
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

		packet.state = PacketState.PKT_ERROR_STATE;
	}

/* mark an existing sequence lost due to failed recovery.
 */
	private void markLost (SequenceNumber sequence)
	{
		System.out.println ("markLost ( " +
					"\"sequence\": " + sequence + "" +
					" )");
		RxPacket packet = this.peek (sequence);
		packet.state = PacketState.PKT_LOST_DATA_STATE;
	}

	private int confirm (long sequence)
	{
		return -1;
	}

	private int recoveryUpdate (long sequence)
	{
		return -1;
	}

	private int recoveryAppend()
	{
		return -1;
	}

/* The java Math library function Math.random() generates a double value in the
 * range [0,1). Notice this range does not include the 1.
 * Reference: http://stackoverflow.com/a/363732/175849
 */
	private int randomIntRange (int begin, int end)
	{
		return begin + (int)(Math.random() * ((end - begin) + 1));
	}

/* calculate NAK_RB_IVL as random time interval 1 - NAK_BO_IVL.
 */
	private int calculateNakRandomBackoffInterval()
	{
		return this.randomIntRange (1 /* us */, this.nak_bo_ivl);
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

	public String toString() {
		return	"{" +
				  "\"lead\": " + this.lead.longValue() + "" +
				", \"trail\": " + this.trail.longValue() + "" +
				", \"RXW_TRAIL\": " + this.rxw_trail.longValue() + "" +
				", \"RXW_TRAIL_INIT\": " + this.rxw_trail_init.longValue() + "" +
				", \"commitLead\": " + this.commitLead.longValue() + "" +
				", \"isConstrained\": " + this.isConstrained + "" +
				", \"isDefined\": " + this.isDefined + "" +
				", \"hasEvent\": " + this.hasEvent + "" +
				", \"size\": " + this.size + "" +
			"}";
	}

}

/* eof */
