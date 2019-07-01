/*
 * Copyright 2013 dc-square GmbH
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.hivemq.maven;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Optional;

import static org.junit.Assert.*;

/**
 * @author Dominik Obermaier
 * @author Abdullah Imal
 */
public class HiveMQMojoTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();


    @Test
    public void test_check_preconditions_wrong_debug_mode() throws Exception {
        final HiveMQMojo hiveMQMojo = new HiveMQMojo();
        hiveMQMojo.debugMode = "INVALID";

        expectedException.expect(MojoExecutionException.class);
        expectedException.expectMessage("parameter 'debugMode' must be either CLIENT, SERVER or NONE");

        hiveMQMojo.execute();
    }

    @Test
    public void test_extension_folder_without_extensions() throws Exception {
        final HiveMQMojo hiveMQMojo = new HiveMQMojo();
        hiveMQMojo.noExtensions = true;

        final Optional<String> extensionFolder = hiveMQMojo.createExtensionFolder();
        assertFalse(extensionFolder.isPresent());
    }

    @Test
    public void test_create_extension_folder() throws Exception {
        final File tempFolder = temporaryFolder.newFolder();

        final InputStream inputStream = new ByteArrayInputStream("test content".getBytes());
        final File testTxt = new File(tempFolder, "test.txt");
        FileUtils.copyToFile(inputStream, testTxt);
        final File extensionZipFile = new File(tempFolder, "myextension.zip");

        final ZipFile zipFile = new ZipFile(extensionZipFile.getAbsolutePath());
        zipFile.createZipFile(testTxt, new ZipParameters());

        final HiveMQMojo hiveMQMojo = new HiveMQMojo();
        hiveMQMojo.noExtensions = false;
        hiveMQMojo.extensionDirectory = tempFolder;
        hiveMQMojo.extensionZipName = extensionZipFile.getName();
        final File includedResource = new File(tempFolder, "/driver/sql");
        includedResource.getParentFile().mkdirs();
        includedResource.createNewFile();
        hiveMQMojo.includeResources = includedResource.getParentFile();


        final Optional<String> extensionFolder = hiveMQMojo.createExtensionFolder();

        final File debugFolder = new File(tempFolder, "debug");

        assertTrue(extensionFolder.isPresent());
        assertEquals("-Dhivemq.extensions.folder=" + debugFolder.getAbsolutePath(), extensionFolder.get());

        assertEquals(2, debugFolder.list().length);
    }

    @Test
    public void test_hivemq_bin_dir_no_directory() throws Exception {
        final HiveMQMojo hiveMQMojo = new HiveMQMojo();
        hiveMQMojo.hiveMQDir = temporaryFolder.newFile();

        expectedException.expect(MojoExecutionException.class);
        expectedException.expectMessage("is not a directory");

        hiveMQMojo.getHiveMQBinDir();
    }

    @Test
    public void test_hivemq_jar_file_does_not_exist() throws Exception {
        final HiveMQMojo hiveMQMojo = new HiveMQMojo();
        hiveMQMojo.hivemqJar = "jarname";
        final File file = temporaryFolder.newFile();

        expectedException.expect(MojoExecutionException.class);
        expectedException.expectMessage("does not exist");

        hiveMQMojo.getHiveMQJarFile(file);
    }
}
