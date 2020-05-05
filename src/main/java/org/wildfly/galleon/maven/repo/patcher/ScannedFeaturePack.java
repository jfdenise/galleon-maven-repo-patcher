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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 *
 * @author jdenise
 */
final class ScannedFeaturePack {

    private final Set<Path> toRemove;
    private final Map<String, String> versionProps;
    private final Map<String, String> oldArtifacts;

    private ScannedFeaturePack(Set<Path> toRemove, final Map<String, String> versionProps, Map<String, String> oldArtifacts) {
        this.toRemove = toRemove;
        this.versionProps = versionProps;
        this.oldArtifacts = oldArtifacts;
    }

    static ScannedFeaturePack scan(Path fpDir, Map<String, String> newArtifactsMap) throws Exception {
        Set<Path> toRemove = new HashSet<>();
        Path artifactProps = fpDir.resolve("resources").resolve("wildfly").resolve("artifact-versions.properties");
        final Map<String, String> versionProps = ArtifactUtils.readProperties(artifactProps);
        Map<String, String> oldArtifacts = new HashMap<>();
        for (Entry<String, String> entry : newArtifactsMap.entrySet()) {
            String origVersion = versionProps.get(entry.getKey());
            if (origVersion != null) {
                // replace with updated artifact
                versionProps.put(entry.getKey(), entry.getValue());
                toRemove.add(ArtifactUtils.convertToPath(origVersion));
                oldArtifacts.put(entry.getKey(), origVersion);
            }
        }
        return new ScannedFeaturePack(toRemove, versionProps, oldArtifacts);
    }

    /**
     * @return the toRemove
     */
    public Set<Path> getToRemove() {
        return toRemove;
    }

    /**
     * @return the versionProps
     */
    public Map<String, String> getVersionProps() {
        return versionProps;
    }

    /**
     * @return the oldArtifacts
     */
    public Map<String, String> getOldArtifacts() {
        return oldArtifacts;
    }
}
