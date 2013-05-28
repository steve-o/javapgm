/* Sections 5 and 8.2 of RFC 3678: Multicast group request.
 */
package hk.miru.javapgm;

import static hk.miru.javapgm.Preconditions.checkArgument;
import static hk.miru.javapgm.Preconditions.checkNotNull;

import java.net.InetAddress;

import javax.annotation.Nullable;

public class GroupSourceRequest implements Comparable<GroupSourceRequest>
{
        private int         gsr_interface = 0;
        private InetAddress gsr_group = null;
        private InetAddress gsr_source = null;

        public GroupSourceRequest (int if_index, InetAddress mcastaddr, @Nullable InetAddress srcaddr) {
                checkNotNull (mcastaddr);
                checkArgument (mcastaddr.isMulticastAddress());
                if (null != srcaddr) {
                        checkArgument (!srcaddr.isMulticastAddress());
                }
                this.gsr_interface = if_index;
                this.gsr_group = mcastaddr;
                this.gsr_source = srcaddr;
        }
       
        public int getNetworkInterfaceIndex() {
                return this.gsr_interface;
        }

        public InetAddress getMulticastAddress() {
                return this.gsr_group;
        }

        public InetAddress getSourceAddress() {
                return this.gsr_source;
        }

        @Override
        public int compareTo (GroupSourceRequest other) {
                if (this.gsr_interface < other.gsr_interface) return -1;
                if (this.gsr_interface > other.gsr_interface) return 1;
                
                if (null == this.gsr_group && null == other.gsr_group) return 0;
                if (this.gsr_group.toString().compareTo (other.gsr_group.toString()) < 0) return -1;
                if (this.gsr_group.toString().compareTo (other.gsr_group.toString()) > 0) return 1;
                
                if (null == this.gsr_source && null == other.gsr_source) return 0;
                if (this.gsr_source.toString().compareTo (other.gsr_source.toString()) < 0) return -1;
                if (this.gsr_source.toString().compareTo (other.gsr_source.toString()) > 0) return 1;
                
                return 0;
        }
}

/* eof */