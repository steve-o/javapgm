/* Unique Global Source Identifier (GSI), usually derived from the system node
 * name or IPv4 address as a convenient unique value.  When combined with a 
 * data-source port it becomes a Transport Session Identifier (TSI).
 */
package hk.miru.javapgm;

import static hk.miru.javapgm.Preconditions.checkArgument;
import static hk.miru.javapgm.Preconditions.checkNotNull;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

import javax.annotation.Nullable;

public class GlobalSourceId
{
/**
 * The number of bytes require to represent a GSI.
 */
	public static final int SIZE = 6;
        
	private final byte[] identifier;

/**
 * Create a new {@code GlobalSourceId} from an sequence of bytes.
 * 
 * @param bytes the primitive value in bytes.
 */        
	public GlobalSourceId (byte[] bytes) {
                checkArgument (bytes.length == SIZE);
		this.identifier = new byte[SIZE];
		set (bytes);
	}

	public GlobalSourceId (GlobalSourceId gsi) {
		this (gsi.getBytes());
	}

/**
 * Constructs a new {@code GlobalSourceId} from the local node name.
 * 
 * @throws UnknownHostException
 * @throws NoSuchAlgorithmException 
 */        
       	public GlobalSourceId() throws UnknownHostException, NoSuchAlgorithmException {
		this (InetAddress.getLocalHost().getHostName().getBytes());
	} 
           
        @Override
	public int hashCode() {
		return toString().hashCode();
	}

        @Override
	public boolean equals (@Nullable Object object) {
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

	public final byte[] getBytes() {
		return this.identifier;
	}

/* Create a global session ID as recommended by the PGM draft specification
 * using low order 48 bits of MD5 of a hostname.
 * 
 * IDN hostnames must be already converted to ASCII character set via
 * java.net.IDN
 */
	public final void set (String hostname) throws NoSuchAlgorithmException {
                checkNotNull (hostname);
		MessageDigest digest = MessageDigest.getInstance ("MD5");
		try {
			digest.update (hostname.getBytes ("US-ASCII"));
		} catch (java.io.UnsupportedEncodingException e) {
			System.out.println ("US-ASCII encoding not supported by JVM: " + e.toString());
			digest.update (hostname.getBytes ());
		}
		set (digest.digest());
	}

/* Create a global session ID based on an IPv4 address.
 */
        @SuppressWarnings("PointlessBitwiseExpression")
	public final void set (Inet4Address addr) {
                checkNotNull (addr);
		System.arraycopy (this.identifier, 0,
				  addr.getAddress(), 0,
				  4);
		Random rand = new Random();
		short random = (short)rand.nextInt (0xffff);
		this.identifier[4] = (byte)((random >> 8) & 0xff);
		this.identifier[5] = (byte)((random >> 0) & 0xff);
	}

/* Create a global session ID based on the MD5 of a user provided data block.
 */
	public final void set (byte[] bytes) {
                checkArgument (bytes.length == SIZE);
		this.identifier[0] = bytes[0];
		this.identifier[1] = bytes[1];
		this.identifier[2] = bytes[2];
		this.identifier[3] = bytes[3];
		this.identifier[4] = bytes[4];
		this.identifier[5] = bytes[5];
	}

/**
 * Returns a string representation of the {@code GlobalSourceId} value.
 */        
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