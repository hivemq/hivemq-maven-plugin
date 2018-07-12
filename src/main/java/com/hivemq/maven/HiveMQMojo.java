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

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.StaticLoggerBinder;
import org.stringtemplate.v4.ST;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;

/**
 * @author Christian Götz
 * @author Dominik Obermaier
 */
@Mojo(name = "hivemq", defaultPhase = LifecyclePhase.PACKAGE)
public class HiveMQMojo extends AbstractMojo {

    private static final Logger log = LoggerFactory.getLogger(HiveMQMojo.class);
    private static final ST DEBUG_PARAMETER_CLIENT = new ST("-agentlib:jdwp=transport=dt_socket,server=n,address=<host>:<port>");
    private static final ST DEBUG_PARAMETER_SERVER = new ST("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=<port>");
    private static final String DEBUG_MODE_CLIENT = "CLIENT";
    private static final String DEBUG_MODE_SERVER = "SERVER";
    private static final String DEBUG_MODE_NONE = "NONE";
    @Parameter(defaultValue = "${project.artifactId}", required = true, readonly = true)
    String artifactId;
    @Parameter(defaultValue = "${project.version}", required = true, readonly = true)
    String version;
    @Parameter(defaultValue = "${basedir}", required = true, readonly = true)
    String baseDir;
    @Parameter(property = "pluginJarName", required = false)
    String pluginJarName;
    @Parameter(defaultValue = "true", property = "verbose", required = true)
    boolean verbose;
    @Parameter(defaultValue = "false", property = "noPlugins", required = true)
    boolean noPlugins;
    @Parameter(defaultValue = DEBUG_MODE_SERVER, property = "debugMode", required = true)
    String debugMode;
    @Parameter(defaultValue = "5005", property = "debugPort", required = true)
    String debugPort;
    @Parameter(defaultValue = "localhost", property = "debugServerHostName", required = true)
    String debugServerHostName;
    @Parameter(defaultValue = "${project.build.directory}", property = "pluginDir", required = true)
    File pluginDirectory;
    @Parameter(property = "hivemqDir", required = true)
    File hiveMQDir;
    @Parameter(defaultValue = "hivemq.jar", property = "hivemqJar", required = true)
    String hivemqJar;
    @Parameter
    Map<String, String> systemPropertyVariables;
    @Parameter
    File[] additionalPluginFiles;

    /**
     * {@inheritDoc}
     *
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    public void execute() throws MojoExecutionException, MojoFailureException {

        //Bridging the SLF4J logger to the Maven logger
        StaticLoggerBinder.getSingleton().setMavenLog(this.getLog());

        checkPreconditions();

        final List<String> commands = assembleCommand();

        final Process hivemqProcess = startHiveMQ(commands);

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                log.info("Stopping HiveMQ");

                hivemqProcess.destroy();
            }
        }


        ));

        try {
            if (verbose) {
                showProcessOutputs(hivemqProcess);
            }
            hivemqProcess.waitFor();
        } catch (InterruptedException e) {
            throw new MojoFailureException("A interruptedException was thrown");
        }
    }

    /**
     * Attaches a Console Reader to the running process which outputs the logs of HiveMQ
     *
     * @param hivemqProcess the process of the HiveMQ server
     * @throws InterruptedException
     */
    private void showProcessOutputs(final Process hivemqProcess) throws InterruptedException {
        final ConsoleReader reader = new ConsoleReader(hivemqProcess.getInputStream());
        reader.join();
    }

    /**
     * Checks for preconditions of the given paramters and throws exceptions if the conditions are not satisfied
     *
     * @throws MojoExecutionException
     */
    private void checkPreconditions() throws MojoExecutionException {

        if (!DEBUG_MODE_CLIENT.equals(debugMode) && !DEBUG_MODE_SERVER.equals(debugMode) && !DEBUG_MODE_NONE.equals(debugMode)) {
            throw new MojoExecutionException("parameter 'debugMode' must be either " + DEBUG_MODE_CLIENT + ", "
                    + DEBUG_MODE_SERVER + " or " + DEBUG_MODE_NONE);
        }
    }

    /**
     * Starts the HiveMQ process with the given parameters
     *
     * @param commandParameters the parameters
     * @return the running HiveMQ process
     * @throws MojoExecutionException if the execution of HiveMQ did not work
     */
    private Process startHiveMQ(final List<String> commandParameters) throws MojoExecutionException {
        final ProcessBuilder processBuilder = new ProcessBuilder(commandParameters);

        processBuilder.directory(hiveMQDir);
        processBuilder.redirectErrorStream(true);

        Process p;
        try {
            p = processBuilder.start();
        } catch (IOException e) {
            log.error("An error occured while starting HiveMQ:", e);
            throw new MojoExecutionException("An error occured while starting HiveMQ", e);
        }
        return p;
    }

