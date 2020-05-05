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

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Error, 3 arguments expected: zipped repo, zipped repo patch, generated zipped repo file name");
        }
        Path originalMavenRepo = Paths.get(args[0]);
        Path repoPatch = Paths.get(args[1]);

        if (!Files.exists(originalMavenRepo)) {
            throw new Exception("Original repo doesn't exist");
        }

        if (!Files.exists(repoPatch)) {
            throw new Exception("Repo patch doesn't exist");
        }

        Path outputFile = Paths.get(args[2]);

        new Patcher(originalMavenRepo, repoPatch, outputFile).patch();
    }
}
