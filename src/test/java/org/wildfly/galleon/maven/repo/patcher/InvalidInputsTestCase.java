/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.galleon.maven.repo.patcher;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 *
 * @author jdenise
 */
public class InvalidInputsTestCase {

    @Test
    public void test() throws Exception {
        {
            String[] args = {};
            boolean failed = false;
            try {
                Main.main(args);
                failed = true;
            } catch (Exception ex) {
                // XXX OK, expected
            }
            if (failed) {
                throw new Exception("Test case should have failed");
            }
        }

        {
            String[] args = {"foo", "bar", "doom"};
            boolean failed = false;
            try {
                Main.main(args);
                failed = true;
            } catch (Exception ex) {
                // XXX OK, expected
            }
            if (failed) {
                throw new Exception("Test case should have failed");
            }
        }

        {
            Path p1 = Files.createTempFile("foo", ".zip");
            String[] args = {p1.toString(), "bar", "doom"};
            boolean failed = false;
            try {
                Main.main(args);
                failed = true;
            } catch (Exception ex) {
                // XXX OK, expected
            }
            if (failed) {
                throw new Exception("Test case should have failed");
            }
        }
    }
}
