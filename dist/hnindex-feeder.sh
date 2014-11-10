#!/bin/bash

SCRIPTDIR=$(dirname $0)

if [ -z "${JAVA_HOME}" ]
then
  JAVA_EXE=java
else
  JAVA_EXE=${JAVA_HOME}/bin/java
fi

${JAVA_EXE} -cp "${SCRIPTDIR}/lib/*:${SCRIPTDIR}/conf" com.bbdouglas.hnindex.Feed
