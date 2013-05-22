/* Socket address type for PGM sockets.
 */
package hk.miru.javapgm;

import static hk.miru.javapgm.Preconditions.checkArgument;
import static hk.miru.javapgm.Preconditions.checkNotNull;

import java.util.Random;

public class SocketAddress
{
	private TransportSessionId tsi = null;
	private int port = 0;       /* data-destination port */

	public SocketAddress (TransportSessionId tsi, int destinationPort) {
                checkNotNull (tsi);
                checkArgument (destinationPort >= 0 && destinationPort <= 65535);
                this.tsi = tsi;
                this.port = destinationPort;
	}

	public SocketAddress (GlobalSourceId gsi, int destinationPort) {
                checkNotNull (gsi);
                checkArgument (destinationPort >= 0 && destinationPort <= 65535);
                Random rand = new Random();
                short sourcePort;
                do {
                        sourcePort = (short)rand.nextInt (0xffff);
                } while (sourcePort == destinationPort);
                this.tsi = new TransportSessionId (gsi, sourcePort);
                this.port = destinationPort;
	}
        
	public TransportSessionId getTransportSessionId() {
		return this.tsi;
	}

	public int getPort() {
		return this.port;
	}
}

/* eof */