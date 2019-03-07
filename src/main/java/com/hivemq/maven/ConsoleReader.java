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

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * @author Christian Götz
 * @author Dominik Obermaier
 * @author Abdullah Imal
 */
public class ConsoleReader extends Thread {

    private static final Logger log = LoggerFactory.getLogger(ConsoleReader.class);

    private InputStream inputStream;

    ConsoleReader(@NotNull final InputStream inputStream) {
        this.inputStream = inputStream;
    }

    @Override
    public void run() {
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            while (true) {
                if (reader.ready()) {
                    log.info(reader.readLine());
                } else {
                    try {
                        TimeUnit.MILLISECONDS.sleep(100);
                    } catch (InterruptedException e) {
                        log.error("An interruptedException occurred while reading the console!", e);
                    }
                }
            }
        } catch (IOException e) {
            log.error("An exception occurred while reading the console", e);
        }
    }
}