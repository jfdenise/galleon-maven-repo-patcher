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
import java.nio.file.Paths;
import java.util.List;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.util.ZipUtils;
import org.junit.Assert;

/**
 *
 * @author jdenise
 */
abstract class AbstractMainTest {

    protected void runTest() throws Exception {
        Path wkDir = Files.createDirectory(Paths.get("test-workDir")); //Files.createTempDirectory("test-workdir");
        Path root = wkDir.resolve("originalRepo").resolve("builder-image").resolve("maven-repository");
        Files.createDirectories(root);
        Path patchedRoot = wkDir.resolve("patchedRepo").resolve("builder-image").resolve("maven-repository");
        Files.createDirectories(patchedRoot);
        Path zippedRepo = wkDir.resolve("orig-repo.zip");
        Path zippedPatchedRepo = wkDir.resolve("patch-repo.zip");
        Path outputDirectory = wkDir.resolve("outputDirectory");
        Files.createDirectory(outputDirectory);
        try {
            setup(root, patchedRoot);
            ZipUtils.zip(root.getParent().getParent(), zippedRepo);
            ZipUtils.zip(patchedRoot.getParent().getParent(), zippedPatchedRepo);
            Path output = Files.createTempFile("maven-repo-test", ".zip");
            output.toFile().deleteOnExit();
            String[] args = {zippedRepo.toAbsolutePath().toString(), zippedPatchedRepo.toAbsolutePath().toString(), output.toAbsolutePath().toString()};
            Main.main(args);
            Assert.assertTrue(Files.exists(output));
            ZipUtils.unzip(output, outputDirectory);
            done(outputDirectory.resolve("builder-image"));

        } finally {
            IoUtils.recursiveDelete(wkDir);
            IoUtils.recursiveDelete(Paths.get(Patcher.WORK_DIR));
            System.clearProperty(Patcher.FP_PATHS);
        }
    }

    protected abstract List<Artifact> setup(Path root, Path patchedRoot) throws Exception;

    protected abstract void done(Path outputRepo) throws Exception;
}
