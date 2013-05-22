/* Sections 5 and 8.2 of RFC 3678: Multicast group request.
 */
package hk.miru.javapgm;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.ProtocolFamily;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.net.UnknownHostException;
import java.util.LinkedList;

public class NetworkInterface
{
/* Return array of addresses that matches family for the node name subject to correct
 * RR configuration of the host.
 */    
        public static InetAddress[] getNodeAddress (ProtocolFamily family) throws UnknownHostException {
                LinkedList<InetAddress> addresses = new LinkedList<> ();
                String nodename = InetAddress.getLocalHost().getHostName();
                for (InetAddress addr : InetAddress.getAllByName (nodename)) {
                        if (StandardProtocolFamily.INET == family && addr instanceof Inet4Address)
                                addresses.add (addr);
                        else if (StandardProtocolFamily.INET6 == family && addr instanceof Inet6Address)
                                addresses.add (addr);
                }
                return (InetAddress[]) addresses.toArray();
        }
    
/* Pick a node address that supports multicast traffic iff more than one
 * address exists.
 */    
        public static InetAddress getMulticastEnabledNodeAddress (ProtocolFamily family) throws UnknownHostException, SocketException {
                InetAddress res[] = getNodeAddress (family);
            
/* iff one address return that independent of multicast support */
                if (res.length == 1)
                        return res[0];
                
                for (InetAddress addr : res) {
/* For each node address find matching interface and test flags */                
                        java.net.NetworkInterface ni = java.net.NetworkInterface.getByInetAddress (addr);
                        if (ni.supportsMulticast())
                                return addr;
                }
                
/* Use last address as fallback */
                return res[res.length - 1];
        }
}

/* eof */