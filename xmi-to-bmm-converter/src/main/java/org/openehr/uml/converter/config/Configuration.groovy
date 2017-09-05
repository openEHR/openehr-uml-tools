package org.openehr.uml.converter.config

import org.openehr.uml.converter.exception.ParseException

import java.nio.file.Files
import java.nio.file.Paths;

/*
 * #%L
 * OpenEHR - OpenEHR UML Tools
 * %%
 * Copyright (C) 2016 - 2017 Cognitive Medical Systems
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 * Author: Claude Nanjo
 */

public class Configuration {

    private List<String> fileNames;
    private List<String> filePaths;
    private String outputDirectory;

    public Configuration() {
        fileNames = new ArrayList<String>();
        filePaths = new ArrayList<String>();
    }

    public void addFile(String name, String path) {
        fileNames.add(name);
        filePaths.add(path);
    }

    public List<String> getFileNames() {
        return fileNames;
    }

    public List<String> getFilePaths() {
        return filePaths;
    }

    String getOutputDirectory() {
        return outputDirectory
    }

    void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory
    }

    public static Configuration load(String configFileDir) {
        Configuration config = new Configuration()
        String configXml = null;
        if(configFileDir != null) {
            configXml = new String(Files.readAllBytes(Paths.get(configFileDir)));
        } else {
            configXml = this.getClass().getResource( '/config.xml' ).text
        }

        println 'Using config directory: ' + configFileDir

        try {
            def configuration  = new XmlSlurper().parseText(configXml);
            configuration.XmiFiles.XmiFile.each { file ->
                config.addFile(file.FileName.text(), file.FilePath.text())}
            configuration.OutputDirectory.each { dir ->
                config.setOutputDirectory(dir.text())}
        } catch(Exception e) {
            throw new ParseException("Error parsing configuration file", e)
        }
        return config;
    }
}
