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

/**
 *
 * @author jdenise
 */
public class Artifact {

    private final Path path;
    private final String[] entry;

    Artifact(Path path) {
        this.path = path;
        // Null for patched artifact that are nit in the original maven repo.
        this.entry = path == null ? null : ArtifactUtils.pathToArtifactVersion(path);
    }

    /**
     * @return the path
     */
    public Path getPath() {
        return path;
    }

    /**
     * @return the entry
     */
    public String[] getEntry() {
        return entry;
    }

}
