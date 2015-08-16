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

import com.google.common.base.Optional;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.assertj.guava.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * @author Dominik Obermaier
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
    public void test_plugin_folder_without_plugins() throws Exception {
        final HiveMQMojo hiveMQMojo = new HiveMQMojo();
        hiveMQMojo.noPlugins = true;

        final Optional<String> tempPluginFolder = hiveMQMojo.createTempPluginFolder();

        assertThat(tempPluginFolder).isAbsent();
    }

    @Test
    public void test_create_plugin_folder() throws Exception {
        final File tempFolder = temporaryFolder.newFolder();
        final File file = new File(tempFolder, "myjar.jar");
        file.createNewFile();
        final HiveMQMojo hiveMQMojo = new HiveMQMojo();
        hiveMQMojo.noPlugins = false;
        hiveMQMojo.pluginDirectory = tempFolder;
        hiveMQMojo.pluginJarName = file.getName();

        final Optional<String> tempPluginFolder = hiveMQMojo.createTempPluginFolder();

        final File debugFolder = new File(tempFolder, "debug");

        assertThat(tempPluginFolder).isPresent().contains("-Dhivemq.plugin.folder=" + debugFolder.getAbsolutePath());

        assertEquals(1, debugFolder.list().length);
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
