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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author jdenise
 */
public class NoPatchingTestCase extends AbstractMainTest {

    final String producer1 = "fp-prod1";
    final String producer2 = "fp-prod2";
    final String producer3 = "fp-prod3";
    final String fpVersion = "1.0-redhat-00001";
    final String expectedFpversion = "1.0" + Patcher.PATCH_MARKER + "-redhat-00001";
    final List<Artifact> lst = new ArrayList<>();

    @Test
    public void test() throws Exception {
        boolean failed = false;
        try {
            runTest();
            failed = true;
        } catch (Exception ex) {
            // OK Expected
            Assert.assertTrue(ex.getMessage().contains("No artifacts found in the maven repo patch"));
        }
        if (failed) {
            throw new Exception("test should have failed");
        }
    }

    @Override
    protected List<Artifact> setup(Path root, Path patchedRoot) throws Exception {
        // Add artifacts that are not patched
        lst.add(TestUtils.createArtifact(root, "org.foo.bar.no.patch", "art1", "1.0", null, "jar"));
        lst.add(TestUtils.createArtifact(root, "org.foo.bar.no.patch", "art2", "2.0", "lib", "so"));
        lst.add(TestUtils.createArtifact(root, "org.foo.bar.no.patch", "art3", "3.0", null, "jar"));

        TestUtils.buildFP(root, producer1, fpVersion, lst);
        // To advertise the GA of feature-packs
        System.setProperty(Patcher.FP_PATHS, "org/foo/bar/" + producer1);
        return lst;
    }

    @Override
    protected void done(Path outputRepo) throws Exception {

    }
}
