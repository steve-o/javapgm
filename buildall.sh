#!/bin/sh

javac \
	-Xlint:unchecked \
	-cp jsr305-2.0.1.jar \
	src/testr*.java \
	src/udp*.java \
	src/ControlBuffer.java \
	src/GlobalSourceId.java \
	src/Header.java \
	src/Ints.java \
	src/NakConfirmPacket.java \
	src/NakPacket.java \
	src/NullNakPacket.java \
	src/OptionFragment.java \
	src/OptionHeader.java \
	src/OriginalData.java \
	src/Packet.java \
	src/Peer.java \
	src/PollPacket.java \
	src/PollResponsePacket.java \
	src/Preconditions.java \
	src/ReceiveWindow.java \
	src/SequenceNumber.java \
	src/SocketBuffer.java \
	src/SourcePathMessage.java \
	src/TransportSessionId.java \
	src/UnsignedInts.java
