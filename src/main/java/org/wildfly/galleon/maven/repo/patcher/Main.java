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
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.jboss.galleon.spec.FeaturePackSpec;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.FeaturePackLocation.FPID;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.util.ZipUtils;
import org.jboss.galleon.xml.FeaturePackXmlParser;
import org.jboss.galleon.xml.FeaturePackXmlWriter;

/**
 * Patch a repo and generate galleon patches for updated artifacts.
 *
 * @author jdenise
 */
public final class Main {

    private static final Set<String> FP = new HashSet<>();

    static {
        FP.add("org/wildfly/core/wildfly-core-galleon-pack");
        FP.add("org/jboss/eap/wildfly-servlet-galleon-pack");
        FP.add("org/jboss/eap/wildfly-ee-galleon-pack");
        FP.add("org/jboss/eap/wildfly-galleon-pack");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Error, 3 arguments expected: zipped repo, zipped repo patch, generated zipped repo file name");
        }
        String originalMavenRepo = args[0];
        String repoPatch = args[1];
        Path outputFile = Paths.get(args[2]);
        List<Path> createdPatches = new ArrayList<>();
        List<FPID> createdPatchesGAV = new ArrayList<>();

        Files.deleteIfExists(outputFile);
        Path workDir = Paths.get("tool-work-dir");
        IoUtils.recursiveDelete(workDir);
        Path dir = Files.createDirectory(workDir);

        Path repoParentDir = dir.resolve("maven-repo");
        Path repoPatchDir = dir.resolve("repo-patch");

        System.out.println("Unzipping maven repo to " + repoParentDir);
        ZipUtils.unzip(Paths.get(originalMavenRepo), repoParentDir);
        Path repoDir = Files.list(repoParentDir).findFirst().get();

        System.out.println("Unzipping maven repo patch to " + repoPatchDir);
        ZipUtils.unzip(Paths.get(repoPatch), repoPatchDir);

        // Convert the new artifacts to key/value pairs as they exist in artifact.properties file.
        Map<String, String> newArtifactsMap = convertToArtifactVersion(repoPatchDir);
        Log log = new Log();
        Path mavenRepoRoot = getMavenRepoRoot(repoDir);
        Path patchedRepoRoot = getMavenRepoRoot(repoPatchDir);
        Set<Path> patchedFiles = new HashSet<>();
        retrievePatchedFiles(patchedRepoRoot, patchedFiles);
        log.addedArtifacts(patchedFiles);

        // Iterate over all the known galleon feature-packs
        for (String p : FP) {
            Path fppath = mavenRepoRoot.resolve(p);
            if (!Files.exists(fppath)) {
                continue;
            }

            Path version = Files.list(fppath).findFirst().get();
            if (version == null) {
                throw new Exception("Error, no version directoy in " + fppath);
            }

            Path fpFile = Files.list(version).filter((file) -> {
                return file.toString().endsWith(".zip");
            }).findFirst().get();
            if (fpFile == null) {
                throw new Exception("Error, no galleon pack artifact in " + version);
            }

            System.out.println("Scanning feature-pack " + fpFile.getFileName());

            // Unzip the feature-pack
            Path fpDir = dir.resolve(fpFile.getFileName());
            ZipUtils.unzip(fpFile, fpDir);

            Set<Path> toRemove = new HashSet<>();
            Path artifactProps = fpDir.resolve("resources").resolve("wildfly").resolve("artifact-versions.properties");
            final Map<String, String> versionProps = readProperties(artifactProps);
            Map<String, String> oldArtifacts = new HashMap<>();
            for (Entry<String, String> entry : newArtifactsMap.entrySet()) {
                String origVersion = versionProps.get(entry.getKey());
                if (origVersion != null) {
                    // replace with updated artifact
                    versionProps.put(entry.getKey(), entry.getValue());
                    toRemove.add(convertToPath(origVersion));
                    oldArtifacts.put(entry.getKey(), origVersion);
                }
            }

            // We found some artifacts in this feature-pack, must create a patch
            if (!toRemove.isEmpty()) {
                // Compute a patch GAV, will be put in the same GA as the galleon pack it is a patch for.
                String patchVersion = createPatchVersion(version.getFileName().toString());
                FPID patchGav = patchPathToGav(Paths.get(p).resolve(patchVersion));
                createdPatchesGAV.add(patchGav);

                // Create the patch
                Path patch = createPatch(dir, fpDir, versionProps, patchGav, Paths.get(p).getFileName().toString(), patchVersion);
                createdPatches.add(patch);

                //Install the patch in the maven repo.
                //Installed in the same artifactId as the patched galleon feature-pack
                Path patchDir = fppath.resolve(patchVersion);
                Files.createDirectories(patchDir);
                Path patchFile = patchDir.resolve(patch.getFileName());
                log.addPatch(patchGav, fpFile);
                Files.copy(patch, patchFile);

                // Remove all the artifacts that we have handled in this patch.
                // We check at the end that the newArtifactsMap is empty, all new artifacts
                // have been found.
                for (String key : oldArtifacts.keySet()) {
                    log.addPatchedArtifact(oldArtifacts.get(key), newArtifactsMap.get(key));
                    newArtifactsMap.remove(key);
                }

                // Remove the actual old artifacts. We remove all inside the version directory.
                for (Path oldPath : toRemove) {
                    Path pr = mavenRepoRoot.resolve(oldPath);
                    if (!Files.exists(pr)) {
                        throw new RuntimeException(pr + " doesn't exist! Can't remove it");
                    }
                    //Delete from the version dir
                    log.addDeletedArtifact(oldPath);
                    IoUtils.recursiveDelete(pr);
                }
            }
        }
        if (createdPatches.isEmpty()) {
            throw new RuntimeException("No patches created, something wrong somewhere");
        }

