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
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author jdenise
 */
public class ArtifactVersionsTestCase {

    @Test
    public void test() throws Exception {
        {
            Path p = Paths.get("org/foo/bar/art1/1.0/art1-1.0.jar");
            String[] entry = ArtifactUtils.pathToArtifactVersion(p);
            Assert.assertEquals("org.foo.bar:art1=org.foo.bar:art1:1.0::jar", entry[0] + "=" + entry[1]);
        }

        {
            Path p = Paths.get("org/foo/bar/art1.foo.bar/1.0/art1-1.0.jar");
            String[] entry = ArtifactUtils.pathToArtifactVersion(p);
            Assert.assertEquals("org.foo.bar:art1.foo.bar=org.foo.bar:art1.foo.bar:1.0::jar", entry[0] + "=" + entry[1]);
        }

        {
            Path p = Paths.get("org/foo/bar/art1/1.0/art1-1.0-class.jar");
            String[] entry = ArtifactUtils.pathToArtifactVersion(p);
            Assert.assertEquals("org.foo.bar:art1::class=org.foo.bar:art1:1.0:class:jar", entry[0] + "=" + entry[1]);
        }

        {
            Path p = Paths.get("org/foo/bar/art1.foo.bar/1.0/art1-1.0-class.jar");
            String[] entry = ArtifactUtils.pathToArtifactVersion(p);
            Assert.assertEquals("org.foo.bar:art1.foo.bar::class=org.foo.bar:art1.foo.bar:1.0:class:jar", entry[0] + "=" + entry[1]);
        }

        {
            Path p = Paths.get("org/foo/bar/art1/1.0/art1-1.0.foo");
            String[] entry = ArtifactUtils.pathToArtifactVersion(p);
            Assert.assertEquals("org.foo.bar:art1=org.foo.bar:art1:1.0::foo", entry[0] + "=" + entry[1]);
        }
    }
}
