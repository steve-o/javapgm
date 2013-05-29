/* Socket address type for PGM sockets.
 */
package hk.miru.javapgm;

import static hk.miru.javapgm.Preconditions.checkArgument;
import static hk.miru.javapgm.Preconditions.checkNotNull;

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
                this.tsi = new TransportSessionId (gsi, Packet.DEFAULT_DATA_SOURCE_PORT);
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