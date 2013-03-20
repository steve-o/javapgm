import java.io.*;
import java.net.*;
import java.util.*;

public class OriginalDataPacket extends PgmPacket {

	private static final int SIZEOF_ODATA_HEADER	= 8;

	private static final int ODATA_SQN_OFFSET	= 0;
	private static final int ODATA_TRAIL_OFFSET	= 4;
	private static final int ODATA_OPTIONS_OFFSET	= SIZEOF_ODATA_HEADER;

	private int _header_length = 0;
	private int _odata_offset = 0;

	public OriginalDataPacket (SocketBuffer skb) {
		super (skb);
		this._odata_offset = this._offset + PGM_TYPE_DATA_OFFSET;
		this._header_length = this._odata_offset + SIZEOF_ODATA_HEADER - this._offset;
	}

	public static final OriginalDataPacket create (SocketBuffer skb) {
		OriginalDataPacket packet = new OriginalDataPacket (skb);
		packet.setType (PGM_ODATA);
		return packet;
	}

	public static final OriginalDataPacket decode (SocketBuffer skb) {
		return new OriginalDataPacket (skb);
	}

	public boolean isValid () {
		if (!super.isValid ())
			return false;
		if (this._length < (this._odata_offset + SIZEOF_ODATA_HEADER)) {
			System.out.println ("ODATA packet too small at " + this._length + " bytes, expecting at least " + (this._odata_offset + SIZEOF_PGM_HEADER) + " bytes.");
			return false;
		}
		return true;
	}

	public void updateChecksum () {
		this.setChecksum (0);
		long unfoldedHeader = PgmChecksum.partial (this._buf, 0, this._header_length, 0);
		long unfoldedData   = PgmChecksum.partial (this._buf, this._header_length, this.getTsduLength(), 0);
		this.setChecksum (PgmChecksum.fold (PgmChecksum.blockAdd (unfoldedHeader, unfoldedData, this._header_length)));
	}

	public final long getSequenceNumber() {
		return this.getUnsignedInt (this._odata_offset + ODATA_SQN_OFFSET);
	}

	public final void setSequenceNumber (long odata_sqn) {
		this.setUnsignedInt (this._odata_offset + ODATA_SQN_OFFSET, odata_sqn);
	}

	public final long getTrail() {
		return this.getUnsignedInt (this._odata_offset + ODATA_TRAIL_OFFSET);
	}

	public final void setTrail (long odata_trail) {
		this.setUnsignedInt (this._odata_offset + ODATA_TRAIL_OFFSET, odata_trail);
	}

	public final boolean isFragment() {
		return false;
	}

	public long getFirstSequenceNumber() {
		if (this.isFragment())
			return 0;
		else
			return this.getSequenceNumber();
	}

	public long getApduLength() {
		return this.getTsduLength();
	}

	public final byte[] getData () {
		byte[] buf = new byte[ this.getTsduLength() ];
		System.arraycopy (this._buf, this._odata_offset + ODATA_OPTIONS_OFFSET,
				  buf, 0,
				  this.getTsduLength());
		return buf;
	}

	public final String getDataAsString () {
		byte[] buf = this.getData();
		return new String (buf);
	}

	public final void setData (byte[] buf) {
		System.arraycopy (buf, 0,
				  this._buf, this._odata_offset + ODATA_OPTIONS_OFFSET,
				  buf.length);
		this.setTsduLength (buf.length);
	}

	public String toString() {
		GlobalSourceId gsi = this.getGlobalSourceId ();
		return  "{ " +
			 "\"sourcePort\": " + this.getSourcePort() + "" +
		       ", \"destinationPort\": " + this.getDestinationPort() + "" +
		       ", \"type\": \"" + this.getTypeName (this.getType()) + "\"" +
		       ", \"options\": " + this.getOptions() + "" +
		       ", \"checksum\": 0x" + Integer.toHexString (this.getChecksum()) + "" +
		       ", \"gsi\": \"" + gsi + "\"" +
		       ", \"tsduLength\": " + this.getTsduLength() + "" +
		       ", \"odataSqn\": " + this.getSequenceNumber() + "" +
		       ", \"odataTrail\": " + this.getTrail() + "" +
		       ", \"odataData\": \"" + this.getDataAsString() + "\"" +
		        " }";
	}

}
