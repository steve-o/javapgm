/* Dump all incoming PGM packets to stdout.
 */ 
import java.io.*;
import java.net.*;
import java.util.*;

public class pgmdump {

	public static void main (String[] args) throws IOException {

		MulticastSocket socket = new MulticastSocket (7500);
		InetAddress address = InetAddress.getByName ("239.192.0.1");
		socket.joinGroup (address);

		byte[] buf = new byte[1500];
		DatagramPacket packet;
		SocketBuffer skb;
		PgmPacket pgm;

		while (true) {    
			packet = new DatagramPacket (buf, buf.length);
			socket.receive (packet);
			skb = new SocketBuffer (packet.getData(),
					        packet.getOffset(),
					        packet.getLength());
			pgm = PgmPacket.decode (skb);
			System.out.println ("address:" + packet.getAddress() +
					    " pgm:" + pgm);
		}

//		socket.leaveGroup (address);
//		socket.close();
	}
}

/* eof */
