/* Sections 5 and 8.2 of RFC 3678: Multicast group request.
 */
package hk.miru.javapgm;

public class InterfaceRequest
{
        private int ir_interface = 0;
        private int ir_scope_id = 0;
        
        public InterfaceRequest (int if_index, int scope_id) {
                this.ir_interface = if_index;
                this.ir_scope_id = scope_id;
        }
        
        public int getNetworkInterfaceIndex() {
                return this.ir_interface;
        }

        public int getScopeId() {
                return this.ir_scope_id;
        }
}

/* eof */