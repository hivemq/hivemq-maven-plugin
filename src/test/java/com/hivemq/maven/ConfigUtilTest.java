package com.hivemq.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConfigUtilTest {

    @Test
    public void replaceClusterSection() throws MojoExecutionException {
        final String config = "<?xml version=\"1.0\"?>\n" +
                "<hivemq>\n" +
                "    <cluster>\n" +
                "        <enabled>true</enabled>\n" +
                "        <transport>\n" +
                "           <tcp>\n" +
                "                <bind-address>192.168.1.1</bind-address>\n" +
                "                <bind-port>7800</bind-port>\n" +
                "           </tcp>\n" +
                "        </transport>\n" +
                "        <discovery>\n" +
                "            <static>\n" +
                "                <node>\n" +
                "                    <host>192.168.1.1</host>\n" +
                "                    <port>7800</port>\n" +
                "                </node>\n" +
                "                <node>\n" +
                "                    <host>192.168.1.2</host>\n" +
                "                    <port>7800</port>\n" +
                "                </node>\n" +
                "            </static>\n" +
                "        </discovery>\n" +
                "\n" +
                "    </cluster>\n" +
                "\n" +
                "    <anonymous-usage-statistics>\n" +
                "        <enabled>true</enabled>\n" +
                "    </anonymous-usage-statistics>\n" +
                "\n" +
                "</hivemq>";

        final String clean = ConfigUtil.replaceClusterSection(config, new int[]{1, 2, 3}, 2);

        assertEquals("<?xml version=\"1.0\"?>\n" +
                "<hivemq>\n" +
                "    <cluster>\n" +
                "        <enabled>true</enabled>\n" +
                "        <transport>\n" +
                "           <tcp>\n" +
                "                <bind-address>127.0.0.1</bind-address>\n" +
                "                <bind-port>3</bind-port>\n" +
                "           </tcp>\n" +
                "        </transport>\n" +
                "        <discovery>\n" +
                "            <static>\n" +
                "                <node>\n" +
                "                    <host>127.0.0.1</host>\n" +
                "                    <port>1</port>\n" +
                "                </node>\n" +
                "                <node>\n" +
                "                    <host>127.0.0.1</host>\n" +
                "                    <port>2</port>\n" +
                "                </node>\n" +
                "                <node>\n" +
                "                    <host>127.0.0.1</host>\n" +
                "                    <port>3</port>\n" +
                "                </node>\n" +
                "            </static>\n" +
                "        </discovery>\n" +
                "\n" +
                "    </cluster>\n" +
                "    <!-- <cluster> -->\n" +
                "<!--         <enabled>true</enabled> -->\n" +
                "<!--         <transport> -->\n" +
                "<!--            <tcp> -->\n" +
                "<!--                 <bind-address>192.168.1.1</bind-address> -->\n" +
                "<!--                 <bind-port>7800</bind-port> -->\n" +
                "<!--            </tcp> -->\n" +
                "<!--         </transport> -->\n" +
                "<!--         <discovery> -->\n" +
                "<!--             <static> -->\n" +
                "<!--                 <node> -->\n" +
                "<!--                     <host>192.168.1.1</host> -->\n" +
                "<!--                     <port>7800</port> -->\n" +
                "<!--                 </node> -->\n" +
                "<!--                 <node> -->\n" +
                "<!--                     <host>192.168.1.2</host> -->\n" +
                "<!--                     <port>7800</port> -->\n" +
                "<!--                 </node> -->\n" +
                "<!--             </static> -->\n" +
                "<!--         </discovery> -->\n" +
                "<!--  -->\n" +
                "<!--     </cluster> -->\n" +
                "\n" +
                "\n" +
                "    <anonymous-usage-statistics>\n" +
                "        <enabled>true</enabled>\n" +
                "    </anonymous-usage-statistics>\n" +
                "\n" +
                "</hivemq>", clean);
    }
}