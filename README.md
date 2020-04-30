# galleon-maven-repo-patcher

* Patch a zipped maven repository with a maven repo patch (a subset of artifacts that have been patched).
* Generates galleon patches for each galleon feature-pack that needs to be patched.
* Remove old artifacts.
* Copy new artifacts from maven-repo patch
* Generate a new zipped repository containing all, ready to be consumed by Openshift image build (or bootable jar maven repo).

Build

```mvn clean install```

Usage

```java -jar target/galleon-maven-repo-patcher-1.0.jar <original zipped repo>  <maven repo zipped patch> <generated zipped maven repo file>```

Output example:

```
java -jar ../target/galleon-maven-repo-patcher-1.0.jar ./jboss-eap-7.3.0.SP1-CR1-image-builder-maven-repository.zip  maven-repo-patch.zip output-repo.zip

Unzipping maven repo to tool-work-dir/maven-repo
Unzipping maven repo patch to tool-work-dir/repo-patch
Scanning feature-pack wildfly-core-galleon-pack-10.1.2.SP1-redhat-00001.zip
Scanning feature-pack wildfly-servlet-galleon-pack-7.3.0.SP1-redhat-00001.zip
Scanning feature-pack wildfly-galleon-pack-7.3.0.SP1-redhat-00001.zip
Zipping tool-work-dir/maven-repo/jboss-eap-7.3.0.SP1-image-builder-maven-repository to output-repo.zip


Created patches:
 * patch org.wildfly.core:wildfly-core-galleon-pack:10.1.2.SP1-patch-redhat-00001 for wildfly-core-galleon-pack-10.1.2.SP1-redhat-00001.zip
   - org.aesh:aesh:2.4.0.redhat-00001::jar => org.aesh:aesh:2.5.0.redhat-00001::jar
 * patch org.jboss.eap:wildfly-galleon-pack:7.3.0.SP1-patch-redhat-00001 for wildfly-galleon-pack-7.3.0.SP1-redhat-00001.zip
   - org.jboss.resteasy:resteasy-jaxrs:3.9.3.SP1-redhat-00001::jar => org.jboss.resteasy:resteasy-jaxrs:3.10-redhat-00001::jar

Added artifacts:
 - org/aesh/aesh/2.5.0.redhat-00001/*
 - org/jboss/resteasy/resteasy-jaxrs/3.10-redhat-00001/*

Deleted artifacts:
 - org/aesh/aesh/2.4.0.redhat-00001/*
 - org/jboss/resteasy/resteasy-jaxrs/3.9.3.SP1-redhat-00001/*

Content of patches.xml:
<patches>
<patch id="org.wildfly.core:wildfly-core-galleon-pack:10.1.2.SP1-patch-redhat-00001"/>
<patch id="org.jboss.eap:wildfly-galleon-pack:7.3.0.SP1-patch-redhat-00001"/>
</patches>
```
