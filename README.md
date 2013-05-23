javapgm
=======

Java implementation of the PGM protocol.


Building
========

Use Maven to build from source.  The following works:
```
  mvn compile
```

In addition the project can be built through Eclipse and NetBeans with native
Maven integration.


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

Monitor the default output:
```
  Create PGM/UDP socket.
  Received 1 SKB
  #1 from 79.4.63.168.226.140.136: "Thu, 23 May 2013 10:01:32 -0400"
  Received 1 SKB
  #1 from 79.4.63.168.226.140.136: "Thu, 23 May 2013 10:01:33 -0400"
  Received 1 SKB
  #1 from 79.4.63.168.226.140.136: "Thu, 23 May 2013 10:01:34 -0400"
  Received 1 SKB
  #1 from 79.4.63.168.226.140.136: "Thu, 23 May 2013 10:01:35 -0400"
```

Configure Apache log4j2 for additional trace as required, place JSON or XML into
classpath, e.g. `log4j2-test.xml`:

```
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <appenders>
    <Console name="STDOUT" target="SYSTEM_OUT">
      <PatternLayout pattern="%d %-5p [%t] %C{2} (%F:%L) - %m%n"/>
    </Console>
  </appenders>
  <loggers>
    <logger name="org.apache.log4j.xml" level="info"/>
    <root level="debug">
      <appender-ref ref="STDOUT"/>
    </root>
  </loggers>
</configuration>
```


Resources
=========

Website: https://code.google.com/p/openpgm/

Development mailing list: openpgm-dev@googlegroups.com

Apache log4j2: http://logging.apache.org/log4j/2.x/


Copying
=======

Free use of this software is granted under the terms of the GNU Lesser General
Public License (LGPL). For details see the file `COPYING` included with the
JavaPGM distribution.

