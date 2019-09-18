package com.hivemq.maven;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author Georg Held
 */
public class HiveMQDirUtil {

    private static final @NotNull Logger log = LoggerFactory.getLogger(HiveMQDirUtil.class);
    private static final @NotNull String nodePrefix = "node-";


    /**
     * Returns the HiveMQ executable jar file from the given directory if it exists.
     *
     * @param hivemqJar    the hivemq jar filename
     * @param hivemqBinDir the directory where the HiveMQ jar file is located
     * @return the HiveMQ executable jar file
     * @throws MojoExecutionException if the jar file does not exist
     */
    static @NotNull File getHiveMQJarFile(final @NotNull String hivemqJar, final @NotNull File hivemqBinDir) throws MojoExecutionException {
        final File hivemqJarFile = new File(hivemqBinDir, hivemqJar);
        if (!hivemqJarFile.exists()) {
            throw new MojoExecutionException("HiveMQ Jar file " + hivemqJarFile.getAbsolutePath() + " does not exist!");
        }
        log.debug("HiveMQ jar file is located at {}", hivemqJarFile.getAbsolutePath());
        return hivemqJarFile;
    }

    /**
     * Returns the HiveMQ executable jar file from the given directory if it exists.
     *
     * @param conf          the hivemq jar filename
     * @param hiveMqConfDir the directory where the HiveMQ jar file is located
     * @return the HiveMQ executable jar file
     * @throws MojoExecutionException if the jar file does not exist
     */
    static @NotNull File getHiveMQConfFile(final @NotNull String conf, final @NotNull File hiveMqConfDir) throws MojoExecutionException {
        final File hivemqConfFile = new File(hiveMqConfDir, conf);
        if (!hivemqConfFile.exists()) {
            throw new MojoExecutionException("HiveMQ conf.xml file " + hivemqConfFile.getAbsolutePath() + " does not exist!");
        }
        log.debug("HiveMQ jar file is located at {}", hivemqConfFile.getAbsolutePath());
        return hivemqConfFile;
    }

    /**
     * Returns the /bin directory of the HiveMQ directory if it exists and if it is a directory.
     *
     * @param hiveMQDir the HiveMQ home directory
     * @return the bin directory of HiveMQ
     * @throws MojoExecutionException if the directory does not exist or it is no directory
     */
    static @NotNull File getHiveMQBinDir(final @NotNull File hiveMQDir) throws MojoExecutionException {
        final File hivemqBinDir = new File(hiveMQDir, "bin");
        log.debug("HiveMQ bin directory is located at {}", hivemqBinDir.getAbsolutePath());

        if (!hivemqBinDir.isDirectory()) {
            throw new MojoExecutionException(hivemqBinDir.getAbsolutePath() + " is not a directory!");
        }
        return hivemqBinDir;
    }

    /**
     * Returns the /conf directory of the HiveMQ directory if it exists and if it is a directory.
     *
     * @param hiveMQDir the HiveMQ home directory
     * @return the conf directory of HiveMQ
     * @throws MojoExecutionException if the directory does not exist or it is no directory
     */
    static @NotNull File getHiveMQConfDir(final @NotNull File hiveMQDir) throws MojoExecutionException {
        final File hivemqConfDir = new File(hiveMQDir, "conf");
        log.debug("HiveMQ conf directory is located at {}", hivemqConfDir.getAbsolutePath());

        if (!hivemqConfDir.isDirectory()) {
            throw new MojoExecutionException(hivemqConfDir.getAbsolutePath() + " is not a directory!");
        }
        return hivemqConfDir;
    }

    static @NotNull File[] generateNodeFolders(final @NotNull File baseDir, final @NotNull File hiveMQDir, final @NotNull int[] ports) throws MojoExecutionException, IOException {

        final File hiveMQConfDir = getHiveMQConfDir(hiveMQDir);
        final File hiveMQConfFile = getHiveMQConfFile("config.xml", hiveMQConfDir);
        final String hiveMQConf = FileUtils.readFileToString(hiveMQConfFile, StandardCharsets.UTF_8);

        final File[] nodeFolders = new File[ports.length];

        for (int i = 0; i < ports.length; i++) {
            final File nodeFolder = new File(baseDir, nodePrefix + ports[i]);
            nodeFolders[i] = nodeFolder;

            nodeFolder.mkdirs();
            final File nodeConfFolder = new File(nodeFolder, "conf");
            FileUtils.copyDirectory(hiveMQConfDir, nodeConfFolder);
            new File(nodeFolder, "data").mkdir();
            new File(nodeFolder, "log").mkdir();

            final File nodeConfFile = new File(nodeConfFolder, "config.xml");
            nodeConfFile.delete();
            if (i == 0) {
                final String cleanConfig = ConfigUtil.replaceClusterSection(hiveMQConf, ports, i);
                FileUtils.write(nodeConfFile, cleanConfig, StandardCharsets.UTF_8);
                log.info(cleanConfig);
            } else {
                final String cleanConfig = ConfigUtil.cleanAndReplaceClusterSection(hiveMQConf, ports, i);
                FileUtils.write(nodeConfFile, cleanConfig, StandardCharsets.UTF_8);
                log.info(cleanConfig);
            }
        }

        return nodeFolders;
    }
}
