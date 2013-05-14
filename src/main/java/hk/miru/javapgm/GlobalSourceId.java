/* Unique global source identifier.
 */
package hk.miru.javapgm;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

public class GlobalSourceId
{
	public static final int SIZE = 6;

	private byte[] identifier;

	public GlobalSourceId() throws UnknownHostException, NoSuchAlgorithmException {
		identifier = new byte[SIZE];
		String hostname = InetAddress.getLocalHost().getHostName();
		setIdentifier (hostname);
	}

	public GlobalSourceId (byte[] bytes) {
		identifier = new byte[SIZE];
		setIdentifier (bytes);
	}

	public GlobalSourceId (GlobalSourceId gsi) {
		if (null == gsi)
			throw new NullPointerException("gsi may not be null");

		identifier = new byte[SIZE];
		setIdentifier (gsi.getAsBytes());
	}

        @Override
	public int hashCode() {
		return this.toString().hashCode();
	}

        @Override
	public boolean equals (Object object) {
		if (! (object instanceof GlobalSourceId))
			return false;

		byte[] identifier2 = ((GlobalSourceId)object).identifier;

		if (this.identifier.length != identifier2.length)
			return false;

		for (int i = 0; i < this.identifier.length; i++)
			if (this.identifier[i] != identifier2[i])
				return false;

		return true;
	}

	public final byte[] getAsBytes() {
		return this.identifier;
	}

/* IDN hostnames must be already converted to ASCII character set via java.net.IDN
 */
	public final void setIdentifier (String hostname) throws NoSuchAlgorithmException {
		MessageDigest digest = MessageDigest.getInstance ("MD5");
		try {
			digest.update (hostname.getBytes ("US-ASCII"));
		} catch (java.io.UnsupportedEncodingException e) {
			System.out.println ("US-ASCII encoding not supported by JVM: " + e.toString());
			digest.update (hostname.getBytes ());
		}
		setIdentifier (digest.digest());
	}

        @SuppressWarnings("PointlessBitwiseExpression")
	public final void setIdentifier (Inet4Address addr) {
		System.arraycopy (this.identifier, 0,
				  addr.getAddress(), 0,
				  4);
		Random rand = new Random();
		short random = (short)rand.nextInt (0xffff);
		this.identifier[4] = (byte)((random >> 8) & 0xff);
		this.identifier[5] = (byte)((random >> 0) & 0xff);
	}

	public final void setIdentifier (byte[] bytes) {
		if (null == bytes || bytes.length < SIZE)
			throw new IllegalArgumentException("Parameter must be array of at least 6 bytes");

		this.identifier[0] = bytes[0];
		this.identifier[1] = bytes[1];
		this.identifier[2] = bytes[2];
		this.identifier[3] = bytes[3];
		this.identifier[4] = bytes[4];
		this.identifier[5] = bytes[5];
	}

        @Override
	public String toString() {
		return String.valueOf (this.identifier[0] & 0xff) + '.'
			+ String.valueOf (this.identifier[1] & 0xff) + '.'
			+ String.valueOf (this.identifier[2] & 0xff) + '.'
			+ String.valueOf (this.identifier[3] & 0xff) + '.'
			+ String.valueOf (this.identifier[4] & 0xff) + '.'
			+ String.valueOf (this.identifier[5] & 0xff);
	}
}

/* eof */