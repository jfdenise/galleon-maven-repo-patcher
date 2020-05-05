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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.galleon.util.ZipUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author jdenise
 */
public class MainTestCase extends AbstractMainTest {

    final String producer1 = "fp-prod1";
    final String producer2 = "fp-prod2";
    final String producer3 = "fp-prod3";
    final String fpVersion = "1.0-redhat-00001";
    final String expectedFpversion = "1.0" + Main.PATCH_MARKER + "-redhat-00001";
    final List<Artifact> lst = new ArrayList<>();
    final Set<Path> dirRemoved = new HashSet<>();

    @Test
    public void test() throws Exception {
        runTest();
    }

    @Override
    protected List<Artifact> setup(Path root, Path patchedRoot) throws Exception {
        List<Artifact> lst1 = new ArrayList<>();
        List<Artifact> lst2 = new ArrayList<>();
        List<Artifact> lst3 = new ArrayList<>();
        // Add artifacts that are not patched
        lst1.add(TestUtils.createArtifact(root, "org.foo.bar.no.patch", "art1", "1.0", null, "jar"));
        lst2.add(TestUtils.createArtifact(root, "org.foo.bar.no.patch", "art2", "2.0", "lib", "so"));
        lst2.add(TestUtils.createArtifact(root, "org.foo.bar.no.patch", "art3", "3.0", null, "jar"));

        // The classified is not patched although the non classified is patched
        lst1.add(TestUtils.createArtifact(root, "org.foo.bar", "art1", "1.0", "lib", "so"));

        // Patched artifacts
        lst1.add(TestUtils.createPatchedArtifact(root, patchedRoot, "org.foo.bar", "art1", "1.0", null, "jar"));

        PatchedArtifact p1 = TestUtils.createPatchedArtifact(root, patchedRoot, "org.foo.bar", "art2", "2.0", null, "jar");
        lst2.add(p1);
        lst2.add(TestUtils.createPatchedArtifact(root, patchedRoot, "org.foo.bar", "art2", "2.0", "lib", "so"));
        PatchedArtifact p2 = TestUtils.createPatchedArtifact(root, patchedRoot, "org.foo.bar", "art3", "3.0", "lib", "so");
        lst3.add(p2);

        // The version dir is removed for these ones:
        dirRemoved.add(p1.getPath().getParent());
        dirRemoved.add(p2.getPath().getParent());

        TestUtils.buildFP(root, producer1, fpVersion, lst1);
        TestUtils.buildFP(root, producer2, fpVersion, lst2);
        TestUtils.buildFP(root, producer3, fpVersion, lst3);
        // To advertise the GA of feature-packs
        System.setProperty(Main.FP_PATHS, "org/foo/bar/" + producer1 + ",org/foo/bar/" + producer2 + ",org/foo/bar/" + producer3);

        lst.addAll(lst1);
        lst.addAll(lst2);
        lst.addAll(lst3);
        return lst;
    }

    @Override
    protected void done(Path outputRepo) throws Exception {
        Path patches = outputRepo.resolve("patches.xml");
        Set<String> ids = new HashSet<>();
        ids.add("org.foo.bar:" + producer1 + ":" + expectedFpversion);
        ids.add("org.foo.bar:" + producer2 + ":" + expectedFpversion);
        ids.add("org.foo.bar:" + producer3 + ":" + expectedFpversion);
        TestUtils.checkPatches(patches, ids);

        Path mavenRepo = outputRepo.resolve("maven-repository");

        // Check added artifacts.
        // Check removed artifacts.
        // Untouched must be there
        for (Artifact a : lst) {
            if (a instanceof PatchedArtifact) {
                PatchedArtifact pa = (PatchedArtifact) a;
                Assert.assertFalse(Files.exists(mavenRepo.resolve(pa.getPath())));
                Assert.assertTrue(Files.exists(mavenRepo.resolve(pa.getPatched())));
            } else {
                Assert.assertTrue(Files.exists(mavenRepo.resolve(a.getPath())));
            }
        }

        //Check that version parent directory has been removed
        for (Path p : dirRemoved) {
            Assert.assertFalse(Files.exists(p));
        }

        //check content of artifacts versions.
        Map<String, String> artifactVersions = new HashMap<>();
        for (String id : ids) {
            Path fpPath = mavenRepo.resolve(Main.convertToPath(id + "::zip"));
            Assert.assertTrue(Files.exists(fpPath));
            Assert.assertTrue(fpPath.toString(), fpPath.toString().endsWith(".zip"));
            Path tmp = Files.createTempDirectory("fp");
            ZipUtils.unzip(fpPath, tmp);
            Path versions = tmp.resolve("resources").resolve("wildfly").resolve("artifact-versions.properties");
            Assert.assertTrue(Files.exists(versions));
            artifactVersions.putAll(Main.readProperties(versions));
        }
        Assert.assertEquals(lst.size(), artifactVersions.size());
        // We must have them all.
        for (Artifact a : lst) {
            if (a instanceof PatchedArtifact) {
                PatchedArtifact pa = (PatchedArtifact) a;
                String inFile = artifactVersions.get(pa.getPatchedEntry()[0]);
                Assert.assertEquals(pa.getPatchedEntry()[1], inFile);
            } else {
                String inFile = artifactVersions.get(a.getEntry()[0]);
                Assert.assertEquals(a.getEntry()[1], inFile);
            }
            artifactVersions.remove(a.getEntry()[0]);
        }
        Assert.assertTrue(artifactVersions.isEmpty());
    }
}
