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

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jboss.galleon.spec.FeaturePackSpec;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.util.ZipUtils;
import org.jboss.galleon.xml.FeaturePackXmlWriter;
import org.junit.Assert;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author jdenise
 */
public class TestUtils {

    static void buildFP(Path root, String fpProducer, String fpVersion, List<Artifact> artifacts) throws Exception {

        Path outputFile = root.resolve("org").resolve("foo").resolve("bar").resolve(fpProducer).resolve(fpVersion);
        Files.createDirectories(outputFile);
        outputFile = outputFile.resolve(fpProducer + "-" + fpVersion + ".zip");
        Path dir = Files.createTempDirectory("fpProducer");
        Path patchSpecPath = dir.resolve("feature-pack.xml");
        FeaturePackLocation.FPID producer = FeaturePackLocation.fromString(fpProducer + "@maven(org.jboss.universe.community-universe):current#" + fpVersion).getFPID();
        FeaturePackSpec sepc = FeaturePackSpec.builder(producer).build();
        FeaturePackXmlWriter.getInstance().write(sepc, patchSpecPath);

        Path wildflyDir = dir.resolve("resources").resolve("wildfly");
        Files.createDirectories(wildflyDir);
        Path propsFile = wildflyDir.resolve("artifact-versions.properties");
        Map<String, String> artifactVersions = new HashMap<>();
        for (Artifact artifact : artifacts) {
            if (artifact.getEntry() != null) {
                artifactVersions.put(artifact.getEntry()[0], artifact.getEntry()[1]);
            }
        }
        Main.storeArtifactVersions(artifactVersions, propsFile);
        ZipUtils.zip(dir, outputFile);
        IoUtils.recursiveDelete(dir);
    }

    static PatchedArtifact createPatchedArtifact(Path rootDir, Path patchedRootDir, String grpId, String artId, String version, String classifier, String extension) throws Exception {
        Path dir = rootDir.resolve(grpId.replaceAll("\\.", "/")).resolve(artId).resolve(version);
        Files.createDirectories(dir);
        StringBuilder builder = new StringBuilder();
        builder.append(artId + "-" + version);
        if (classifier != null) {
            builder.append("-" + classifier);
        }
        Path file = dir.resolve(builder.toString() + "." + extension);
        Files.createFile(file);
        Path pomFile = dir.resolve(builder.toString() + ".pom");
        Files.createFile(pomFile);
        Path sha1 = dir.resolve(builder.toString() + "." + extension + ".sha1");
        Files.createFile(sha1);
        Path sha1Pom = dir.resolve(builder.toString() + ".pom.sha1");
        Files.createFile(sha1Pom);

        String patchVersion = version + ".patch";
        Path dirPatched = patchedRootDir.resolve(grpId.replaceAll("\\.", "/")).resolve(artId).resolve(patchVersion);
        Files.createDirectories(dirPatched);
        StringBuilder builderPatched = new StringBuilder();
        builderPatched.append(artId + "-" + patchVersion);
        if (classifier != null) {
            builderPatched.append("-" + classifier);
        }
        builderPatched.append("." + extension);
        Path patchedFile = dirPatched.resolve(builderPatched.toString());
        Files.createFile(patchedFile);
        return new PatchedArtifact(rootDir.relativize(file), patchedRootDir.relativize(patchedFile));
    }

    static PatchedArtifact createPatchedArtifact(Path patchedRootDir, String grpId, String artId, String version, String classifier, String extension) throws Exception {
        String patchVersion = version + ".patch";
        Path dirPatched = patchedRootDir.resolve(grpId.replaceAll("\\.", "/")).resolve(artId).resolve(patchVersion);
        Files.createDirectories(dirPatched);
        StringBuilder builderPatched = new StringBuilder();
        builderPatched.append(artId + "-" + patchVersion);
        if (classifier != null) {
            builderPatched.append("-" + classifier);
        }
        builderPatched.append("." + extension);
        Path patchedFile = dirPatched.resolve(builderPatched.toString());
        Files.createFile(patchedFile);
        return new PatchedArtifact(null, patchedRootDir.relativize(patchedFile));
    }

    static Artifact createArtifact(Path rootDir, String grpId, String artId, String version, String classifier, String extension) throws Exception {
        Path dir = rootDir.resolve(grpId.replaceAll("\\.", "/")).resolve(artId).resolve(version);
        Files.createDirectories(dir);
        StringBuilder builder = new StringBuilder();
        builder.append(artId + "-" + version);
        if (classifier != null) {
            builder.append("-" + classifier);
        }
        builder.append("." + extension);
        Path file = dir.resolve(builder.toString());
        Files.createFile(file);
        return new Artifact(rootDir.relativize(file));
    }

    static void checkPatches(Path patches, Set<String> ids) throws Exception {
        Assert.assertTrue(Files.exists(patches));
        FileInputStream fileInputStream = new FileInputStream(patches.toFile());
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

        Document document = documentBuilder.parse(fileInputStream);
        Element root = document.getDocumentElement();
        Assert.assertEquals("patches", root.getNodeName());
        NodeList lst = root.getChildNodes();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < lst.getLength(); i++) {
            Node n = (Node) lst.item(i);
            if (n instanceof Element) {
                Element elem = (Element) n;
                Assert.assertEquals("patch", n.getNodeName());
                Assert.assertTrue(ids.contains(elem.getAttribute("id")));
                seen.add(elem.getAttribute("id"));
            }
        }
        Assert.assertEquals(ids, seen);
    }
}
