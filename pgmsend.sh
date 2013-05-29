#!/bin/sh

java \
	-cp log4j-api-2.0-beta6.jar\;log4j-core-2.0-beta6.jar\;target/classes \
	-enableassertions \
	pgmsend $*
