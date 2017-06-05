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

host="127.0.0.1:3306"
database="us_20170301"
username="root"
password="\"\""
effectiveTime="20170301"
dbParams="${host} ${database} ${username} ${password} "
negativeDeltaArchive="/Users/Peter/tmp/SnomedCT_RF2Release_USNegativeDelta_20170601.zip"

memParams="-Xms3g -Xmx8g"
set -x;
java -jar ${memParams} ${debugParams} target/snomed-deletion.jar ${dbParams} ${negativeDeltaArchive} ${effectiveTime}


