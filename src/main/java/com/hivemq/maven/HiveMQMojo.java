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
import net.lingala.zip4j.exception.ZipException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.StaticLoggerBinder;
import org.stringtemplate.v4.ST;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author Christian Götz
 * @author Dominik Obermaier
 * @author Abdullah Imal
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
    @Parameter(property = "extensionZipName", required = false)
    String extensionZipName;
    @Parameter(defaultValue = "true", property = "verbose", required = true)
    boolean verbose;
    @Parameter(defaultValue = "false", property = "noExtensions", required = true)
    boolean noExtensions;
    @Parameter(defaultValue = DEBUG_MODE_SERVER, property = "debugMode", required = true)
    String debugMode;
    @Parameter(defaultValue = "5005", property = "debugPort", required = true)
    String debugPort;
    @Parameter(defaultValue = "localhost", property = "debugServerHostName", required = true)
    String debugServerHostName;
    @Parameter(defaultValue = "${project.build.directory}", property = "extensionDir", required = true)
    File extensionDirectory;
    @Parameter(property = "hivemqDir", required = true)
    File hiveMQDir;
    @Parameter(defaultValue = "hivemq.jar", property = "hivemqJar", required = true)
    String hivemqJar;

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
        if (!hivemqProcess.isAlive()) {
            throw new MojoFailureException("HiveMQ process could not be started!");
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Stopping HiveMQ process.");
            hivemqProcess.destroy();

            final CompletableFuture<Process> processCompletableFuture = hivemqProcess.onExit();
            while (!processCompletableFuture.isDone()) {
                log.info("Waiting for HiveMQ to stop.");
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    log.error("An interruptedException occurred while waiting for HiveMQ to stop!");
                }
            }
        }));

        try {
            if (verbose) {
                showProcessOutputs(hivemqProcess);
            }
            hivemqProcess.waitFor();
        } catch (InterruptedException e) {
            throw new MojoFailureException("An interruptedException was thrown while HiveMQ was running!");
        }
    }

    /**
     * Attaches a Console Reader to the running process which outputs the logs of HiveMQ
     *
     * @param hivemqProcess the process of the HiveMQ server
     */
    private void showProcessOutputs(@NotNull final Process hivemqProcess) {
        final ConsoleReader reader = new ConsoleReader(hivemqProcess.getInputStream());
        Executors.newSingleThreadExecutor().submit(reader);
    }

    /**
     * Checks for preconditions of the given parameters.
     *
     * @throws MojoExecutionException Will be thrown when the conditions are not satisfied.
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
    private Process startHiveMQ(@NotNull final List<String> commandParameters) throws MojoExecutionException {
        final ProcessBuilder processBuilder = new ProcessBuilder(commandParameters);

        processBuilder.directory(hiveMQDir);
        processBuilder.redirectErrorStream(true);

        final Process p;
        try {
            p = processBuilder.start();
        } catch (IOException e) {
            log.error("An error occurred while starting HiveMQ:", e);
            throw new MojoExecutionException("An error occurred while starting HiveMQ!", e);
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

        final List<String> commands = new ArrayList<>();

        final String javaHome = System.getProperty("java.home");
        String customJavaCommand = null;
        if (javaHome != null && !javaHome.isEmpty() && javaHome.trim().length() > 0) {
            if (SystemUtils.IS_OS_WINDOWS) {
                final File javaExe = new File(new File(javaHome), "\\bin\\java.exe");
                if (javaExe.exists()) {
                    customJavaCommand = javaExe.getAbsolutePath();
                }
            } else if (SystemUtils.IS_OS_UNIX) {
                final File javaBin = new File(new File(javaHome), "/bin/java");
                if (javaBin.exists()) {
                    customJavaCommand = javaBin.getAbsolutePath();

                }
            }
        }
        if (customJavaCommand == null) {
            commands.add("java");
        } else {
            commands.add(customJavaCommand);
        }


        if (DEBUG_MODE_CLIENT.equals(debugMode)) {
            DEBUG_PARAMETER_CLIENT.add("port", debugPort).add("host", debugServerHostName);
            commands.add(DEBUG_PARAMETER_CLIENT.render());
        } else if (DEBUG_MODE_SERVER.equals(debugMode)) {
            DEBUG_PARAMETER_SERVER.add("port", debugPort);
            commands.add(DEBUG_PARAMETER_SERVER.render());
        }

        final Optional<String> debugFolder = createExtensionFolder();
        debugFolder.ifPresent(commands::add);

        commands.add("-Djava.net.preferIPv4Stack=true");
        commands.add("-Dhivemq.home=" + hiveMQDir.getAbsolutePath());
        commands.add("-noverify");

        commands.add("--add-opens");
        commands.add("java.base/java.lang=ALL-UNNAMED");
        commands.add("--add-opens");
        commands.add("java.base/java.nio=ALL-UNNAMED");
        commands.add("--add-opens");
        commands.add("java.base/sun.nio.ch=ALL-UNNAMED");
        commands.add("--add-opens");
        commands.add("jdk.management/com.sun.management.internal=ALL-UNNAMED");
        commands.add("--add-exports");
        commands.add("java.base/jdk.internal.misc=ALL-UNNAMED");

        commands.add("-jar");
        commands.add(hivemqJarFile.getAbsolutePath());

        return commands;
    }

    /**
     * Returns the HiveMQ executable jar file from the given directory if it exists.
     *
     * @param hivemqBinDir the directory where the HiveMQ jar file is located
     * @return the HiveMQ executable jar file
     * @throws MojoExecutionException if the jar file does not exist
     */
    @TestOnly
    File getHiveMQJarFile(@NotNull final File hivemqBinDir) throws MojoExecutionException {
        final File hivemqJarFile = new File(hivemqBinDir, hivemqJar);
        if (!hivemqJarFile.exists()) {
            throw new MojoExecutionException("HiveMQ Jar file " + hivemqJarFile.getAbsolutePath() + " does not exist!");
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
    @TestOnly
    File getHiveMQBinDir() throws MojoExecutionException {
        final File hivemqBinDir = new File(hiveMQDir, "bin");
        log.debug("HiveMQ bin directory is located at {}", hivemqBinDir.getAbsolutePath());

        if (!hivemqBinDir.isDirectory()) {
            throw new MojoExecutionException(hivemqBinDir.getAbsolutePath() + " is not a directory!");
        }
        return hivemqBinDir;
    }

    /**
     * Creates the debug folder where the packaged extension is unzipped to
     *
     * @return a {@link Optional} which can contain the parameter of the HiveMQ extension folder
     */
    @TestOnly
    Optional<String> createExtensionFolder() throws MojoExecutionException {
        if (noExtensions) {
            return Optional.empty();
        }

        if (extensionZipName == null) {
            extensionZipName = artifactId + "-" + version + "-distribution.zip";
        }
        final File extensionZipFile = new File(extensionDirectory, extensionZipName);
        if (!extensionZipFile.exists()) {
            throw new MojoExecutionException("Could not find extension zip file " + extensionZipFile.getAbsolutePath());
        }

        final File debugFolder = new File(extensionDirectory, "debug");
        if (debugFolder.exists()) {
            try {
                FileUtils.deleteDirectory(debugFolder);
            } catch (IOException e) {
                throw new MojoExecutionException("An error occurred while deleting " + debugFolder.getAbsolutePath(), e);
            }
        }

        final boolean mkdirsSuccessful = debugFolder.mkdirs();
        if (!mkdirsSuccessful) {
            throw new MojoExecutionException("Could not create " + debugFolder.getAbsolutePath());
        }

        try {
            final ZipFile zipFile = new ZipFile(extensionZipFile.getAbsolutePath());
            zipFile.extractAll(debugFolder.getAbsolutePath());
        } catch (ZipException e) {
            throw new MojoExecutionException("Error while copying extension to debug folder", e);
        }

        return Optional.of("-Dhivemq.extensions.folder=" + debugFolder.getAbsolutePath());
    }

}
