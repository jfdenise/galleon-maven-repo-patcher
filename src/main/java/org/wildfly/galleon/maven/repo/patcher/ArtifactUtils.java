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
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.jboss.galleon.universe.FeaturePackLocation;
import static org.wildfly.galleon.maven.repo.patcher.Patcher.PATCH_MARKER;

/**
 *
 * @author jdenise
 */
public final class ArtifactUtils {

    private static void readProperties(Path propsFile, Map<String, String> propsMap) throws Exception {
        try (BufferedReader reader = Files.newBufferedReader(propsFile)) {
            String line = reader.readLine();
            while (line != null) {
                line = line.trim();
                if (line.charAt(0) != '#' && !line.isEmpty()) {
                    final int i = line.indexOf('=');
                    if (i < 0) {
                        throw new Exception("Failed to parse property " + line + " from " + propsFile);
                    }
                    propsMap.put(line.substring(0, i), line.substring(i + 1));
                }
                line = reader.readLine();
            }
        }
    }

    static Map<String, String> readProperties(final Path propsFile) throws Exception {
        final Map<String, String> propsMap = new HashMap<>();
        readProperties(propsFile, propsMap);
        return propsMap;
    }

    static void storeArtifactVersions(Map<String, String> map, Path target) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardOpenOption.CREATE)) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                writer.write(entry.getKey());
                writer.write('=');
                writer.write(entry.getValue());
                writer.newLine();
            }
        }
    }

    static Path convertToPath(String str) {

        final String[] parts = str.split(":");
        final String groupId = parts[0];
        final String artifactId = parts[1];
        String version = parts[2];
        String classifier = parts[3];
        String extension = parts[4];

        Path path = Paths.get(groupId.replaceAll("\\.", "/")).resolve(artifactId).resolve(version);
        StringBuilder name = new StringBuilder();
        name.append(artifactId).append("-").append(version);
        if (classifier != null && !classifier.isEmpty()) {
            name.append("-").append(classifier);
        }
        name.append(".").append(extension);
        path = path.resolve(name.toString());
        return path;
    }

    static String[] pathToArtifactVersion(Path path) {
        Path versionPath = path.getParent();
        Path artifactIdPath = versionPath.getParent();
        Path groupIdPath = artifactIdPath.getParent();
        String version = path.getParent().getFileName().toString();
        String artifactId = artifactIdPath.getFileName().toString();
        String groupId = groupIdPath.toString().replaceAll("/", ".");
        String artifact = path.getFileName().toString();
        int versionIndex = artifact.indexOf(version);
        int extIndex = artifact.lastIndexOf(".");
        String extension = artifact.substring(extIndex + 1);
        String classifier = artifact.substring(versionIndex + version.length(), extIndex);
        StringBuilder key = new StringBuilder();
        key.append(groupId).append(":").append(artifactId);
        StringBuilder value = new StringBuilder();
        value.append(groupId).append(":").append(artifactId).append(":").append(version).append(":");
        if (classifier != null && !classifier.isEmpty()) {
            // Remove leading "-";
            classifier = classifier.substring(1);
            key.append("::").append(classifier);
            value.append(classifier);
        }
        value.append(":").append(extension);
        String[] ret = {key.toString(), value.toString()};
        return ret;
    }

    static Map<String, String> convertToArtifactVersion(Set<Path> files) throws Exception {
        Map<String, String> map = new HashMap<>();
        for (Path path : files) {
            String[] entry = ArtifactUtils.pathToArtifactVersion(path);
            map.put(entry[0], entry[1]);
        }
        return map;
    }

    static boolean isArtifact(Path p) {
        return p.toString().endsWith(".jar") || p.toString().endsWith(".so");
    }

    static FeaturePackLocation.FPID patchPathToGav(Path path) {
        Path versionPath = path;
        Path artifactIdPath = versionPath.getParent();
        Path groupIdPath = artifactIdPath.getParent();
        String version = versionPath.getFileName().toString();
        String artifactId = artifactIdPath.getFileName().toString();
        String groupId = groupIdPath.toString().replaceAll("/", ".");
        String gav = groupId + ":" + artifactId + ":" + version;
        return FeaturePackLocation.fromString(gav).getFPID();
    }

    static String createPatchVersion(String version) {
        int idx = version.indexOf("-redhat-");
        String prefix = version.substring(0, idx);
        return prefix + PATCH_MARKER + version.substring(idx);
    }

}
