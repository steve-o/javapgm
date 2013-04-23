javapgm
=======

Java implementation of the PGM protocol.


Example usage
=============

Modify `testreceive.java` for the appropriate network configuration and build the
class set.
```
  java testreceive
```

Start a PGM publisher on the same or other host, aware of platform specific
multicast loop rules.  For example using the OpenPGM C package:
```
  ./daytime -p 7500 -l
```

Monitor the slightly verbose output.
```
  ...
  add ( "skb":  { "timestamp": 1366746451692, "tsi": "169.232.35.188.84.158.39986", "sequence": null } )
  defining window
  sequence: 87726, lead: 87725
  append
  ReceiveWindow.add returned RXW_APPENDED
  New pending data.
  flushPeersPending
  read
  incomingRead
  incomingReadApdu
  Received 1 SKBs
  #1 from 169.232.35.188.84.158.39986: "Tue, 23 Apr 2013 15:47:31 -0400"
  timer pending ...
  timer pending ...
  timer pending ...
  timer pending ...
  timer pending ...

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

