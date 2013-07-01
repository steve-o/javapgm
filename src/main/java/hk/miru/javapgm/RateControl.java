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

/* Create machinery for rate regulation.
 * The rate_per_sec is ammortized over millisecond time periods.
 */      
	public RateControl (long rate_per_sec, int iphdr_len, int max_tpdu) {
/* Pre-conditions */            
                checkArgument (rate_per_sec >= max_tpdu);
                
                this.rate_per_sec = rate_per_sec;
                this.iphdr_len = iphdr_len;
                this.last_rate_check = Socket.microTime();
/* Pre-fill bucket */
                if ((this.rate_per_sec / 1000L) >= max_tpdu) {
                        this.rate_per_msec = this.rate_per_sec / 1000L;
                        this.rate_limit = this.rate_per_msec;
                } else {
                        this.rate_limit = this.rate_per_sec;
                }
	}

        @SuppressWarnings("CallToThreadYield")
        public static boolean check2 (RateControl major_bucket, RateControl minor_bucket, long data_size, boolean isNonBlocking) {
                long new_major_limit = 0, new_minor_limit;
                long now;
                
/* Pre-conditions */
                assert (null != major_bucket);
                assert (null != minor_bucket);
                assert (data_size > 0);
                
                if (0 == major_bucket.rate_per_sec && 0 == minor_bucket.rate_per_sec)
                        return true;
                
                if (0 != major_bucket.rate_per_sec)
                {
                        now = Socket.microTime();

                        if (minor_bucket.rate_per_msec > 0)
                        {
                                final long time_since_last_rate_check = now - major_bucket.last_rate_check;
                                if (time_since_last_rate_check > (1L * 1000L)) {
                                        new_major_limit = major_bucket.rate_per_msec;
                                } else {
                                        new_major_limit = major_bucket.rate_limit + ((major_bucket.rate_per_msec * time_since_last_rate_check) / 1000L);
                                        if (new_major_limit > major_bucket.rate_per_msec)
                                                new_major_limit = major_bucket.rate_per_msec;
                                }
                        }
                        else
                        {
                                final long time_since_last_rate_check = now - major_bucket.last_rate_check;
                                if (time_since_last_rate_check > (1L * 1000L * 1000L)) {
                                        new_major_limit = major_bucket.rate_per_sec;
                                } else {
                                        new_major_limit = major_bucket.rate_limit + ((major_bucket.rate_per_sec * time_since_last_rate_check) / (1000L * 1000L));
                                        if (new_major_limit > major_bucket.rate_per_sec)
                                                new_major_limit = major_bucket.rate_per_sec;
                                }
                        }
                        
                        new_major_limit -= ( major_bucket.iphdr_len + data_size );
                        if (isNonBlocking && new_major_limit < 0) {
                                return false;
                        }

                        if (major_bucket.rate_limit < 0) {
                                long sleep_amount;
                                do {
                                        Thread.yield();
                                        now = Socket.microTime();
                                        sleep_amount = (major_bucket.rate_per_sec * (now - major_bucket.last_rate_check)) / (1000L * 1000L);
                                } while ((sleep_amount + major_bucket.rate_limit) < 0);
                                major_bucket.rate_limit += sleep_amount;
                        }                        
                }
                else
                {
/* ensure we have a timestamp */
                        now = Socket.microTime();
                }
                
                if (0 != minor_bucket.rate_per_sec)
                {
                        if (minor_bucket.rate_per_msec > 0)
                        {
                                final long time_since_last_rate_check = now - minor_bucket.last_rate_check;
                                if (time_since_last_rate_check > (1L * 1000L)) {
                                        new_minor_limit = minor_bucket.rate_per_msec;
                                } else {
                                        new_minor_limit = minor_bucket.rate_limit + ((minor_bucket.rate_per_msec * time_since_last_rate_check) / 1000L);
                                        if (new_minor_limit > minor_bucket.rate_per_msec)
                                                new_minor_limit = minor_bucket.rate_per_msec;
                                }
                        }
                        else
                        {
                                final long time_since_last_rate_check = now - minor_bucket.last_rate_check;
                                if (time_since_last_rate_check > (1L * 1000L * 1000L)) {
                                        new_minor_limit = minor_bucket.rate_per_sec;
                                } else {
                                        new_minor_limit = minor_bucket.rate_limit + ((minor_bucket.rate_per_sec * time_since_last_rate_check) / (1000L * 1000L));
                                        if (new_minor_limit > minor_bucket.rate_per_sec)
                                                new_minor_limit = minor_bucket.rate_per_sec;
                                }
                        }
                        
                        new_minor_limit -= ( minor_bucket.iphdr_len + data_size );
                        if (isNonBlocking && new_minor_limit < 0) {
                                return false;
                        }
                        
/* commit new rate limit */
                        minor_bucket.rate_limit = new_minor_limit;
                        minor_bucket.last_rate_check = now;
                }
                
                if (0 != major_bucket.rate_per_sec) {
                        major_bucket.rate_limit = new_major_limit;
                        major_bucket.last_rate_check = now;
                }
                
/* sleep on minor bucket outside of lock */
                if (minor_bucket.rate_limit < 0) {
                        long sleep_amount;
                        do {
                                Thread.yield();
                                now = Socket.microTime();
                                sleep_amount = (minor_bucket.rate_per_sec * (now - minor_bucket.last_rate_check)) / (1000L * 1000L);
                        } while ((sleep_amount + minor_bucket.rate_limit) < 0);
                        minor_bucket.rate_limit += sleep_amount;
                        minor_bucket.last_rate_check = now;
                }
                
                return true;
        }
        
        @SuppressWarnings("CallToThreadYield")
        public static boolean check (RateControl bucket, long data_size, boolean isNonBlocking) {
                long new_rate_limit;
                
/* Pre-conditions */
                assert (null != bucket);
                assert (data_size > 0);
                
                if (0 == bucket.rate_per_sec)
                        return true;
                
                long now = Socket.microTime();
                
                if (bucket.rate_per_msec > 0) {
                        final long time_since_last_rate_check = now - bucket.last_rate_check;
                        if (time_since_last_rate_check > (1L * 1000L)) {
                                new_rate_limit = bucket.rate_per_msec;
                        } else {
                                new_rate_limit = bucket.rate_limit + ((bucket.rate_per_msec * time_since_last_rate_check) / 1000L);
                                if (new_rate_limit > bucket.rate_per_msec)
                                        new_rate_limit = bucket.rate_per_msec;
                        }
                } else {
                        final long time_since_last_rate_check = now - bucket.last_rate_check;
                        if (time_since_last_rate_check > (1L * 1000L * 1000L)) {
                                new_rate_limit = bucket.rate_per_sec;
                        } else {
                                new_rate_limit = bucket.rate_limit + ((bucket.rate_per_sec * time_since_last_rate_check) / (1000L * 1000L));
                                if (new_rate_limit > bucket.rate_per_sec)
                                        new_rate_limit = bucket.rate_per_sec;
                        }
                }
                
                new_rate_limit -= ( bucket.iphdr_len + data_size );
                if (isNonBlocking && new_rate_limit < 0) {
                        return false;
                }
                
                bucket.rate_limit = new_rate_limit;
                bucket.last_rate_check = now;
                if (bucket.rate_limit < 0) {
                        long sleep_amount;
                        do {
                                Thread.yield();
                                now = Socket.microTime();
                                sleep_amount = (bucket.rate_per_sec * (now - bucket.last_rate_check)) / (1000L * 1000L);
                        } while ((sleep_amount + bucket.rate_limit) < 0);
                        bucket.rate_limit += sleep_amount;
                        bucket.last_rate_check = now;
                }                
                return true;
        }
        
        public static long remaining2 (RateControl major_bucket, RateControl minor_bucket, long n) {
                long remaining = 0;
                long now;

/* Pre-conditions */
                assert (null != major_bucket);
                assert (null != minor_bucket);
                
                if (0 == major_bucket.rate_per_sec && 0 == minor_bucket.rate_per_sec)
                        return remaining;
                
                if (0 != major_bucket.rate_per_sec)
                {
                        now = Socket.microTime();
                        final long bucket_bytes = major_bucket.rate_limit + ((major_bucket.rate_per_sec * (now - major_bucket.last_rate_check)) / (1000L * 1000L)) - n;
                        
                        if (bucket_bytes < 0) {
                                final long outstanding_bytes = -bucket_bytes;
                                final long major_remaining = (1000L * 1000L * outstanding_bytes) / major_bucket.rate_per_sec;
                                remaining = major_remaining;
                        }
                }
                else
                {
/* ensure we have a timestamp */
                        now = Socket.microTime();
                }
                
                if (0 != minor_bucket.rate_per_sec)
                {
                        final long bucket_bytes = minor_bucket.rate_limit + ((minor_bucket.rate_per_sec * (now - minor_bucket.last_rate_check)) / (1000L * 1000L)) - n;

                        if (bucket_bytes < 0) {
                                final long outstanding_bytes = -bucket_bytes;
                                final long minor_remaining = (1000L * 1000L * outstanding_bytes) / minor_bucket.rate_per_sec;
                                remaining = remaining > 0 ? Math.min (remaining, minor_remaining) : minor_remaining;
                        }                
                }              
                
                return remaining;
        }

        public static long remaining (RateControl bucket, long n) {
/* Pre-conditions */
                assert (null != bucket);
                
                if (0 == bucket.rate_per_sec)
                        return 0;
                
                final long now = Socket.microTime();
                final long timeSinceLastRateCheck = now - bucket.last_rate_check;
                final long bucketBytes = bucket.rate_limit + ((bucket.rate_per_sec * timeSinceLastRateCheck) / (1000L * 1000L)) - n;
                
                if (bucketBytes >= 0)
                        return 0;
                
                final long outstandingBytes = -bucketBytes;
                final long remaining = (1000L * 1000L * outstandingBytes) / bucket.rate_per_sec;
                
                return remaining;
        }
}

/* eof */