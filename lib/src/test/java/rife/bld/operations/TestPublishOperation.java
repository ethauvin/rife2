/*
 * Copyright 2001-2023 Geert Bevin (gbevin[remove] at uwyn dot com)
 * Licensed under the Apache License, Version 2.0 (the "License")
 */
package rife.bld.operations;

import com.reposilite.ReposiliteLauncherKt;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import rife.bld.Project;
import rife.bld.blueprints.BlankProjectBlueprint;
import rife.bld.dependencies.*;
import rife.bld.publish.PublishInfo;
import rife.tools.FileUtils;
import rife.tools.exceptions.FileUtilsErrorException;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class TestPublishOperation {
    @Test
    void testInstantiation() {
        var operation = new PublishOperation();
        assertNull(operation.repository());
        assertNull(operation.moment());
        assertTrue(operation.dependencies().isEmpty());
        assertNotNull(operation.info());
        assertNull(operation.info().groupId());
        assertNull(operation.info().artifactId());
        assertNull(operation.info().version());
        assertNull(operation.info().name());
        assertNull(operation.info().description());
        assertNull(operation.info().url());
        assertTrue(operation.info().licenses().isEmpty());
        assertTrue(operation.info().developers().isEmpty());
        assertNull(operation.info().scm());
        assertTrue(operation.artifacts().isEmpty());
    }

    @Test
    void testPopulation() {
        var repository = new Repository("repository1");
        var moment = ZonedDateTime.now();

        var operation1 = new PublishOperation()
            .repository(repository)
            .moment(moment);
        assertEquals(repository, operation1.repository());
        assertEquals(moment, operation1.moment());
    }

    @Test
    void testPublishRelease()
    throws Exception {
        var tmp1 = Files.createTempDirectory("test1").toFile();
        var tmp2 = Files.createTempDirectory("test2").toFile();
        var tmp_reposilite = Files.createTempDirectory("test").toFile();
        try {
            var repository = ReposiliteLauncherKt.createWithParameters(
                "-wd", tmp_reposilite.getAbsolutePath(),
                "-p", "8081",
                "--token", "manager:passwd");
            repository.launch();

            // wait for full startup
            Thread.sleep(2000);

            // verify the version doesn't exist
            assertThrows(FileUtilsErrorException.class, () -> FileUtils.readString(new URL("http://localhost:8081/api/maven/details/releases/test/pkg/myapp")));
            assertThrows(FileUtilsErrorException.class, () -> FileUtils.readString(new URL("http://localhost:8081/api/maven/details/releases/test/pkg/myapp/0.0.1")));

            // create a first publication
            var create_operation1 = new CreateBlankOperation()
                .workDirectory(tmp1)
                .packageName("test.pkg")
                .projectName("myapp")
                .downloadDependencies(true);
            create_operation1.execute();

            new CompileOperation()
                .fromProject(create_operation1.project())
                .execute();

            var jar_operation1 = new JarOperation()
                .fromProject(create_operation1.project());
            jar_operation1.execute();

            var publish_operation1 = new PublishOperation()
                .fromProject(create_operation1.project())
                .repository(new Repository("http://localhost:8081/releases", "manager", "passwd"));
            publish_operation1.execute();

            var dir_json1 = new JSONObject(FileUtils.readString(new URL("http://localhost:8081/api/maven/details/releases/test/pkg/myapp")));
            assertEquals("myapp", dir_json1.get("name"));
            var dir_files_json1 = dir_json1.getJSONArray("files");
            assertEquals(6, dir_files_json1.length());
            assertEquals("0.0.1", dir_files_json1.getJSONObject(0).get("name"));
            assertEquals("maven-metadata.xml.md5", dir_files_json1.getJSONObject(1).get("name"));
            assertEquals("maven-metadata.xml.sha1", dir_files_json1.getJSONObject(2).get("name"));
            assertEquals("maven-metadata.xml.sha256", dir_files_json1.getJSONObject(3).get("name"));
            assertEquals("maven-metadata.xml.sha512", dir_files_json1.getJSONObject(4).get("name"));
            assertEquals("maven-metadata.xml", dir_files_json1.getJSONObject(5).get("name"));

            var maven_metadata1 = new Xml2MavenMetadata();
            maven_metadata1.processXml(FileUtils.readString(new URL("http://localhost:8081/releases/test/pkg/myapp/maven-metadata.xml")));
            assertEquals(create_operation1.project().version(), maven_metadata1.getLatest());
            assertEquals(create_operation1.project().version(), maven_metadata1.getRelease());
            assertEquals(VersionNumber.UNKNOWN, maven_metadata1.getSnapshot());
            assertNull(maven_metadata1.getSnapshotTimestamp());
            assertNull(maven_metadata1.getSnapshotBuildNumber());
            assertEquals(1, maven_metadata1.getVersions().size());
            assertTrue(maven_metadata1.getVersions().contains(create_operation1.project().version()));

            var version_json1 = new JSONObject(FileUtils.readString(new URL("http://localhost:8081/api/maven/details/releases/test/pkg/myapp/0.0.1")));
            assertEquals("0.0.1", version_json1.get("name"));
            var version_files_json1 = version_json1.getJSONArray("files");
            assertEquals(10, version_files_json1.length());
            assertEquals("myapp-0.0.1.jar.md5", version_files_json1.getJSONObject(0).get("name"));
            assertEquals("myapp-0.0.1.jar.sha1", version_files_json1.getJSONObject(1).get("name"));
            assertEquals("myapp-0.0.1.jar.sha256", version_files_json1.getJSONObject(2).get("name"));
            assertEquals("myapp-0.0.1.jar.sha512", version_files_json1.getJSONObject(3).get("name"));
            assertEquals("myapp-0.0.1.jar", version_files_json1.getJSONObject(4).get("name"));
            assertEquals("myapp-0.0.1.pom.md5", version_files_json1.getJSONObject(5).get("name"));
            assertEquals("myapp-0.0.1.pom.sha1", version_files_json1.getJSONObject(6).get("name"));
            assertEquals("myapp-0.0.1.pom.sha256", version_files_json1.getJSONObject(7).get("name"));
            assertEquals("myapp-0.0.1.pom.sha512", version_files_json1.getJSONObject(8).get("name"));
            assertEquals("myapp-0.0.1.pom", version_files_json1.getJSONObject(9).get("name"));

            // created an updated publication
            var create_operation2 = new CreateBlankOperation() {
                protected Project createProjectBlueprint() {
                    return new BlankProjectBlueprint(new File(workDirectory(), projectName()), packageName(), projectName(), new VersionNumber(1, 0, 0));
                }
            }
                .workDirectory(tmp2)
                .packageName("test.pkg")
                .projectName("myapp")
                .downloadDependencies(true);
            create_operation2.execute();

            new CompileOperation()
                .fromProject(create_operation2.project())
                .execute();

            var jar_operation2 = new JarOperation()
                .fromProject(create_operation2.project());
            jar_operation2.execute();

            var publish_operation2 = new PublishOperation()
                .fromProject(create_operation2.project())
                .repository(new Repository("http://localhost:8081/releases", "manager", "passwd"));
            publish_operation2.execute();

            var dir_json2 = new JSONObject(FileUtils.readString(new URL("http://localhost:8081/api/maven/details/releases/test/pkg/myapp")));
            assertEquals("myapp", dir_json2.get("name"));
            var dir_files_json2 = dir_json2.getJSONArray("files");
            assertEquals(7, dir_files_json2.length());
            assertEquals("0.0.1", dir_files_json2.getJSONObject(0).get("name"));
            assertEquals("1.0.0", dir_files_json2.getJSONObject(1).get("name"));
            assertEquals("maven-metadata.xml.md5", dir_files_json2.getJSONObject(2).get("name"));
            assertEquals("maven-metadata.xml.sha1", dir_files_json2.getJSONObject(3).get("name"));
            assertEquals("maven-metadata.xml.sha256", dir_files_json2.getJSONObject(4).get("name"));
            assertEquals("maven-metadata.xml.sha512", dir_files_json2.getJSONObject(5).get("name"));
            assertEquals("maven-metadata.xml", dir_files_json2.getJSONObject(6).get("name"));

            var maven_metadata2 = new Xml2MavenMetadata();
            maven_metadata2.processXml(FileUtils.readString(new URL("http://localhost:8081/releases/test/pkg/myapp/maven-metadata.xml")));
            assertEquals(create_operation2.project().version(), maven_metadata2.getLatest());
            assertEquals(create_operation2.project().version(), maven_metadata2.getRelease());
            assertEquals(VersionNumber.UNKNOWN, maven_metadata2.getSnapshot());
            assertNull(maven_metadata2.getSnapshotTimestamp());
            assertNull(maven_metadata2.getSnapshotBuildNumber());
            assertEquals(2, maven_metadata2.getVersions().size());
            assertTrue(maven_metadata2.getVersions().contains(create_operation1.project().version()));
            assertTrue(maven_metadata2.getVersions().contains(create_operation2.project().version()));

            var version_json1b = new JSONObject(FileUtils.readString(new URL("http://localhost:8081/api/maven/details/releases/test/pkg/myapp/0.0.1")));
            assertEquals("0.0.1", version_json1b.get("name"));
            var version_files_json1b = version_json1b.getJSONArray("files");
            assertEquals(10, version_files_json1b.length());
            assertEquals("myapp-0.0.1.jar.md5", version_files_json1b.getJSONObject(0).get("name"));
            assertEquals("myapp-0.0.1.jar.sha1", version_files_json1b.getJSONObject(1).get("name"));
            assertEquals("myapp-0.0.1.jar.sha256", version_files_json1b.getJSONObject(2).get("name"));
            assertEquals("myapp-0.0.1.jar.sha512", version_files_json1b.getJSONObject(3).get("name"));
            assertEquals("myapp-0.0.1.jar", version_files_json1b.getJSONObject(4).get("name"));
            assertEquals("myapp-0.0.1.pom.md5", version_files_json1b.getJSONObject(5).get("name"));
            assertEquals("myapp-0.0.1.pom.sha1", version_files_json1b.getJSONObject(6).get("name"));
            assertEquals("myapp-0.0.1.pom.sha256", version_files_json1b.getJSONObject(7).get("name"));
            assertEquals("myapp-0.0.1.pom.sha512", version_files_json1b.getJSONObject(8).get("name"));
            assertEquals("myapp-0.0.1.pom", version_files_json1b.getJSONObject(9).get("name"));

            var version_json2 = new JSONObject(FileUtils.readString(new URL("http://localhost:8081/api/maven/details/releases/test/pkg/myapp/1.0.0")));
            assertEquals("1.0.0", version_json2.get("name"));
            var version_files_json2 = version_json2.getJSONArray("files");
            assertEquals(10, version_files_json2.length());
            assertEquals("myapp-1.0.0.jar.md5", version_files_json2.getJSONObject(0).get("name"));
            assertEquals("myapp-1.0.0.jar.sha1", version_files_json2.getJSONObject(1).get("name"));
            assertEquals("myapp-1.0.0.jar.sha256", version_files_json2.getJSONObject(2).get("name"));
            assertEquals("myapp-1.0.0.jar.sha512", version_files_json2.getJSONObject(3).get("name"));
            assertEquals("myapp-1.0.0.jar", version_files_json2.getJSONObject(4).get("name"));
            assertEquals("myapp-1.0.0.pom.md5", version_files_json2.getJSONObject(5).get("name"));
            assertEquals("myapp-1.0.0.pom.sha1", version_files_json2.getJSONObject(6).get("name"));
            assertEquals("myapp-1.0.0.pom.sha256", version_files_json2.getJSONObject(7).get("name"));
            assertEquals("myapp-1.0.0.pom.sha512", version_files_json2.getJSONObject(8).get("name"));
            assertEquals("myapp-1.0.0.pom", version_files_json2.getJSONObject(9).get("name"));

            repository.shutdown();
        } finally {
            FileUtils.deleteDirectory(tmp_reposilite);
            FileUtils.deleteDirectory(tmp2);
            FileUtils.deleteDirectory(tmp1);
        }
    }

    @Test
    void testPublishReleaseLocal()
    throws Exception {
        var tmp1 = Files.createTempDirectory("test1").toFile();
        var tmp2 = Files.createTempDirectory("test2").toFile();
        var tmp_local = Files.createTempDirectory("test").toFile();
        try {
            // create a first publication
            var create_operation1 = new CreateBlankOperation()
                .workDirectory(tmp1)
                .packageName("test.pkg")
                .projectName("myapp")
                .downloadDependencies(true);
            create_operation1.execute();

            new CompileOperation()
                .fromProject(create_operation1.project())
                .execute();

            var jar_operation1 = new JarOperation()
                .fromProject(create_operation1.project());
            jar_operation1.execute();

            var publish_operation1 = new PublishOperation()
                .fromProject(create_operation1.project())
                .repository(new Repository(tmp_local.getAbsolutePath()));
            publish_operation1.execute();

            assertEquals("""
                /test
                /test/pkg
                /test/pkg/myapp
                /test/pkg/myapp/0.0.1
                /test/pkg/myapp/0.0.1/myapp-0.0.1.jar
                /test/pkg/myapp/0.0.1/myapp-0.0.1.pom
                /test/pkg/myapp/maven-metadata-local.xml""", FileUtils.generateDirectoryListing(tmp_local));

            var maven_metadata1 = new Xml2MavenMetadata();
            maven_metadata1.processXml(FileUtils.readString(Path.of(tmp_local.getAbsolutePath(), "test", "pkg", "myapp", "maven-metadata-local.xml").toFile()));
            assertEquals(create_operation1.project().version(), maven_metadata1.getLatest());
            assertEquals(create_operation1.project().version(), maven_metadata1.getRelease());
            assertEquals(VersionNumber.UNKNOWN, maven_metadata1.getSnapshot());
            assertNull(maven_metadata1.getSnapshotTimestamp());
            assertNull(maven_metadata1.getSnapshotBuildNumber());
            assertEquals(1, maven_metadata1.getVersions().size());
            assertTrue(maven_metadata1.getVersions().contains(create_operation1.project().version()));

            // created an updated publication
            var create_operation2 = new CreateBlankOperation() {
                protected Project createProjectBlueprint() {
                    return new BlankProjectBlueprint(new File(workDirectory(), projectName()), packageName(), projectName(), new VersionNumber(1, 0, 0));
                }
            }
                .workDirectory(tmp2)
                .packageName("test.pkg")
                .projectName("myapp")
                .downloadDependencies(true);
            create_operation2.execute();

            new CompileOperation()
                .fromProject(create_operation2.project())
                .execute();

            var jar_operation2 = new JarOperation()
                .fromProject(create_operation2.project());
            jar_operation2.execute();

            var publish_operation2 = new PublishOperation()
                .fromProject(create_operation2.project())
                .repository(new Repository(tmp_local.getAbsolutePath()));
            publish_operation2.execute();

            assertEquals("""
                /test
                /test/pkg
                /test/pkg/myapp
                /test/pkg/myapp/0.0.1
                /test/pkg/myapp/0.0.1/myapp-0.0.1.jar
                /test/pkg/myapp/0.0.1/myapp-0.0.1.pom
                /test/pkg/myapp/1.0.0
                /test/pkg/myapp/1.0.0/myapp-1.0.0.jar
                /test/pkg/myapp/1.0.0/myapp-1.0.0.pom
                /test/pkg/myapp/maven-metadata-local.xml""", FileUtils.generateDirectoryListing(tmp_local));

            var maven_metadata2 = new Xml2MavenMetadata();
            maven_metadata2.processXml(FileUtils.readString(Path.of(tmp_local.getAbsolutePath(), "test", "pkg", "myapp", "maven-metadata-local.xml").toFile()));
            assertEquals(create_operation2.project().version(), maven_metadata2.getLatest());
            assertEquals(create_operation2.project().version(), maven_metadata2.getRelease());
            assertEquals(VersionNumber.UNKNOWN, maven_metadata2.getSnapshot());
            assertNull(maven_metadata2.getSnapshotTimestamp());
            assertNull(maven_metadata2.getSnapshotBuildNumber());
            assertEquals(2, maven_metadata2.getVersions().size());
            assertTrue(maven_metadata2.getVersions().contains(create_operation1.project().version()));
            assertTrue(maven_metadata2.getVersions().contains(create_operation2.project().version()));
        } finally {
            FileUtils.deleteDirectory(tmp_local);
            FileUtils.deleteDirectory(tmp2);
            FileUtils.deleteDirectory(tmp1);
        }
    }

    @Test
    void testPublishSnapshot()
    throws Exception {
        var tmp1 = Files.createTempDirectory("test1").toFile();
        var tmp2 = Files.createTempDirectory("test2").toFile();
        var tmp_reposilite = Files.createTempDirectory("test").toFile();
        try {
            var repository = ReposiliteLauncherKt.createWithParameters(
                "-wd", tmp_reposilite.getAbsolutePath(),
                "-p", "8081",
                "--token", "manager:passwd");
            repository.launch();

            // wait for full startup
            Thread.sleep(2000);

            // verify the version doesn't exist
            assertThrows(FileUtilsErrorException.class, () -> FileUtils.readString(new URL("http://localhost:8081/api/maven/details/releases/test/pkg/myapp")));
            assertThrows(FileUtilsErrorException.class, () -> FileUtils.readString(new URL("http://localhost:8081/api/maven/details/releases/test/pkg/myapp/1.2.3-SNAPSHOT")));

            // create a first publication
            var create_operation1 = new CreateBlankOperation() {
                protected Project createProjectBlueprint() {
                    return new BlankProjectBlueprint(new File(workDirectory(), projectName()), packageName(), projectName(), new VersionNumber(1, 2, 3, "SNAPSHOT"));
                }
            }
                .workDirectory(tmp1)
                .packageName("test.pkg")
                .projectName("myapp")
                .downloadDependencies(true);
            create_operation1.execute();

            new CompileOperation()
                .fromProject(create_operation1.project())
                .execute();

            var jar_operation1 = new JarOperation()
                .fromProject(create_operation1.project());
            jar_operation1.execute();

            var publish_operation1 = new PublishOperation()
                .fromProject(create_operation1.project())
                .moment(ZonedDateTime.of(2023, 3, 29, 18, 54, 32, 909, ZoneId.of("America/New_York")))
                .repository(new Repository("http://localhost:8081/releases", "manager", "passwd"));
            publish_operation1.execute();

            var dir_json1 = new JSONObject(FileUtils.readString(new URL("http://localhost:8081/api/maven/details/releases/test/pkg/myapp")));
            assertEquals("myapp", dir_json1.get("name"));
            var dir_files_json1 = dir_json1.getJSONArray("files");
            assertEquals(6, dir_files_json1.length());
            assertEquals("1.2.3-SNAPSHOT", dir_files_json1.getJSONObject(0).get("name"));
            assertEquals("maven-metadata.xml.md5", dir_files_json1.getJSONObject(1).get("name"));
            assertEquals("maven-metadata.xml.sha1", dir_files_json1.getJSONObject(2).get("name"));
            assertEquals("maven-metadata.xml.sha256", dir_files_json1.getJSONObject(3).get("name"));
            assertEquals("maven-metadata.xml.sha512", dir_files_json1.getJSONObject(4).get("name"));
            assertEquals("maven-metadata.xml", dir_files_json1.getJSONObject(5).get("name"));

            var maven_metadata1 = new Xml2MavenMetadata();
            maven_metadata1.processXml(FileUtils.readString(new URL("http://localhost:8081/releases/test/pkg/myapp/maven-metadata.xml")));
            assertEquals(create_operation1.project().version(), maven_metadata1.getLatest());
            assertEquals(create_operation1.project().version(), maven_metadata1.getRelease());
            assertEquals(VersionNumber.UNKNOWN, maven_metadata1.getSnapshot());
            assertNull(maven_metadata1.getSnapshotTimestamp());
            assertNull(maven_metadata1.getSnapshotBuildNumber());
            assertEquals(1, maven_metadata1.getVersions().size());
            assertTrue(maven_metadata1.getVersions().contains(create_operation1.project().version()));

            var version_json1 = new JSONObject(FileUtils.readString(new URL("http://localhost:8081/api/maven/details/releases/test/pkg/myapp/1.2.3-SNAPSHOT")));
            assertEquals("1.2.3-SNAPSHOT", version_json1.get("name"));
            var version_files_json1 = version_json1.getJSONArray("files");
            assertEquals(15, version_files_json1.length());
            assertEquals("maven-metadata.xml.md5", version_files_json1.getJSONObject(0).get("name"));
            assertEquals("maven-metadata.xml.sha1", version_files_json1.getJSONObject(1).get("name"));
            assertEquals("maven-metadata.xml.sha256", version_files_json1.getJSONObject(2).get("name"));
            assertEquals("maven-metadata.xml.sha512", version_files_json1.getJSONObject(3).get("name"));
            assertEquals("maven-metadata.xml", version_files_json1.getJSONObject(4).get("name"));
            assertEquals("myapp-1.2.3-20230329.225432-1.jar.md5", version_files_json1.getJSONObject(5).get("name"));
            assertEquals("myapp-1.2.3-20230329.225432-1.jar.sha1", version_files_json1.getJSONObject(6).get("name"));
            assertEquals("myapp-1.2.3-20230329.225432-1.jar.sha256", version_files_json1.getJSONObject(7).get("name"));
            assertEquals("myapp-1.2.3-20230329.225432-1.jar.sha512", version_files_json1.getJSONObject(8).get("name"));
            assertEquals("myapp-1.2.3-20230329.225432-1.jar", version_files_json1.getJSONObject(9).get("name"));
            assertEquals("myapp-1.2.3-20230329.225432-1.pom.md5", version_files_json1.getJSONObject(10).get("name"));
            assertEquals("myapp-1.2.3-20230329.225432-1.pom.sha1", version_files_json1.getJSONObject(11).get("name"));
            assertEquals("myapp-1.2.3-20230329.225432-1.pom.sha256", version_files_json1.getJSONObject(12).get("name"));
            assertEquals("myapp-1.2.3-20230329.225432-1.pom.sha512", version_files_json1.getJSONObject(13).get("name"));
            assertEquals("myapp-1.2.3-20230329.225432-1.pom", version_files_json1.getJSONObject(14).get("name"));

            var maven_snapshot_metadata1 = new Xml2MavenMetadata();
            maven_snapshot_metadata1.processXml(FileUtils.readString(new URL("http://localhost:8081/releases/test/pkg/myapp/1.2.3-SNAPSHOT/maven-metadata.xml")));
            assertEquals(create_operation1.project().version(), maven_snapshot_metadata1.getLatest());
            assertEquals(VersionNumber.UNKNOWN, maven_snapshot_metadata1.getRelease());
            assertEquals(new VersionNumber(1, 2, 3, "20230329.225432-1"), maven_snapshot_metadata1.getSnapshot());
            assertEquals("20230329.225432", maven_snapshot_metadata1.getSnapshotTimestamp());
            assertEquals(1, maven_snapshot_metadata1.getSnapshotBuildNumber());
            assertEquals(1, maven_snapshot_metadata1.getVersions().size());
            assertTrue(maven_snapshot_metadata1.getVersions().contains(create_operation1.project().version()));

            // created an updated publication
            var create_operation2 = new CreateBlankOperation() {
                protected Project createProjectBlueprint() {
                    return new BlankProjectBlueprint(new File(workDirectory(), projectName()), packageName(), projectName(), new VersionNumber(1, 2, 3, "SNAPSHOT"));
                }
            }
                .workDirectory(tmp2)
                .packageName("test.pkg")
                .projectName("myapp")
                .downloadDependencies(true);
            create_operation2.execute();

            new CompileOperation()
                .fromProject(create_operation2.project())
                .execute();

            var jar_operation2 = new JarOperation()
                .fromProject(create_operation2.project());
            jar_operation2.execute();

            var publish_operation2 = new PublishOperation()
                .fromProject(create_operation2.project())
                .moment(ZonedDateTime.of(2023, 3, 30, 13, 17, 29, 89, ZoneId.of("America/New_York")))
                .repository(new Repository("http://localhost:8081/releases", "manager", "passwd"));
            publish_operation2.execute();

            var dir_json2 = new JSONObject(FileUtils.readString(new URL("http://localhost:8081/api/maven/details/releases/test/pkg/myapp")));
            assertEquals("myapp", dir_json2.get("name"));
            var dir_files_json2 = dir_json2.getJSONArray("files");
            assertEquals(6, dir_files_json2.length());
            assertEquals("1.2.3-SNAPSHOT", dir_files_json2.getJSONObject(0).get("name"));
            assertEquals("maven-metadata.xml.md5", dir_files_json2.getJSONObject(1).get("name"));
            assertEquals("maven-metadata.xml.sha1", dir_files_json2.getJSONObject(2).get("name"));
            assertEquals("maven-metadata.xml.sha256", dir_files_json2.getJSONObject(3).get("name"));
            assertEquals("maven-metadata.xml.sha512", dir_files_json2.getJSONObject(4).get("name"));
            assertEquals("maven-metadata.xml", dir_files_json2.getJSONObject(5).get("name"));

            var maven_metadata2 = new Xml2MavenMetadata();
            maven_metadata2.processXml(FileUtils.readString(new URL("http://localhost:8081/releases/test/pkg/myapp/maven-metadata.xml")));
            assertEquals(create_operation2.project().version(), maven_metadata2.getLatest());
            assertEquals(create_operation2.project().version(), maven_metadata2.getRelease());
            assertEquals(VersionNumber.UNKNOWN, maven_metadata2.getSnapshot());
            assertNull(maven_metadata2.getSnapshotTimestamp());
            assertNull(maven_metadata2.getSnapshotBuildNumber());
            assertEquals(1, maven_metadata2.getVersions().size());
            assertTrue(maven_metadata2.getVersions().contains(create_operation1.project().version()));
            assertTrue(maven_metadata2.getVersions().contains(create_operation2.project().version()));

            var version_json2 = new JSONObject(FileUtils.readString(new URL("http://localhost:8081/api/maven/details/releases/test/pkg/myapp/1.2.3-SNAPSHOT")));
            assertEquals("1.2.3-SNAPSHOT", version_json2.get("name"));
            var version_files_json2 = version_json2.getJSONArray("files");
            assertEquals(15, version_files_json2.length());
            assertEquals("maven-metadata.xml.md5", version_files_json2.getJSONObject(0).get("name"));
            assertEquals("maven-metadata.xml.sha1", version_files_json2.getJSONObject(1).get("name"));
            assertEquals("maven-metadata.xml.sha256", version_files_json2.getJSONObject(2).get("name"));
            assertEquals("maven-metadata.xml.sha512", version_files_json2.getJSONObject(3).get("name"));
            assertEquals("maven-metadata.xml", version_files_json2.getJSONObject(4).get("name"));
            assertEquals("myapp-1.2.3-20230330.171729-2.jar.md5", version_files_json2.getJSONObject(5).get("name"));
            assertEquals("myapp-1.2.3-20230330.171729-2.jar.sha1", version_files_json2.getJSONObject(6).get("name"));
            assertEquals("myapp-1.2.3-20230330.171729-2.jar.sha256", version_files_json2.getJSONObject(7).get("name"));
            assertEquals("myapp-1.2.3-20230330.171729-2.jar.sha512", version_files_json2.getJSONObject(8).get("name"));
            assertEquals("myapp-1.2.3-20230330.171729-2.jar", version_files_json2.getJSONObject(9).get("name"));
            assertEquals("myapp-1.2.3-20230330.171729-2.pom.md5", version_files_json2.getJSONObject(10).get("name"));
            assertEquals("myapp-1.2.3-20230330.171729-2.pom.sha1", version_files_json2.getJSONObject(11).get("name"));
            assertEquals("myapp-1.2.3-20230330.171729-2.pom.sha256", version_files_json2.getJSONObject(12).get("name"));
            assertEquals("myapp-1.2.3-20230330.171729-2.pom.sha512", version_files_json2.getJSONObject(13).get("name"));
            assertEquals("myapp-1.2.3-20230330.171729-2.pom", version_files_json2.getJSONObject(14).get("name"));

            var maven_snapshot_metadata2 = new Xml2MavenMetadata();
            maven_snapshot_metadata2.processXml(FileUtils.readString(new URL("http://localhost:8081/releases/test/pkg/myapp/1.2.3-SNAPSHOT/maven-metadata.xml")));
            assertEquals(create_operation2.project().version(), maven_snapshot_metadata2.getLatest());
            assertEquals(VersionNumber.UNKNOWN, maven_snapshot_metadata2.getRelease());
            assertEquals(new VersionNumber(1, 2, 3, "20230330.171729-2"), maven_snapshot_metadata2.getSnapshot());
            assertEquals("20230330.171729", maven_snapshot_metadata2.getSnapshotTimestamp());
            assertEquals(2, maven_snapshot_metadata2.getSnapshotBuildNumber());
            assertEquals(1, maven_snapshot_metadata2.getVersions().size());
            assertTrue(maven_snapshot_metadata2.getVersions().contains(create_operation2.project().version()));

            repository.shutdown();
        } finally {
            FileUtils.deleteDirectory(tmp_reposilite);
            FileUtils.deleteDirectory(tmp2);
            FileUtils.deleteDirectory(tmp1);
        }
    }

    @Test
    void testPublishSnapshotLocal()
    throws Exception {
        var tmp1 = Files.createTempDirectory("test1").toFile();
        var tmp2 = Files.createTempDirectory("test2").toFile();
        var tmp_local = Files.createTempDirectory("test").toFile();
        try {            // create a first publication
            var create_operation1 = new CreateBlankOperation() {
                protected Project createProjectBlueprint() {
                    return new BlankProjectBlueprint(new File(workDirectory(), projectName()), packageName(), projectName(), new VersionNumber(1, 2, 3, "SNAPSHOT"));
                }
            }
                .workDirectory(tmp1)
                .packageName("test.pkg")
                .projectName("myapp")
                .downloadDependencies(true);
            create_operation1.execute();

            new CompileOperation()
                .fromProject(create_operation1.project())
                .execute();

            var jar_operation1 = new JarOperation()
                .fromProject(create_operation1.project());
            jar_operation1.execute();

            var publish_operation1 = new PublishOperation()
                .fromProject(create_operation1.project())
                .moment(ZonedDateTime.of(2023, 3, 29, 18, 54, 32, 909, ZoneId.of("America/New_York")))
                .repository(new Repository(tmp_local.getAbsolutePath()));
            publish_operation1.execute();

            assertEquals("""
                /test
                /test/pkg
                /test/pkg/myapp
                /test/pkg/myapp/1.2.3-SNAPSHOT
                /test/pkg/myapp/1.2.3-SNAPSHOT/maven-metadata-local.xml
                /test/pkg/myapp/1.2.3-SNAPSHOT/myapp-1.2.3-SNAPSHOT.jar
                /test/pkg/myapp/1.2.3-SNAPSHOT/myapp-1.2.3-SNAPSHOT.pom
                /test/pkg/myapp/maven-metadata-local.xml""", FileUtils.generateDirectoryListing(tmp_local));

            var maven_metadata1 = new Xml2MavenMetadata();
            maven_metadata1.processXml(FileUtils.readString(Path.of(tmp_local.getAbsolutePath(), "test", "pkg", "myapp", "maven-metadata-local.xml").toFile()));
            assertEquals(create_operation1.project().version(), maven_metadata1.getLatest());
            assertEquals(create_operation1.project().version(), maven_metadata1.getRelease());
            assertEquals(VersionNumber.UNKNOWN, maven_metadata1.getSnapshot());
            assertNull(maven_metadata1.getSnapshotTimestamp());
            assertNull(maven_metadata1.getSnapshotBuildNumber());
            assertEquals(1, maven_metadata1.getVersions().size());
            assertTrue(maven_metadata1.getVersions().contains(create_operation1.project().version()));

            var maven_snapshot_metadata1 = new Xml2MavenMetadata();
            maven_snapshot_metadata1.processXml(FileUtils.readString(Path.of(tmp_local.getAbsolutePath(), "test", "pkg", "myapp", "1.2.3-SNAPSHOT", "maven-metadata-local.xml").toFile()));
            assertEquals(create_operation1.project().version(), maven_snapshot_metadata1.getLatest());
            assertEquals(VersionNumber.UNKNOWN, maven_snapshot_metadata1.getRelease());
            assertEquals(new VersionNumber(1, 2, 3, "SNAPSHOT"), maven_snapshot_metadata1.getSnapshot());
            assertNull(maven_snapshot_metadata1.getSnapshotTimestamp());
            assertNull(maven_snapshot_metadata1.getSnapshotBuildNumber());
            assertEquals(1, maven_snapshot_metadata1.getVersions().size());
            assertTrue(maven_snapshot_metadata1.getVersions().contains(create_operation1.project().version()));

            // created an updated publication
            var create_operation2 = new CreateBlankOperation() {
                protected Project createProjectBlueprint() {
                    return new BlankProjectBlueprint(new File(workDirectory(), projectName()), packageName(), projectName(), new VersionNumber(1, 2, 3, "SNAPSHOT"));
                }
            }
                .workDirectory(tmp2)
                .packageName("test.pkg")
                .projectName("myapp")
                .downloadDependencies(true);
            create_operation2.execute();

            new CompileOperation()
                .fromProject(create_operation2.project())
                .execute();

            var jar_operation2 = new JarOperation()
                .fromProject(create_operation2.project());
            jar_operation2.execute();

            var publish_operation2 = new PublishOperation()
                .fromProject(create_operation2.project())
                .moment(ZonedDateTime.of(2023, 3, 30, 13, 17, 29, 89, ZoneId.of("America/New_York")))
                .repository(new Repository(tmp_local.getAbsolutePath()));
            publish_operation2.execute();

            assertEquals("""
                /test
                /test/pkg
                /test/pkg/myapp
                /test/pkg/myapp/1.2.3-SNAPSHOT
                /test/pkg/myapp/1.2.3-SNAPSHOT/maven-metadata-local.xml
                /test/pkg/myapp/1.2.3-SNAPSHOT/myapp-1.2.3-SNAPSHOT.jar
                /test/pkg/myapp/1.2.3-SNAPSHOT/myapp-1.2.3-SNAPSHOT.pom
                /test/pkg/myapp/maven-metadata-local.xml""", FileUtils.generateDirectoryListing(tmp_local));

            var maven_metadata2 = new Xml2MavenMetadata();
            maven_metadata2.processXml(FileUtils.readString(Path.of(tmp_local.getAbsolutePath(), "test", "pkg", "myapp", "maven-metadata-local.xml").toFile()));
            assertEquals(create_operation2.project().version(), maven_metadata2.getLatest());
            assertEquals(create_operation2.project().version(), maven_metadata2.getRelease());
            assertEquals(VersionNumber.UNKNOWN, maven_metadata2.getSnapshot());
            assertNull(maven_metadata2.getSnapshotTimestamp());
            assertNull(maven_metadata2.getSnapshotBuildNumber());
            assertEquals(1, maven_metadata2.getVersions().size());
            assertTrue(maven_metadata2.getVersions().contains(create_operation1.project().version()));
            assertTrue(maven_metadata2.getVersions().contains(create_operation2.project().version()));

            var maven_snapshot_metadata2 = new Xml2MavenMetadata();
            maven_snapshot_metadata2.processXml(FileUtils.readString(Path.of(tmp_local.getAbsolutePath(), "test", "pkg", "myapp", "1.2.3-SNAPSHOT", "maven-metadata-local.xml").toFile()));
            assertEquals(create_operation2.project().version(), maven_snapshot_metadata2.getLatest());
            assertEquals(VersionNumber.UNKNOWN, maven_snapshot_metadata2.getRelease());
            assertEquals(new VersionNumber(1, 2, 3, "SNAPSHOT"), maven_snapshot_metadata2.getSnapshot());
            assertNull(maven_snapshot_metadata2.getSnapshotTimestamp());
            assertNull(maven_snapshot_metadata2.getSnapshotBuildNumber());
            assertEquals(1, maven_snapshot_metadata2.getVersions().size());
            assertTrue(maven_snapshot_metadata2.getVersions().contains(create_operation2.project().version()));
        } finally {
            FileUtils.deleteDirectory(tmp_local);
            FileUtils.deleteDirectory(tmp2);
            FileUtils.deleteDirectory(tmp1);
        }
    }
}
