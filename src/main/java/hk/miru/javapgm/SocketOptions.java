/* Socket options for PGM sockets.
 */
package hk.miru.javapgm;

public interface SocketOptions
{
        static final int PGM_SEND_SOCK              = 0x2000;
        static final int PGM_RECV_SOCK              = 0x2001;
        static final int PGM_REPAIR_SOCK            = 0x2002;
        static final int PGM_PENDING_SOCK           = 0x2003;
        static final int PGM_ACK_SOCK               = 0x2004;
        static final int PGM_TIME_REMAIN            = 0x2005;
        static final int PGM_RATE_REMAIN            = 0x2006;
        static final int PGM_IP_ROUTER_ALERT        = 0x2007;
        static final int PGM_MTU                    = 0x2008;
        static final int PGM_MSSS                   = 0x2009;
        static final int PGM_MSS                    = 0x200a;
        static final int PGM_PDU                    = 0x200b;
        static final int PGM_MULTICAST_LOOP         = 0x200c;
        static final int PGM_MULTICAST_HOPS         = 0x200d;
        static final int PGM_TOS                    = 0x200e;
        static final int PGM_AMBIENT_SPM            = 0x200f;
        static final int PGM_HEARTBEAT_SPM          = 0x2010;
        static final int PGM_TXW_BYTES              = 0x2011;
        static final int PGM_TXW_SQNS               = 0x2012;
        static final int PGM_TXW_SECS               = 0x2013;
        static final int PGM_TXW_MAX_RTE            = 0x2014;
        static final int PGM_PEER_EXPIRY            = 0x2015;
        static final int PGM_SPMR_EXPIRY            = 0x2016;
        static final int PGM_RXW_BYTES              = 0x2017;
        static final int PGM_RXW_SQNS               = 0x2018;
        static final int PGM_RXW_SECS               = 0x2019;
        static final int PGM_RXW_MAX_RTE            = 0x201a;
        static final int PGM_NAK_BO_IVL             = 0x201b;
        static final int PGM_NAK_RPT_IVL            = 0x201c;
        static final int PGM_NAK_RDATA_IVL          = 0x201d;
        static final int PGM_NAK_DATA_RETRIES       = 0x201e;
        static final int PGM_NAK_NCF_RETRIES        = 0x201f;
        static final int PGM_USE_FEC                = 0x2020;
        static final int PGM_USE_CR                 = 0x2021;
        static final int PGM_USE_PGMCC              = 0x2022;
        static final int PGM_SEND_ONLY              = 0x2023;
        static final int PGM_RECV_ONLY              = 0x2024;
        static final int PGM_PASSIVE                = 0x2025;
        static final int PGM_ABORT_ON_RESET         = 0x2026;
        static final int PGM_NOBLOCK                = 0x2027;
        static final int PGM_SEND_GROUP             = 0x2028;
        static final int PGM_JOIN_GROUP             = 0x2029;
        static final int PGM_LEAVE_GROUP            = 0x202a;
        static final int PGM_BLOCK_SOURCE           = 0x202b;
        static final int PGM_UNBLOCK_SOURCE         = 0x202c;
        static final int PGM_JOIN_SOURCE_GROUP      = 0x202d;
        static final int PGM_LEAVE_SOURCE_GROUP     = 0x202e;
        static final int PGM_MSFILTER               = 0x202f;
        static final int PGM_UDP_ENCAP_UCAST_PORT   = 0x2030;
        static final int PGM_UDP_ENCAP_MCAST_PORT   = 0x2031;
        static final int PGM_UNCONTROLLED_ODATA     = 0x2032;
        static final int PGM_UNCONTROLLED_RDATA     = 0x2033;
        static final int PGM_ODATA_MAX_RTE          = 0x2034;
        static final int PGM_RDATA_MAX_RTE          = 0x2035;
}

/* eof */