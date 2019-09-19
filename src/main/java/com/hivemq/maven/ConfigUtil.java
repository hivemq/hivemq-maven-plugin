package com.hivemq.maven;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Georg Held
 */
public class ConfigUtil {
    private static final @NotNull Logger log = LoggerFactory.getLogger(ConfigUtil.class);

    private static final @NotNull Function<MatchResult, String> commentOutMatch = (result) -> {
        final StringJoiner stringJoiner = new StringJoiner(" -->\n<!-- ", "<!-- ", " -->\n");
        final String[] split = result.group().split("\n");
        for (final String line : split) {
            stringJoiner.add(line);
        }

        return stringJoiner.toString();
    };

    private static final int portSectionGroup = 1;
    private static final @NotNull Pattern listenersPattern = Pattern.compile("<listeners>.+?(<port>.+?</port>).+?</listeners>", Pattern.DOTALL);
    private static final @NotNull Pattern clusterPattern = Pattern.compile("<cluster>.+?</cluster>", Pattern.DOTALL);
    private static final @NotNull Pattern controlCenterPattern = Pattern.compile("<control-center>.+?</control-center>", Pattern.DOTALL);

    private static final @NotNull String hiveMQOpeningTag = "<hivemq>";
    private static final @NotNull String hiveMQClosingTag = "</hivemq>";
    private static final @NotNull String clusterFormat = "    <cluster>\n" +
            "        <enabled>true</enabled>\n" +
            "        <transport>\n" +
            "           <tcp>\n" +
            "                <bind-address>127.0.0.1</bind-address>\n" +
            "                <bind-port>%d</bind-port>\n" +
            "           </tcp>\n" +
            "        </transport>\n" +
            "        <discovery>\n" +
            "            <static>\n" +
            "%s" +
            "            </static>\n" +
            "        </discovery>\n" +
            "\n" +
            "    </cluster>";
    private static final @NotNull String nodeFormat = "                <node>\n" +
            "                    <host>127.0.0.1</host>\n" +
            "                    <port>%d</port>\n" +
            "                </node>\n";
    private static final @NotNull String disabledControlCenter = "    <control-center>\n" +
            "        <enabled>false</enabled>\n" +
            "    </control-center>\n";
    private static final @NotNull String portFormat = "<port>%d</port>";

    /**
     * Replaces the configured listener port with a random generated one.
     *
     * @param hiveMQConfig the current HiveMQ config xml.
     * @return the config with a replaced listener port.
     */
    public static @NotNull String replaceListenerPort(final @NotNull String hiveMQConfig) {
        final Matcher matcher = listenersPattern.matcher(hiveMQConfig);
        final StringBuffer stringBuffer = new StringBuffer();
        while (matcher.find()) {
            final String portSection = String.format(portFormat, getPort());
            log.info("Replacing listener port {} with random generated port {}.", matcher.group(portSectionGroup), portSection);
            final StringBuilder stringBuilder = new StringBuilder(matcher.group());
            stringBuilder.replace(matcher.start(portSectionGroup) - matcher.start(), matcher.end(portSectionGroup) - matcher.start(), portSection);
            matcher.appendReplacement(stringBuffer, stringBuilder.toString());
        }
        matcher.appendTail(stringBuffer);

        return stringBuffer.toString();
    }

    /**
     * Comments out an existing Control Center section and places one with the &lt;enabled&gt;false&lt;/enabled&gt; tag.
     *
     * @param hiveMQConfig the current HiveMQ config xml.
     * @return the config with disabled control center
     */
    public static @NotNull String removeControlCenterSection(final @NotNull String hiveMQConfig) {
        final String cleaned = controlCenterPattern.matcher(hiveMQConfig).replaceAll(commentOutMatch);

        final String before = StringUtils.substringBefore(cleaned, hiveMQClosingTag);
        final String after = StringUtils.substringAfter(cleaned, hiveMQClosingTag);

        return before + '\n' + disabledControlCenter + hiveMQClosingTag + after;
    }

    /**
     * Changes the configuration to join a cluster and removes/changes potential conflicting configuration section. Use this method for the non primary nodes.
     *
     * @param hiveMQConfig the current HiveMQ config.
     * @param ports        the generated list of cluster transport ports
     * @param node         the node number for which the config is changed.
     * @return an updated HiveMQ config for one non primary node
     * @throws MojoExecutionException if ports does not contain enough entries
     */
    public static @NotNull String cleanAndReplaceClusterSection(final @NotNull String hiveMQConfig, final @NotNull int[] ports, final int node) throws MojoExecutionException {
        String clean = replaceListenerPort(hiveMQConfig);
        clean = removeControlCenterSection(clean);
        return replaceClusterSection(clean, ports, node);
    }

    /**
     * Changes the configuration to join a cluster. Use this method for the primary node.
     *
     * @param hiveMQConfig the current HiveMQ config.
     * @param ports        the generated list of cluster transport ports
     * @param node         the node number for which the config is changed.
     * @return an updated HiveMQ config for one non primary node
     * @throws MojoExecutionException if ports does not contain enough entries
     */
    public static @NotNull String replaceClusterSection(final @NotNull String hiveMQConfig, final @NotNull int[] ports, final int node) throws MojoExecutionException {
        if (node >= ports.length) {
            throw new MojoExecutionException("List of ports must contain one port per cluster node");
        }

        final String cleaned = clusterPattern.matcher(hiveMQConfig).replaceAll(commentOutMatch);
        String nodes = "";

        for (final int port : ports) {
            nodes += String.format(nodeFormat, port);
        }

        final String clusterConfig = String.format(clusterFormat, ports[node], nodes);

        final String before = StringUtils.substringBefore(cleaned, hiveMQOpeningTag);
        final String after = StringUtils.substringAfter(cleaned, hiveMQOpeningTag);

        return before + hiveMQOpeningTag + '\n' + clusterConfig + after;
    }

    /**
     * Generate an array of free random port numbers.
     *
     * @param nodeCount the amount of port numbers to be generated.
     * @return an array of probably free port numbers.
     */
    public static @NotNull int[] getPorts(final int nodeCount) {
        final ServerSocket[] serverSockets = new ServerSocket[nodeCount];
        final int[] ports = new int[nodeCount];
        try {
            for (int i = 0; i < nodeCount; i++) {
                serverSockets[i] = new ServerSocket(0);
            }
            for (int i = 0; i < nodeCount; i++) {
                ports[i] = serverSockets[i].getLocalPort();
            }
            for (int i = 0; i < nodeCount; i++) {
                serverSockets[i].close();
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        return ports;
    }

    /**
     * Generate an free random port number.
     *
     * @return a probably free port numbers.
     */
    public static int getPort() {
        try (final ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