    /**
     * Creates the list of commands needed to pass to the process builder
     *
     * @return a list of command strings
     */
    private List<String> assembleCommand() throws MojoExecutionException {

        final File hivemqBinDir = getHiveMQBinDir();

        final File hivemqJarFile = getHiveMQJarFile(hivemqBinDir);

        final List<String> commands = newArrayList();

        commands.add("java");

        if (DEBUG_MODE_CLIENT.equals(debugMode)) {
            DEBUG_PARAMETER_CLIENT.add("port", debugPort).add("host", debugServerHostName);
            commands.add(DEBUG_PARAMETER_CLIENT.render());
        } else if (DEBUG_MODE_SERVER.equals(debugMode)) {
            DEBUG_PARAMETER_SERVER.add("port", debugPort);
            commands.add(DEBUG_PARAMETER_SERVER.render());
        }

        final Optional<String> debugFolder = createTempPluginFolder();
        if (debugFolder.isPresent()) {
            commands.add(debugFolder.get());

        }
        commands.addAll(getSystemProperties());
        commands.add("-Dhivemq.home=" + hiveMQDir.getAbsolutePath());
        commands.add("-noverify");
        commands.add("-jar");
        commands.add(hivemqJarFile.getAbsolutePath());

        return commands;
    }

    /**
     * @return a list of systemproperty strings matching "-D%key%=%value%
     */
    @VisibleForTesting
    List<String> getSystemProperties() {
        final List<String> systemProperties = newArrayList();
        if (systemPropertyVariables != null) {
            for (Map.Entry<String, String> systemPropertyEntry : systemPropertyVariables.entrySet()) {
                systemProperties.add(format("-D%s=%s", systemPropertyEntry.getKey() , systemPropertyEntry.getValue()));
            }
        }
        return systemProperties;
    }

    /**
     * Returns the HiveMQ executable jar file from the given directory if it exists.
     *
     * @param hivemqBinDir the directory where the HiveMQ jar file is located
     * @return the HiveMQ executable jar file
     * @throws MojoExecutionException if the jar file does not exist
     */
    @VisibleForTesting
    File getHiveMQJarFile(final File hivemqBinDir) throws MojoExecutionException {
        final File hivemqJarFile = new File(hivemqBinDir, hivemqJar);
        if (!hivemqJarFile.exists()) {
            throw new MojoExecutionException("HiveMQ Jar file " + hivemqJarFile.getAbsolutePath() + " does not exist");
        }
        log.debug("HiveMQ jar file is located at {}", hivemqJarFile.getAbsolutePath());
        return hivemqJarFile;
    }

    /**
     * Returns the /bin directory of the HiveMQ directory if it exists and if it is a directory.
     *
     * @return the bin directory of HiveMQ
     * @throws MojoExecutionException if the directory does not exist or it is no directory
     */
    @VisibleForTesting
    File getHiveMQBinDir() throws MojoExecutionException {
        final File hivemqBinDir = new File(hiveMQDir, "bin");
        log.debug("HiveMQ bin directory is located at {}", hivemqBinDir.getAbsolutePath());

        if (!hivemqBinDir.isDirectory()) {
            throw new MojoExecutionException(hivemqBinDir.getAbsolutePath() + " is not a directory!");
        }
        return hivemqBinDir;
    }

    /**
     * Creates the debug folder where the packaged plugin is copied to
     *
     * @return a {@link Optional} which can contain the parameter of the HiveMQ plugin folder
     */
    @VisibleForTesting
    Optional<String> createTempPluginFolder() throws MojoExecutionException {
        File debugFolder;
        if (!noPlugins) {
            if (pluginJarName == null) {
                pluginJarName = artifactId + "-" + version + ".jar";
            }

            debugFolder = new File(pluginDirectory, "debug");

            if (debugFolder.exists()) {
                try {
                    FileUtils.deleteDirectory(debugFolder);
                } catch (IOException e) {
                    throw new MojoExecutionException("An error occured while deleting " + debugFolder.getAbsolutePath(), e);
                }
            }

            final boolean mkdirsSuccessful = debugFolder.mkdirs();
            if (!mkdirsSuccessful) {
                throw new MojoExecutionException("Could not create " + debugFolder.getAbsolutePath());
            }

            try {
                FileUtils.copyFile(new File(pluginDirectory, pluginJarName), new File(debugFolder, pluginJarName));
            } catch (IOException e) {
                throw new MojoExecutionException("Error while copying plugin to debug folder", e);
            }

            final File resourcesDir = new File(baseDir + File.separator + "src" + File.separator + "main" + File.separator + "resources");

            if (resourcesDir.exists()) {

                String[] propertyFiles = resourcesDir.list();

                if (propertyFiles != null && propertyFiles.length > 0) {
                    for (String propertyFile : propertyFiles) {
                        try {
                            final File file = new File(resourcesDir, propertyFile);
                            if (file.isFile()) {
                                FileUtils.copyFile(file, new File(debugFolder, propertyFile));
                            }
                        } catch (IOException e) {
                            throw new MojoExecutionException("Error while copying property file to debug folder", e);
                        }
                    }
                }
            }

            /*
             * Copies additionalPlugins to debugFolder
             */
            copyAdditionalPluginFiles(debugFolder);

            return Optional.of("-Dhivemq.plugin.folder=" + debugFolder.getAbsolutePath());
        }
        return Optional.absent();
    }

    @VisibleForTesting
    void copyAdditionalPluginFiles(final File debugFolder) throws MojoExecutionException {
        if (debugFolder == null) {
            return;
        }

        if (additionalPluginFiles != null) {
            for (File additionalPluginFile : additionalPluginFiles) {
                try {
                    FileUtils.copyFile(additionalPluginFile, new File(debugFolder, additionalPluginFile.getName()));
                    log.debug("Copied additionalPluginFile {} to debugFolder", additionalPluginFile);
                } catch (IOException e) {
                    throw new MojoExecutionException("Error while copying plugin to debug folder", e);
                }
            }
        }
    }

}

