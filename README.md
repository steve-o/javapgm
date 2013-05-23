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

Monitor the output, configure Apache log4j2 for additional trace.
```
  Create PGM/UDP socket.
  Received 1 SKBs
  #1 from 79.4.63.168.226.140.136: "Thu, 23 May 2013 09:20:41 -0400"
  timer pending ...
  timer pending ...
  timer pending ...
  timer pending ...
  timer pending ...
  Received 1 SKBs
  #1 from 79.4.63.168.226.140.136: "Thu, 23 May 2013 09:20:42 -0400"
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

