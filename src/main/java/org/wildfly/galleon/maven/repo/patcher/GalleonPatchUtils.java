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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jboss.galleon.spec.FeaturePackSpec;
import org.jboss.galleon.universe.FeaturePackLocation.FPID;
import org.jboss.galleon.util.ZipUtils;
import org.jboss.galleon.xml.FeaturePackXmlParser;
import org.jboss.galleon.xml.FeaturePackXmlWriter;

/**
 *
 * @author jdenise
 */
final class GalleonPatchUtils {

    static String createPatchesFile(Path repoDir, List<FPID> createdPatchesGAV) throws UnsupportedEncodingException, IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("<patches>").append("\n");
        for (FPID gav : createdPatchesGAV) {
            builder.append("<patch id=\"" + gav + "\"/>").append("\n");
        }
        builder.append("</patches>");
        Path patchesFile = repoDir.resolve("patches.xml");
        Files.write(patchesFile, builder.toString().getBytes("UTF-8"));
        return builder.toString();
    }

    static Path createPatch(Path tmpDir, Path fpDir, Map<String, String> versionProps, FPID patchGav, String artifactId, String patchVersion) throws Exception {
        Path origSpec = fpDir.resolve("feature-pack.xml");
        Path patchDir = tmpDir.resolve("patch-" + fpDir.getFileName());
        Files.createDirectories(patchDir);
        Path patchSpecPath = patchDir.resolve("feature-pack.xml");
        FPID forProducer = getProducer(origSpec);
        FeaturePackSpec sepc = FeaturePackSpec.builder(patchGav).setPatchFor(forProducer).build();
        FeaturePackXmlWriter.getInstance().write(sepc, patchSpecPath);

        Path wildflyDir = patchDir.resolve("resources").resolve("wildfly");
        Files.createDirectories(wildflyDir);
        Path propsFile = wildflyDir.resolve("artifact-versions.properties");
        ArtifactUtils.storeArtifactVersions(versionProps, propsFile);

        Path patchFile = tmpDir.resolve(artifactId + "-" + patchVersion + ".zip");
        ZipUtils.zip(patchDir, patchFile);
        return patchFile;
    }

    private static FPID getProducer(Path path) throws Exception {
        FileReader fileReader = new FileReader(path.toFile());
        FeaturePackSpec spec = FeaturePackXmlParser.getInstance().parse(new BufferedReader(fileReader));
        return spec.getFPID();
    }
}
