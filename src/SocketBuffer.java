/* Primitive data buffer for PGM packets.
 */
import java.io.*;
import java.net.*;
import java.util.*;

public class SocketBuffer {

	private byte[]		_buf	= null;
	private int		_offset	= 0;
	private int		_length	= 0;
	private long		timestamp = 0;

	public SocketBuffer() {
		this (1500);
	}

	public SocketBuffer (int length) {
		this._length = length;
		this._buf = new byte[length];
	}

	public SocketBuffer (byte[] buf) {
		this (buf, 0, buf.length);
	}

	public SocketBuffer (byte[] buf, int offset, int length) {
		this._buf = buf;
		this._offset = offset;
		this._length = offset + length;
		this.timestamp = System.currentTimeMillis();
	}

	public byte[] getData() {
		return this._buf;
	}

	public int getOffset() {
		return this._offset;
	}

	public int getLength() {
		return this._length;
	}

	public long getTimestamp() {
		return this.timestamp;
	}
}

/* eof */
