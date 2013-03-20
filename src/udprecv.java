/*
 */ 

import java.io.*;
import java.net.*;
import java.util.*;

public class udprecv
{
	String group = "239.192.0.1";
	int port = 7500;
	int max_tpdu = 1500;

	public udprecv (String[] args) throws IOException
	{
		InetAddress group = InetAddress.getByName (this.group);
		MulticastSocket socket = new MulticastSocket (this.port);
		byte[] buffer = new byte[this.max_tpdu];
		DatagramPacket datagram;

		socket.joinGroup (group);
		while (true) {
			datagram = new DatagramPacket (buffer, buffer.length);
			socket.receive (datagram);
			System.out.println ("packet: { " +
						  "\"data\": \"" + new String (datagram.getData(), datagram.getOffset(), datagram.getLength()) + "\"" +
						", \"length\": " + datagram.getLength() + "" +
						" }");
		}
//		socket.leaveGroup (address);
//		socket.close ();
	}

	public static void main (String[] args) throws IOException
	{
		new udprecv (args);
	}
}
