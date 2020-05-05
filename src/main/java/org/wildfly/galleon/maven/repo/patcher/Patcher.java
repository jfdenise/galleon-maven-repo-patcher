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

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.jboss.galleon.universe.FeaturePackLocation.FPID;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.util.ZipUtils;

/**
 * Patch a repo and generate galleon patches for updated artifacts.
 *
 * @author jdenise
 */
public final class Patcher {

    private static final Set<String> FP = new HashSet<>();

    static final String WORK_DIR = "tool-work-dir";
    static final String FP_PATHS = "fp-paths";
    static final String PATCH_MARKER = "-patch";

    static {
        FP.add("org/wildfly/core/wildfly-core-galleon-pack");
        FP.add("org/jboss/eap/wildfly-servlet-galleon-pack");
        FP.add("org/jboss/eap/wildfly-ee-galleon-pack");
        FP.add("org/jboss/eap/wildfly-galleon-pack");
    }

    private final Path outputFile;
    private final Path repoWorkDir;
    private final Path repoPatchWorkDir;
    private final Path repoParentDir;
    private final Path workDir;

    private final Path mavenRepoRoot;
    private final Path patchedMavenRepoRoot;

    private final Log log = new Log();

    private final Map<String, String> newArtifactsMap;

    Patcher(Path originalMavenRepo, Path repoPatch, Path outputFile) throws Exception {
        this.outputFile = outputFile;
        Files.deleteIfExists(outputFile);
        workDir = Paths.get(WORK_DIR);
        System.out.println("Tool work dir (you can delete once tool has been run): " + workDir);
        IoUtils.recursiveDelete(workDir);
        Files.createDirectory(workDir);

        repoWorkDir = workDir.resolve("maven-repo");
        repoPatchWorkDir = workDir.resolve("repo-patch");

        System.out.println("Unzipping maven repo to " + repoWorkDir);
        ZipUtils.unzip(originalMavenRepo, repoWorkDir);

        repoParentDir = Files.list(repoWorkDir).findFirst().get();

        System.out.println("Unzipping maven repo patch to " + repoPatchWorkDir);
        ZipUtils.unzip(repoPatch, repoPatchWorkDir);

        mavenRepoRoot = getMavenRepoRoot(repoWorkDir);
        patchedMavenRepoRoot = getMavenRepoRoot(repoPatchWorkDir);

        Set<Path> upgradedFiles = new HashSet<>();
        retrievePatchedFiles(patchedMavenRepoRoot, upgradedFiles);

        if (upgradedFiles.isEmpty()) {
            throw new Exception("No artifacts found in the maven repo patch. Check your maven repo patch.");
        }

        log.addedArtifacts(upgradedFiles);

        // Convert the new artifacts to key/value pairs as they exist in artifact.properties file.
        newArtifactsMap = ArtifactUtils.convertToArtifactVersion(upgradedFiles);
    }

