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
import java.util.Set;
import org.jboss.galleon.universe.FeaturePackLocation.FPID;

/**
 *
 * @author jdenise
 */
public class Log {

    private final StringBuilder addedMessages = new StringBuilder();
    private final StringBuilder deletedMessages = new StringBuilder();
    private final StringBuilder patchMessages = new StringBuilder();

    public Log() {
        addedMessages.append("Added artifacts:").append("\n");

        deletedMessages.append("Deleted artifacts:").append("\n");
        patchMessages.append("Created patches:").append("\n");
    }

    void addedArtifacts(Set<Path> patchedFiles) {
        for (Path p : patchedFiles) {
            addedMessages.append(" - " + p.getParent() + "/*").append("\n");
        }
    }

    void addPatch(FPID patchGav, Path fpFile) {
        patchMessages.append(" * patch " + patchGav + " for " + fpFile.getFileName()).append("\n");
    }

    void addPatchedArtifact(String old, String newArtifact) {
        patchMessages.append("   - " + old + " => " + newArtifact).append("\n");
    }

    void addDeletedArtifact(Path oldPath) {
        deletedMessages.append(" - " + oldPath).append("\n");
    }

    void addDeletedDir(Path oldPath) {
        deletedMessages.append(" - " + oldPath + "/*").append("\n");
    }

    void print(String patchesContent) {
        System.out.println("\n");
        System.out.println(patchMessages.toString());
        System.out.println(addedMessages.toString());
        System.out.println(deletedMessages.toString());
        System.out.println("Content of patches.xml:");
        System.out.println(patchesContent);
    }
}
