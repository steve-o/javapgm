
import java.util.*;
import java.net.*;

public class testsend
{
	int dataSourcePort = 0;
	int dataDestinationPort = 7500;
	String networkGroup = "239.192.0.1";
	int udpEncapsulationPort = 7500;
	GlobalSourceId gsi = null;

	public testsend(String args[])
	{
		MulticastSocket s = null;
		InetAddress group = null;

		try {
			s = new MulticastSocket();
		} catch (java.io.IOException e) {
			System.out.println ("Failed to create socket: " + e.toString());
			e.printStackTrace();
			System.exit(0);
		}

		try {
			group = InetAddress.getByName (this.networkGroup);
		} catch (java.net.UnknownHostException e) {
			System.out.println ("Failed to resolve network address: " + e.toString());
			e.printStackTrace();
			System.exit(0);
		}

		try {
			this.gsi = new GlobalSourceId();
		} catch (java.net.UnknownHostException e) {
			System.out.println ("Failed to resolve nodename: " + e.toString());
			e.printStackTrace();
			System.exit(0);
		} catch (java.security.NoSuchAlgorithmException e) {
			System.out.println ("MD5 algorithm not supported by JVM: " + e.toString());
			e.printStackTrace();
			System.exit(0);
		}

		String msg = "i am a banana!";
		SocketBuffer skb = new SocketBuffer();
		OriginalDataPacket odata = OriginalDataPacket.create (skb);
		odata.setSourcePort (this.dataSourcePort);
		odata.setDestinationPort (this.dataDestinationPort);
		odata.setGlobalSourceId (this.gsi);
		odata.setSequenceNumber (0);
		odata.setTrail (0);
		odata.setData (msg.getBytes());
		odata.updateChecksum();
		DatagramPacket p = new DatagramPacket (skb.getData(), skb.getOffset(), skb.getLength(), group, udpEncapsulationPort);
		try {
			s.send (p);
		} catch (java.io.IOException e) {
			System.out.println ("Failed to send: " + e.toString());
			e.printStackTrace();
			System.exit(0);
		}

		s.close();
	}

	public static void main(String args[])
	{
		new testsend(args);
	}
}