        if (!newArtifactsMap.isEmpty()) {
            throw new Exception("Following artifacts present in maven repo patch have not been found in galleon featurepacks: " + newArtifactsMap);
        }

        // generate patch.txt file
        String content = createPatchesFile(repoDir, createdPatchesGAV);
        // Copy new artifacts in repo
        IoUtils.copy(patchedRepoRoot, mavenRepoRoot);
        //Zip the repo
        System.out.println("Zipping " + repoDir + " to " + outputFile);
        ZipUtils.zip(repoParentDir, outputFile);

        // Finally advertise what we have done
        log.print(content);
    }

    private static String createPatchesFile(Path repoDir, List<FPID> createdPatchesGAV) throws UnsupportedEncodingException, IOException {
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

    private static Map<String, String> readProperties(final Path propsFile) throws Exception {
        final Map<String, String> propsMap = new HashMap<>();
        readProperties(propsFile, propsMap);
        return propsMap;
    }

    private static Path createPatch(Path tmpDir, Path fpDir, Map<String, String> versionProps, FPID patchGav, String artifactId, String patchVersion) throws Exception {
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
        storeArtifactVersions(versionProps, propsFile);

        Path patchFile = tmpDir.resolve(artifactId + "-" + patchVersion + ".zip");
        ZipUtils.zip(patchDir, patchFile);
        return patchFile;
    }

    private static void storeArtifactVersions(Map<String, String> map, Path target) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardOpenOption.CREATE)) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                writer.write(entry.getKey());
                writer.write('=');
                writer.write(entry.getValue());
                writer.newLine();
            }
        }
    }

    private static FPID getProducer(Path path) throws Exception {
        FileReader fileReader = new FileReader(path.toFile());
        FeaturePackSpec spec = FeaturePackXmlParser.getInstance().parse(new BufferedReader(fileReader));
        return spec.getFPID();
    }

    private static Path convertToPath(String str) {

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
        return path;
    }

    private static Map<String, String> convertToArtifactVersion(Path p) throws Exception {
        Map<String, String> map = new HashMap<>();
        Path root = getMavenRepoRoot(p);
        visitRepoPatch(root, map);
        return map;
    }

    private static Path getMavenRepoRoot(Path p) throws Exception {
        List<Path> path = new ArrayList<>();
        Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.endsWith("maven-repository")) {
                    path.add(dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return path.get(0);
    }

    //org.wildfly.core:wildfly-cli::client=org.wildfly.core:wildfly-cli:11.0.0.Final:client:jar
    //org.reactivestreams:reactive-streams=org.reactivestreams:reactive-streams:1.0.2::jar
    private static void visitRepoPatch(Path p, Map<String, String> map) throws Exception {
        Set<Path> files = new HashSet<>();
        Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path t, BasicFileAttributes bfa) throws IOException {
                if (t.toString().endsWith(".jar") || t.toString().endsWith(".so")) {
                    files.add(p.relativize(t));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        for (Path path : files) {
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
                key.append("::").append(classifier);
                value.append(classifier);
            }
            value.append(":").append(extension);
            map.put(key.toString(), value.toString());
        }
    }

    private static void retrievePatchedFiles(Path p, Set<Path> set) throws Exception {
        Set<Path> files = new HashSet<>();
        Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path t, BasicFileAttributes bfa) throws IOException {
                if (t.toString().endsWith(".jar") || t.toString().endsWith(".so")) {
                    set.add(p.relativize(t).getParent());
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static FPID patchPathToGav(Path path) {
        Path versionPath = path;
        Path artifactIdPath = versionPath.getParent();
        Path groupIdPath = artifactIdPath.getParent();
        String version = versionPath.getFileName().toString();
        String artifactId = artifactIdPath.getFileName().toString();
        String groupId = groupIdPath.toString().replaceAll("/", ".");
        String gav = groupId + ":" + artifactId + ":" + version;
        return FeaturePackLocation.fromString(gav).getFPID();
    }

    private static String createPatchVersion(String version) {
        int idx = version.indexOf("-redhat-");
        String prefix = version.substring(0, idx);
        return prefix + "-patch" + version.substring(idx);
    }
}
