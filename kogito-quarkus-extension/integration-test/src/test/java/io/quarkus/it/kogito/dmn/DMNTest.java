/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.quarkus.it.kogito.dmn;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;

@QuarkusTest
public class DMNTest {

    static { RestAssured.enableLoggingOfRequestAndResponseIfValidationFails(); }

    @Test
    public void testAdult() {

        String payload = "{\n" +
                "  \"p\": {\n" +
                "    \"addresses\": [\n" +
                "      {\n" +
                "        \"streetName\": \"Street Name\",\n" +
                "        \"streetNumber\": 1\n" +
                "      },\n" +
                "      {\n" +
                "        \"streetName\": \"Another street name\",\n" +
                "        \"streetNumber\": 2\n" +
                "      }\n" +
                "    ],\n" +
                "    \"name\": \"Luca\"\n" +
                "  }\n" +
                "}";

        RestAssured.given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(payload)
                .post("/dmnModel")
                .then()
                .statusCode(200)
                .body("d.Hello", is("Hello Luca"));
    }

    @Test
    public void allTypes() {

        String payload = "{\n" +
                "  \"InputBoolean\": true,\n" +
                "  \"InputDTDuration\": \"P1D\",\n" +
                "  \"InputDate\": \"2020-04-17\",\n" +
                "  \"InputDateAndTime\": \"2020-04-17T10:03:51.615Z\",\n" +
                "  \"InputNumber\": 0,\n" +
                "  \"InputString\": \"string\",\n" +
                "  \"InputTime\": \"13:45\",\n" +
                "  \"InputYMDuration\": \"P1M\"\n" +
                "}";

        RestAssured.given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(payload)
                .post("/OneOfEachType")
                .then()
                .statusCode(200);
    }
}
