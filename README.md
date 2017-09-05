# uml-to-openehr-tools
Tooling to support the conversion of UML into OpenEHR BMM and/or ADL

## xmi-to-bmm-converter
The XMI-to-BMM Converter is a command line tool that converts one or more XMI files listed in a config.xml configuration file and generates the corresponding BMM file in the output folder specified as a command line parameter.

To run the command, you will need to specify two command line arguments:

1. The location of the config.xml file as the first argument
1. The output directory path (to a valid directory) terminated with a path separator as the second argument

To build and run the XMI-to-BMM converter, you will need to perform the following steps:

1. Create a new config.xml file. For an example of a config.xml file see [example](https://github.com/cnanjo/uml-to-openehr-tools/blob/master/xmi-to-bmm-converter/src/main/resources/config.xml.template)
1. Create or identify an output repository that will store the generated bmm files
1. Go to the xmi-to-bmm-converter package
1. Run the following Maven command: `mvn clean compile assembly:single`
1. When completed, type: `cd target`
1. Run the application using the command: `java -jar XmiToBmmConverter-jar-with-dependencies.jar path/to/config.xml path/to/output/dir/`

