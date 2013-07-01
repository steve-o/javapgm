/* Transmit window as a ring buffer.
 */
package hk.miru.javapgm;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

public class TransmitWindow {
    
        private static Logger LOG = LogManager.getLogger (TransmitWindow.class.getName());
        private static final Marker TX_WINDOW_MARKER = MarkerManager.getMarker ("TX_WINDOW");

	protected TransportSessionId tsi;

	private class State implements ControlBuffer {
                int     unfolded_checksum;      /* First 32-bit word must be checksum */
                
                boolean waiting_retransmit;     /* In retransmit queue */
                int     retransmit_count;
                int     nak_elimination_count;
                
                int     pkt_cnt_requested;      /* # parity packets to send */
                int     pkt_cnt_sent;           /* # parity packets already sent */
	}

	protected SequenceNumber	lead;
	protected SequenceNumber	trail;
        
	protected Queue<SocketBuffer>	retransmitQueue;
        
	protected int			size;
	protected int			alloc;
	protected SocketBuffer[]	pdata = null;

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
                        assert (null != skb);
                        assert (SocketBuffer.isValid (skb));
                        assert (null == skb.getTransportSessionId());
			return skb;
		}

		return null;
	}
        
        public final SequenceNumber getTrail() {
                return this.trail;
        }
        
        public final SequenceNumber getLead() {
                return this.lead;
        }

        public final SequenceNumber getNextLead() {
                return this.lead.plus (1);
        }
        
        public boolean canPeekRetransmit() {
                return (null != this.tryPeekRetransmit());
        }
        
	public int getUnfoldedChecksum (SocketBuffer skb) {
		State state = (State)skb.getControlBuffer();
		return state.unfolded_checksum;
	}

	public void setUnfoldedChecksum (SocketBuffer skb, int csum) {
		State state = (State)skb.getControlBuffer();
		state.unfolded_checksum = csum;
	}
        
        public void incrementRetransmitCount (SocketBuffer skb) {
		State state = (State)skb.getControlBuffer();
                state.retransmit_count++;
        }
        
	public boolean isRetransmitEmpty() {
		return this.retransmitQueue.isEmpty();
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

/* Constructor for transmit window.  Zero-length windows are not permitted.
 */        
	public TransmitWindow (
		TransportSessionId tsi,
		int tpdu_size,			/* unsigned 16-bit */
		int sqns,			/* unsigned */
		int secs,			/* unsigned */
		long max_rte
		)
	{
/* Pre-conditions */            
                assert (null != tsi);
                if (sqns != 0) {
                        assert (tpdu_size == 0);
                        assert (sqns > 0);
                        assert (secs == 0);
                        assert (max_rte == 0);
                } else {
                        assert (tpdu_size > 0);
                        assert (secs > 0);
                        assert (max_rte > 0);
                }
                
                LOG.debug ("create (\"tsi\":{} \"tpdu-size\":{} \"sqns\":{} \"secs\":{} \"max-rte\":{})",
                           tsi, tpdu_size, sqns, secs, max_rte);
                
/* Calculate transmit window parameters */
                assert (sqns > 0 || (tpdu_size > 0 && secs > 0 && max_rte > 0));
		final int alloc_sqns = sqns > 0 ? sqns : (int)((secs * max_rte) / tpdu_size);
		this.pdata = new SocketBuffer[alloc_sqns];

		this.tsi = tsi;

/* Empty state for transmission group boundaries to align.
 *
 * trail = 0, lead = -1
 */
		this.lead = SequenceNumber.MAX_VALUE;
		this.trail = this.lead.plus (1);

/* Skbuff array */
		this.alloc = alloc_sqns;

/* Concurrent to permit modification during iteration without a listIterator. */
		this.retransmitQueue = new ConcurrentLinkedQueue<> ();
                
/* Post-conditions */
                assert (getMaxLength() == alloc_sqns);
                assert (getLength() == 0);
                assert (getSize() == 0);
                assert (isEmpty());
                assert (!isFull());
                assert (!canPeekRetransmit());
	}

/* Add skb to transmit window, taking ownership.  Window does not grow.
 * PGM skbuff data/tail pointers must point to the PGM payload, and hence skb->len
 * is allowed to be zero.
 *
 * Side effects:
 *
 * 1) Sequence number is set in skb.
 * 2) Window is updated with new skb.
 *
 * No return value.  Fatal error raised on invalid parameters.  If window is full then
 * an entry is dropped to fulfil the request.
 *
 * It is an error to try to free the skb after adding to the window.
 */
        public void add (SocketBuffer skb)
	{
/* Pre-conditions */                
                assert (null != skb);
                assert (getMaxLength() > 0);
                assert (SocketBuffer.isValid (skb));
                
                LOG.debug ("add (\"skb\": {}})", skb);

		skb.setControlBuffer (new State ());
                
                if (isFull()) {
/* Transmit window advancement scheme dependent action here */
                        removeTail();
                }
                
/* generate new sequence number */
                this.lead = this.lead.plus (1);
		skb.setSequenceNumber (this.lead);

/* Add skb to window */
		final int index = (int)(this.lead.longValue() % getMaxLength());
		this.pdata[index] = skb;
                
/* Statistics */
                this.size += skb.getLength();
                
/* Post-conditions */               
                assert (getLength() > 0);
                assert (getLength() <= getMaxLength());
	}

/* Remove an entry from the trailing edge of the transmit window.
 */        
	private void removeTail()
	{
                LOG.debug ("removeTail");
            
/* Pre-conditions */
                assert (!isEmpty());
            
		SocketBuffer skb = peek (this.trail);
                assert (null != skb);
                assert (SocketBuffer.isValid (skb));

                State state = (State)skb.getControlBuffer();
		if (state.waiting_retransmit) {
                        this.retransmitQueue.remove (skb);
                        state.waiting_retransmit = false;
                }
                
/* Statistics */                
		this.size -= skb.getLength();
                
/* Remove reference to skb */
		skb = null;
                
/* Advance trailing pointer */
                this.trail = this.trail.plus (1);

/* Post-conditions */
                assert (!isFull());
        }

/* Try to add a sequence number to the retransmit queue, ignore if
 * already there or no longer in the transmit window.
 *
 * For parity NAKs, we deal on the transmission group sequence number
 * rather than the packet sequence number.  To simplify managment we
 * use the leading window packet to store the details of the entire
 * transmisison group.  Parity NAKs are ignored if the packet count is
 * less than or equal to the count already queued for retransmission.
 *
 * Returns FALSE if request was eliminated, returns TRUE if request was
 * added to queue.
 */ 
        public boolean pushRetransmit (SequenceNumber sequence) {
                LOG.debug ("pushRetransmit");
                
/* Early elimination */
                if (isEmpty())
                        return false;
                
                return pushSelectiveRetransmit (sequence);
        }
        
        private boolean pushSelectiveRetransmit (SequenceNumber sequence) {
                SocketBuffer skb = peek (sequence);
                if (null == skb) {
                        LOG.trace (TX_WINDOW_MARKER, "Requested packet {} not in window.", sequence);
                        return false;
                }
                
                assert (SocketBuffer.isValid (skb));
                State state = (State)skb.getControlBuffer();
                
/* Check if request can be eliminated */
                if (state.waiting_retransmit) {
                        assert (!this.retransmitQueue.isEmpty());
                        state.nak_elimination_count++;
                        return false;
                }
                
/* New request */                
                this.retransmitQueue.add (skb);
                assert (!this.retransmitQueue.isEmpty());
                state.waiting_retransmit = true;
                return true;
        }
        
/* Try to peek a request from the retransmit queue
 *
 * Return pointer of first skb in queue, or return NULL if the queue is empty.
 */
        public SocketBuffer tryPeekRetransmit() {
                LOG.debug ("tryPeekRetransmit");
                
/* No lock required to detect presence of a request */
                SocketBuffer skb = this.retransmitQueue.peek();
                if (null == skb) {
                        LOG.debug ("Retransmit queue empty on peek.");
                        return skb;
                }
                
                assert (SocketBuffer.isValid (skb));
                State state = (State)skb.getControlBuffer();
                
/* Packet payload still in transit */
                if (1 != skb.getUsers()) {
                        LOG.trace (TX_WINDOW_MARKER, "Retransmit sequence {} is still in transit in transmit thread.", skb.getSequenceNumber());
                        return null;
                }
                if (0 == state.pkt_cnt_requested) {
                        return skb;
                }
                
/* Generate parity packet to satisfy request */
                return skb;
        }
        
/* Remove head entry from retransmit queue, will fail on assertion if queue is empty.
 */
        public void removeRetransmit() {
                LOG.debug ("removeRetransmit");
                        
/* Tail link is valid without lock */
                SocketBuffer skb = this.retransmitQueue.peek();
                assert (SocketBuffer.isValid (skb));
                State state = (State)skb.getControlBuffer();
                if (state.pkt_cnt_requested > 0) {
                        state.pkt_cnt_sent++;
                        
/* Remove if all requested parity packets have been sent */
                        if (state.pkt_cnt_sent == state.pkt_cnt_requested) {
                                this.retransmitQueue.remove();
                                state.waiting_retransmit = false;
                        }
                }
                else    /* Selective request */
                {
                        this.retransmitQueue.remove();
                        state.waiting_retransmit = false;
                }
        }

/* Dump window state */        
        @Override
	public String toString() {
		return	"{" +
				  "\"tsi\": \"" + this.tsi + "\"" +
				", \"lead\": " + this.lead + "" +
				", \"trail\": " + this.trail + "" +
				", \"retransmitQueue\": { \"length\": " + this.retransmitQueue.size() + " }" +
				", \"size\": " + this.size + "" +
				", \"alloc\": " + this.alloc + "" +
			"}";
	}
}

/* eof */