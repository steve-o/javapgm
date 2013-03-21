/* A peer in the context of the sock is another party on the network sending PGM
 * packets.
 */
import java.io.*;
import java.net.*;
import java.util.*;

public class Peer {

	private TransportSessionId tsi = null;
	private InetAddress groupPath = null;
	private long lastPacketTimestamp = 0;
	private ReceiveWindow window;
	private boolean hasPendingLinkData = false;
	private long lastCommit = 0;
	private long lostCount = 0;
	private long lastCumulativeLosses = 0;

	public Peer (
		TransportSessionId tsi,
		int max_tpdu,
		int rxw_sqns,
		int rxw_secs,
		long rxw_max_rte
		)
	{
		this.tsi = tsi;
		this.window = new ReceiveWindow (tsi, max_tpdu, rxw_sqns, rxw_secs, rxw_max_rte);
	}

	public ReceiveWindow.Returns add (OriginalDataPacket packet) {
		return window.add (packet);
	}

	public ReceiveWindow.Returns add (RepairDataPacket packet) {
		return ReceiveWindow.Returns.RXW_MALFORMED;
	}

	public int read (byte[] buffer, int buffer_length) {
		return this.window.read (buffer, buffer_length);
	}

	public TransportSessionId getTransportSessionId() {
		return this.tsi;
	}

	public void setGroupPath (InetAddress groupPath) {
		this.groupPath = groupPath;
	}

	public void setLastPacketTimestamp (long lastPacketTimestamp) {
		this.lastPacketTimestamp = lastPacketTimestamp;
	}

/* edge trigerred has receiver pending events
 */
	public boolean hasPending() {
		if (!this.hasPendingLinkData && window.hasEvent()) {
			window.clearEvent();
			return true;
		}
		return false;
	}

	public void setPendingLinkData() {
		this.hasPendingLinkData = true;
	}

	public void clearPendingLinkData() {
		this.hasPendingLinkData = false;
	}

	public boolean hasLastCommit() {
		return this.lastCommit > 0;
	}

	public long getLastCommit() {
		return this.lastCommit;
	}

	public void setLastCommit (long lastCommit) {
		this.lastCommit = lastCommit;
	}

	public void removeCommit() {
		this.window.removeCommit();
	}

	public boolean hasDataLoss() {
		if (this.lastCumulativeLosses != window.getCumulativeLosses()) {
			this.lostCount = window.getCumulativeLosses() - this.lastCumulativeLosses;
			this.lastCumulativeLosses = window.getCumulativeLosses();
			return true;
		}
		return false;
	}

	public String toString() {
		return	 "{ " +
			  "\"tsi\": \"" + this.tsi + "\"" +
			", \"groupPath\": " + this.groupPath + "" +
			", \"lastPacketTimestamp\": " + this.lastPacketTimestamp + "" +
			", \"window\": " + this.window + "" +
			 " }";
	}
}

/* eof */
