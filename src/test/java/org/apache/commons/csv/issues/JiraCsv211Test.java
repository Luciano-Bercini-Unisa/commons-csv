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
package org.apache.commons.csv.issues;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.StringReader;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.junit.jupiter.api.Test;

 class JiraCsv211Test {

    @Test
     void testJiraCsv211Format() throws IOException {
        // @formatter:off
        final CSVFormat printFormat = CSVFormat.DEFAULT.builder()
            .setDelimiter('\t')
            .setHeader("ID", "Name", "Country", "Age")
            .get();
        // @formatter:on
        final String formatted = printFormat.format("1", "Jane Doe", "USA", "");
        assertEquals("ID\tName\tCountry\tAge\r\n1\tJane Doe\tUSA\t", formatted);

        final CSVFormat parseFormat = CSVFormat.DEFAULT.builder().setDelimiter('\t').setHeader().setSkipHeaderRecord(true).get();
        try (final CSVParser parser = parseFormat.parse(new StringReader(formatted))) {
            parser.forEach(myRecord -> {
                assertEquals("1", myRecord.get(0));
                assertEquals("Jane Doe", myRecord.get(1));
                assertEquals("USA", myRecord.get(2));
                assertEquals("", myRecord.get(3));
            });
        }
    }
}
