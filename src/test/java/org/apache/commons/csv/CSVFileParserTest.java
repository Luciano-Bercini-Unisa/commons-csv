/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Parse tests using test files
 */
class CSVFileParserTest {

    private static final File BASE_DIR = new File("src/test/resources/org/apache/commons/csv/CSVFileParser");

    static Stream<File> generateData() {
        final File[] files = BASE_DIR.listFiles((dir, name) -> name.startsWith("test") && name.endsWith(".txt"));
        return files != null ? Stream.of(files) : Stream.empty();
    }

    private String readTestData(final BufferedReader reader) throws IOException {
        String line;
        do {
            line = reader.readLine();
        } while (line != null && line.startsWith("#"));
        return line;
    }

    @ParameterizedTest
    @MethodSource("generateData")
    void testCSVFile(final File testFile) throws Exception {
        try (FileReader fr = new FileReader(testFile); BufferedReader testDataReader = new BufferedReader(fr)) {
            String line = readTestData(testDataReader);
            assertNotNull("file must contain config line", line);
            final String[] split = line.split(" ");
            assertTrue(split.length >= 1, testFile.getName() + " require 1 param");
            // first line starts with csv data file name
            CSVFormat format = CSVFormat.newFormat(',').builder().setQuote('"').get();
            boolean checkComments = false;
            for (int i = 1; i < split.length; i++) {
                final String option = split[i];
                final String[] optionParts = option.split("=", 2);
                if ("IgnoreEmpty".equalsIgnoreCase(optionParts[0])) {
                    format = format.builder().setIgnoreEmptyLines(Boolean.parseBoolean(optionParts[1])).get();
                } else if ("IgnoreSpaces".equalsIgnoreCase(optionParts[0])) {
                    format = format.builder().setIgnoreSurroundingSpaces(Boolean.parseBoolean(optionParts[1])).get();
                } else if ("CommentStart".equalsIgnoreCase(optionParts[0])) {
                    format = format.builder().setCommentMarker(optionParts[1].charAt(0)).get();
                } else if ("CheckComments".equalsIgnoreCase(optionParts[0])) {
                    checkComments = true;
                } else {
                    fail(testFile.getName() + " unexpected option: " + option);
                }
            }
            line = readTestData(testDataReader); // get string version of format
            assertEquals(line, format.toString(), testFile.getName() + " Expected format ");

            // Now parse the file and compare against the expected results
            // We use a buffered reader internally so no need to create one here.
            try (final CSVParser parser = CSVParser.parse(new File(BASE_DIR, split[0]), Charset.defaultCharset(), format)) {
                for (final CSVRecord myRecord : parser) {
                    String parsed = Arrays.toString(myRecord.values());
                    final String comment = myRecord.getComment();
                    if (checkComments && comment != null) {
                        parsed += "#" + comment.replace("\n", "\\n");
                    }
                    final int count = myRecord.size();
                    assertEquals(readTestData(testDataReader), count + ":" + parsed, testFile.getName());
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("generateData")
    void testCSVUrl(final File testFile) throws Exception {
        try (FileReader fr = new FileReader(testFile); BufferedReader testData = new BufferedReader(fr)) {
            String line = readTestData(testData);
            assertNotNull("file must contain config line", line);
            final String[] split = line.split(" ");
            assertTrue(split.length >= 1, testFile.getName() + " require 1 param");
            // first line starts with csv data file name
            CSVFormat format = CSVFormat.newFormat(',').builder().setQuote('"').get();
            boolean checkComments = false;
            for (int i = 1; i < split.length; i++) {
                final String option = split[i];
                final String[] optionParts = option.split("=", 2);
                if ("IgnoreEmpty".equalsIgnoreCase(optionParts[0])) {
                    format = format.builder().setIgnoreEmptyLines(Boolean.parseBoolean(optionParts[1])).get();
                } else if ("IgnoreSpaces".equalsIgnoreCase(optionParts[0])) {
                    format = format.builder().setIgnoreSurroundingSpaces(Boolean.parseBoolean(optionParts[1])).get();
                } else if ("CommentStart".equalsIgnoreCase(optionParts[0])) {
                    format = format.builder().setCommentMarker(optionParts[1].charAt(0)).get();
                } else if ("CheckComments".equalsIgnoreCase(optionParts[0])) {
                    checkComments = true;
                } else {
                    fail(testFile.getName() + " unexpected option: " + option);
                }
            }
            line = readTestData(testData); // get string version of format
            assertEquals(line, format.toString(), testFile.getName() + " Expected format ");

            // Now parse the file and compare against the expected results
            final URL resource = ClassLoader.getSystemResource("org/apache/commons/csv/CSVFileParser/" + split[0]);
            try (final CSVParser parser = CSVParser.parse(resource, StandardCharsets.UTF_8, format)) {
                for (final CSVRecord myRecord : parser) {
                    String parsed = Arrays.toString(myRecord.values());
                    final String comment = myRecord.getComment();
                    if (checkComments && comment != null) {
                        parsed += "#" + comment.replace("\n", "\\n");
                    }
                    final int count = myRecord.size();
                    assertEquals(readTestData(testData), count + ":" + parsed, testFile.getName());
                }
            }
        }
    }
}
