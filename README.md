javapgm
=======

Java implementation of the PGM protocol.


Building
========

Use Maven to build from source.  The following works:
```
  mvn compile
```

Example usage
=============

Modify `testreceive.java` for the appropriate network configuration and build the
class set.
```
  java testreceive
```

Start a PGM publisher on the same or other host, aware of platform specific
multicast loop rules.  For example using the OpenPGM C package with default
wireless adapter:
```
  ./daytime -lp 3056 -n "wlan0;239.192.0.1"
```

Monitor the slightly verbose output.
```
  ...
  onData
  add ( "skb":  { "timestamp": 1367954156656, "tsi": "126.111.159.175.2.164.33465", "sequence": null, "len": 34, "header":  { "pgm_sport": 33465, "pgm_dport": 7500, "pgm_type": "PGM_ODATA", "pgm_options": "", "pgm_checksum": 0, "pgm_gsi": "126.111.159.175.2.164", "pgm_tsdu_length": 34 }, "odata": { "sourcePort": 33465, "destinationPort": 7500, "type": "PGM_ODATA", "options": 0, "checksum": 0x0, "gsi": "126.111.159.175.2.164", "tsduLength": 34, "dataSqn": 25, "dataTrail": 0, "dataData": "火, 07  5月 2013 15:15:56 -0400" }, "opt_fragment": null, "buf": { "head": 0, "data": 24, "tail": 58, "end": 58, "length": 58 } } )
  defining window
  SKB:25 trail:25 commit:25 lead:24 (RXW_TRAIL:25)
  append
  ReceiveWindow.add returned RXW_APPENDED
  New pending data.
  flushPeersPending
  read
  read #25
  incomingRead
  incomingReadApdu
  Received 1 SKBs
  #1 from 126.111.159.175.2.164.33465: "火, 07  5月 2013 15:15:56 -0400"

```

Resources
=========

Website: https://code.google.com/p/openpgm/

Development mailing list: openpgm-dev@googlegroups.com


Copying
=======

Free use of this software is granted under the terms of the GNU Lesser General
Public License (LGPL). For details see the file `COPYING` included with the
JavaPGM distribution.

