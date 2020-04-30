/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
            addedMessages.append(" - " + p + "/*").append("\n");
        }
    }

    void addPatch(FPID patchGav, Path fpFile) {
        patchMessages.append(" * patch " + patchGav + " for " + fpFile.getFileName()).append("\n");
    }

    void addPatchedArtifact(String old, String newArtifact) {
        patchMessages.append("   - " + old + " => " + newArtifact).append("\n");
    }

    void addDeletedArtifact(Path oldPath) {
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