    void patch() throws Exception {
        List<Path> createdPatches = new ArrayList<>();
        List<FPID> createdPatchesGAV = new ArrayList<>();

        Set<String> fps = FP;
        // Used by tests
        String fpPaths = System.getProperty(FP_PATHS, null);
        if (fpPaths != null) {
            String[] paths = fpPaths.split(",");
            fps = new HashSet<>();
            for (String p : paths) {
                fps.add(p.trim());
            }
        }
        // Iterate over all the known galleon feature-packs
        for (String p : fps) {
            Path fppath = mavenRepoRoot.resolve(p);
            if (!Files.exists(fppath)) {
                continue;
            }

            Path version = Files.list(fppath).findFirst().get();
            if (version == null) {
                throw new Exception("Error, no version directory in " + fppath);
            }

            Path fpFile = Files.list(version).filter((file) -> {
                return file.toString().endsWith(".zip");
            }).findFirst().get();

            if (fpFile == null) {
                throw new Exception("Error, no galleon pack artifact in " + version);
            }

            System.out.println("Scanning feature-pack " + fpFile.getFileName());

            // Unzip the feature-pack
            Path fpDir = workDir.resolve(fpFile.getFileName());
            ZipUtils.unzip(fpFile, fpDir);

            ScannedFeaturePack scannedFp = ScannedFeaturePack.scan(fpDir, newArtifactsMap);

            // We found some artifacts in this feature-pack, must create a patch
            if (!scannedFp.getToRemove().isEmpty()) {
                // Compute a patch GAV, will be put in the same GA as the galleon pack it is a patch for.
                String patchVersion = ArtifactUtils.createPatchVersion(version.getFileName().toString());
                FPID patchGav = ArtifactUtils.patchPathToGav(Paths.get(p).resolve(patchVersion));
                createdPatchesGAV.add(patchGav);

                // Create the patch
                Path patch = GalleonPatchUtils.createPatch(workDir, fpDir,
                        scannedFp.getVersionProps(), patchGav, Paths.get(p).getFileName().toString(), patchVersion);
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
                for (String key : scannedFp.getOldArtifacts().keySet()) {
                    log.addPatchedArtifact(scannedFp.getOldArtifacts().get(key), newArtifactsMap.get(key));
                    newArtifactsMap.remove(key);
                }

                // Remove the old artifacts.
                deleteArtifacts(scannedFp.getToRemove());
            }
        }
        if (createdPatches.isEmpty()) {
            throw new RuntimeException("No patches created, something wrong somewhere");
        }

        if (!newArtifactsMap.isEmpty()) {
            throw new Exception("Following artifacts present in maven repo patch have not been found in galleon featurepacks: " + newArtifactsMap);
        }

        // generate patch.txt file
        String content = GalleonPatchUtils.createPatchesFile(repoParentDir, createdPatchesGAV);
        // Copy new artifacts in repo
        IoUtils.copy(patchedMavenRepoRoot, mavenRepoRoot);
        //Zip the repo
        System.out.println("Zipping " + repoParentDir + " to " + outputFile);
        ZipUtils.zip(repoWorkDir, outputFile);

        // Finally advertise what we have done
        log.print(content);
    }

    private void deleteArtifacts(Set<Path> toRemove) throws Exception {
        // Remove the old artifacts.
        Map<Path, Set<Path>> versionDirs = new HashMap<>();
        for (Path oldPath : toRemove) {
            Path pr = mavenRepoRoot.resolve(oldPath);
            if (!Files.exists(pr)) {
                throw new RuntimeException(pr + " doesn't exist! Can't remove it");
            }
            Path parentDir = pr.getParent();
            Set<Path> paths = versionDirs.get(parentDir);
            if (paths == null) {
                paths = new HashSet<>();
                versionDirs.put(parentDir, paths);
            }
            paths.add(pr);
            final Set<Path> finalPaths = paths;
            // We want to delete all files that starts by the same name
            // pom file being delete only if no more artifact in version dir after removal.
            String name = pr.getFileName().toString();
            Files.list(parentDir).filter((path) -> {
                String fileName = path.getFileName().toString();
                return fileName.startsWith(name);
            }).forEach((path) -> {
                finalPaths.add(path);
            });
        }
        //Check the parent directory, if no more artifact delete it
        // otherwise delete the subset of paths.
        for (Entry<Path, Set<Path>> entry : versionDirs.entrySet()) {
            Path versionDir = entry.getKey();
            Set<Path> ignore = entry.getValue();
            long num = Files.list(versionDir).filter((path) -> {
                return ArtifactUtils.isArtifact(path) && !ignore.contains(path);
            }).count();
            if (num == 0) {
                IoUtils.recursiveDelete(versionDir);
                log.addDeletedDir(mavenRepoRoot.relativize(versionDir));
            } else {
                for (Path path : entry.getValue()) {
                    Files.delete(path);
                    log.addDeletedArtifact(mavenRepoRoot.relativize(path));
                }
            }
        }
    }

    private static Path getMavenRepoRoot(Path p) throws Exception {
        List<Path> path = new ArrayList<>();
        Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.getFileName().toString().equals("maven-repository")) {
                    path.add(dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return path.get(0);
    }

    private static void retrievePatchedFiles(Path p, Set<Path> set) throws Exception {
        Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path t, BasicFileAttributes bfa) throws IOException {
                if (ArtifactUtils.isArtifact(t)) {
                    set.add(p.relativize(t));
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
