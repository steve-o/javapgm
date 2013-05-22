/* Sections 5 and 8.2 of RFC 3678: Multicast group request.
 */
package hk.miru.javapgm;

import static hk.miru.javapgm.Preconditions.checkArgument;
import static hk.miru.javapgm.Preconditions.checkNotNull;

import java.net.InetAddress;

public class GroupRequest
{
        private int         gr_interface = 0;
        private InetAddress gr_group = null;
        
        public GroupRequest (int if_index, InetAddress mcastaddr) {
                checkNotNull (mcastaddr);
                checkArgument (mcastaddr.isMulticastAddress());
                this.gr_interface = if_index;
                this.gr_group = mcastaddr;
        }
        
        public int getNetworkInterfaceIndex() {
                return this.gr_interface;
        }

        public InetAddress getMulticastAddress() {
                return this.gr_group;
        }
}

/* eof */