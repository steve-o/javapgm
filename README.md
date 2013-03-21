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
  defining window
  sequence: 0, lead: -1
  ReceiveWindow.add returned RXW_APPENDED
  New pending data.
  #1
  flushPeersPending
  read
  incomingRead
  incomingReadApdu
  peer read=32
  apdu_length=32
  msg: { "data": "Wed, 20 Mar 2013 13:40:37 -0400^@", "length": 32 )
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

