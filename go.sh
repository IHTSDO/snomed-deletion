#!/bin/bash
set -e;

while getopts ":cd" opt
do
	case $opt in
		c)
			mvn clean package
		;;
		d)
  			debugParams="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8080"
		;;
		help|\?)
			echo -e "Usage: [-c] [-d]"
			echo -e "\t c - (re)compile the tool"
			echo -e "\t d - debug mode, allows IDE to connect on debug port"
			exit 0
		;;
	esac
done

effectiveTime="20170131,20170301"
originalArchive="/Users/Peter/Backup/SnomedCT_USEditionRF2_Production_20170301T120000.zip"
negativeDeltaArchive="/Users/Peter/tmp/SnomedCT_RF2Release_USNegativeDelta_20170601_2.zip"
edition="US1000124"
memParams="-Xms6g -Xmx10g"
set -x;
java -jar ${memParams} ${debugParams} target/snomed-deletion.jar ${originalArchive} ${negativeDeltaArchive} ${effectiveTime} ${edition}


