/* Requires JDK 7.
 */ 

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.net.*;
import java.util.*;

public class channelrecv
{
	String adapter = "10.0.9.30";
	String group = "239.192.0.1";
	int port = 7500;
	int max_tpdu = 1500;

	public channelrecv (String[] args) throws IOException
	{
		InetAddress group = InetAddress.getByName (this.group);
		ProtocolFamily pf = group instanceof Inet4Address ? StandardProtocolFamily.INET : StandardProtocolFamily.INET6;
		NetworkInterface ni = NetworkInterface.getByName (this.adapter);
		if (null == ni) ni = NetworkInterface.getByInetAddress (InetAddress.getByName (this.adapter));
		DatagramChannel dc = DatagramChannel.open (pf)
			.setOption (StandardSocketOptions.SO_REUSEADDR, true)
			.bind (new InetSocketAddress (this.port))
			.setOption (StandardSocketOptions.IP_MULTICAST_IF, ni);
		dc.configureBlocking (false);
		MembershipKey key = dc.join (group, ni);
		ByteBuffer buffer = ByteBuffer.allocateDirect (this.max_tpdu);
		Selector selector = Selector.open();
		SelectionKey sk = dc.register (selector, SelectionKey.OP_READ);

		while (true) {
			int keyCount = selector.select (1000);
			if (keyCount > 0) {
				selector.selectedKeys().clear();
				SocketAddress source = dc.receive (buffer);
				buffer.flip();
				byte[] bytes = new byte [buffer.remaining()];
				buffer.get (bytes, 0, bytes.length);
				buffer.clear();
				System.out.println ("packet: { " +
					  "\"data\": \"" + new String (bytes, 0, bytes.length) + "\"" +
					", \"length\": " + bytes.length + "" +
					" }");
			}
		}
	}

	public static void main (String[] args) throws IOException
	{
		new channelrecv (args);
	}
}
