The JVMDumper utility can be used to quickly extract Stack Traces & Mbean information from a running system, for all JVM's in one step.

usage: java -jar JVMDumper.jar {server-list-file} [{output-path}]

The format of the file {server-list-file} (e.g. servers.txt )  is a CSV with the format:

{FileNamePrefix},{connection}

where connection should include host name, port and any authorisation details. e.g. localhost:8181, or localhost:8181:uid:pwd

It will generate two files per JVM in the output path folder with the format:

<filenamePrefix><host><date>.tdump

<filenamePrefix><host><date>-mbean.html
