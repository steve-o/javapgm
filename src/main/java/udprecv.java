/* Example of basic UDP receiver not using a selector.
 */ 
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class udprecv
{
	String group = "239.192.0.1";
	int port = 7500;
	int max_tpdu = 1500;

	public udprecv (String[] args) throws IOException
	{
		InetAddress group = InetAddress.getByName (this.group);
		byte[] buffer = new byte[this.max_tpdu];
		DatagramPacket datagram;

		try (MulticastSocket socket = new MulticastSocket (this.port)) {
			socket.joinGroup (group);
			while (true) {
				datagram = new DatagramPacket (buffer, buffer.length);
				socket.receive (datagram);
				System.out.println ("packet: { " +
						  "\"src\": \"" + datagram.getAddress() + "\"" +
						", \"data\": \"" + new String (datagram.getData(), datagram.getOffset(), datagram.getLength()) + "\"" +
						", \"length\": " + datagram.getLength() + "" +
						" }");
			}
		}
	}

	public static void main (String[] args) throws IOException
	{
		new udprecv (args);
	}
}

/* eof */