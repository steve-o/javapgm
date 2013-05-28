/* Socket address type for PGM sockets.
 */
package hk.miru.javapgm;

import static hk.miru.javapgm.Preconditions.checkArgument;

public class RateControl
{
        private long rate_per_sec;
        private long rate_per_msec;
        private int iphdr_len;
        
        private long rate_limit;
        private long last_rate_check;

	public RateControl (long rate_per_sec, int iphdr_len, int max_tpdu) {
/* Pre-conditions */            
                checkArgument (rate_per_sec >= max_tpdu);
                
                this.rate_per_sec = rate_per_sec;
                this.iphdr_len = iphdr_len;
                this.last_rate_check = System.currentTimeMillis();
/* Pre-fill bucket */
                if ((this.rate_per_sec / 1000) >= max_tpdu) {
                        this.rate_per_msec = this.rate_per_sec / 1000;
                        this.rate_limit = this.rate_per_msec;
                } else {
                        this.rate_limit = this.rate_per_sec;
                }
	}
}

/* eof */