# hdfs-zip-reader

Java has the built in java.util.zip.ZipFile class, which allows a zip file to be read and then individual entries can be efficiently extracted from the zip. Unfortunately this library requires a zip file to be passed to it, and if you want to extract individual files from a zip stored in HDFS it will not work.

This library HDFS Zip Reader, uses the Java HDFS API to access the ZIP file, read its contents and then open an input stream for individual files in the Zip.

# How It Works

[Wikipedia](https://en.wikipedia.org/wiki/Zip_(file_format)) has a pretty good overview of the Zip file format, and there is a much more detail vesion on the [PKWare site](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT).

The quick and simple summary is that a Zip file is made up of a series of entries, each with a header describing the entry. These entries are the files stored in the zip. At the end of the file, there is a Central Directory, which contains an entry for every file in the Zip, along with the entries offset from the start of the Zip file. Finally there is an End Of Central Directory record, which gives the number of entries in the complete Zip, along with the start position of the central directory.

Therefore, to list the contents of a Zip, you can start at the end of the file, read the end of central directory record, then jump to the start of the central directory and read the details of all files in the archive. Given this information, it is fairly easy to extract any given file.

# Building

The library can be built with Maven in the usual way:

    mvn package

The shade plugin is included in the pom.xml to build an uber jar containing all the dependencies, so after building you will find the following files in the target directory:

 * Jar including all dependencies - HDFSZipReader-1.0-SNAPSHOT.jar
 * Compiled jar with just this library - original-HDFSZipReader-1.0-SNAPSHOT.jar

# Usage

## Command line

The class com.sodonnel.hadoop.zip.ZipExtractor is a simple command line utility to list and extract files from a Zip. The most simple way to run it, is by adding both the Hadoop Config directory and this jar to the classpath, and running it as follows:

    export CLASSPATH=HDFSZipReader-1.0-SNAPSHOT.jar:/Users/sodonnell/hadoop-conf
    java com.sodonnel.hadoop.zip.ZipExtractor <path to zip file> [<regex to filter files> <option to extract the files>]

### Parameters

* The path to the zip file should be in the format:

```
file:///User/sodonnell/Downloads/test.zip # Local files
hdfs:///user/sodonnell/test.zip           # Files in HDFS
```	

* The second parameter is a regex and you should quote it to prevent regex characters affecting the shell - to extact all files pass ".*", but in general you will want to extract certain files by passing their full path, or a regex, eg:

```
logs/myservice_hosta.log
```

Or, to get all 'myservice' logs:

    "logs/myservice.*\.log"

* The final parameter can be any string - if only 2 parameters are passed (the Zip file and the regex) the filtered files are printed. If the 3rd parameter is also present, the files will be extracted into the current directory.

In this version the way the files are extracted is to strip the path off the zip file path and create a local file called 'extracted\_<zip\_filename>'. For example, if you have a file in the Zip called logs/myservice.log, the extracted file will be extracted_myservice.log. Please see the limitations section for more information on this. I expect to be able to improve the naming of extracted files in a future release.


### Examples

List the contents of the Zip:

    java com.sodonnel.hadoop.zip.ZipExtractor hdfs:///user/sodonnel/test.zip

Filter out all Files with except those containing log or stderr in the filename:

    java com.sodonnel.hadoop.zip.ZipExtractor hdfs:///user/sodonnel/test.zip ".*(logs|stderr).*"

Extract the filtered file list:

    java com.sodonnel.hadoop.zip.ZipExtractor hdfs:///user/sodonnel/test.zip ".*(logs|stderr).*" extract


### Kerberos Secured Clusters

So long as you can kinit successfully on your OS to get a ticket, then the command line utility should work without any further configuration.

## Usage in a Java Application

TODO - have a look at the com.sodonnel.hadoop.zip.ZipReader class.


# Limitations

* The current implementation of the command line extractor creates a local file with the same name of the entry in the Zip without its path, prefixed with 'extracted_'. If you attempt to extract two files at different paths but with the same name, the second file will overwrite the first, eg:

```
patha/test.txt
pathb/test.txt
```

The extracted file will be called extracted_test.txt. This will be addressed in a later release.

* A Zip with over 65k entries will not be readable in this version - the original Zip format did not support more than 64k entries, until Zip64 was introduced. This will be fixed in a later version

* A Zip over 4GB in size will not be readable - this will be fixed in a later version
