# snomed-deletion
Tool to take a set of SNOMED negative deltas and apply them to create a resulting delta which is the recalculated latest state (ie snapshot) for the targetted components.

### build:

mvn clean package

### useage:

```go.sh [-c] [-d]
c - (re)compile the tool
d - debug mode, allows IDE to connect on debug port
```

Ensure that the parameters in go.sh are modified eg:

effectiveTime="20170131,20170301"
originalArchive="/Users/Peter/Backup/SnomedCT_USEditionRF2_Production_20170301T120000.zip"
negativeDeltaArchive="/Users/Peter/tmp/SnomedCT_RF2Release_USNegativeDelta_20170601_2.zip"
edition="US1000124"

Or alternative call the java jar file directly:

memParams="-Xms6g -Xmx10g"
java -jar ${memParams} ${debugParams} target/snomed-deletion.jar ${originalArchive} ${negativeDeltaArchive} ${effectiveTime} ${edition}



