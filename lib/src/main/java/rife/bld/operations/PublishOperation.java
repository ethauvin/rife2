/*
 * Copyright 2001-2023 Geert Bevin (gbevin[remove] at uwyn dot com)
 * Licensed under the Apache License, Version 2.0 (the "License")
 */
package rife.bld.operations;

import rife.bld.Project;
import rife.bld.dependencies.*;
import rife.bld.dependencies.exceptions.ArtifactNotFoundException;
import rife.bld.operations.exceptions.OperationOptionException;
import rife.bld.operations.exceptions.UploadException;
import rife.bld.publish.*;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static rife.bld.publish.MetadataBuilder.SNAPSHOT_TIMESTAMP_FORMATTER;
import static rife.tools.HttpUtils.HEADER_AUTHORIZATION;
import static rife.tools.HttpUtils.basicAuthorizationHeader;
import static rife.tools.StringUtils.encodeHexLower;

/**
 * Published artifacts to a Maven repository.
 *
 * @author Geert Bevin (gbevin[remove] at uwyn dot com)
 * @since 1.5.7
 */
public class PublishOperation extends AbstractOperation<PublishOperation> {
    private ZonedDateTime moment_ = null;
    private Repository repository_;
    private final DependencyScopes dependencies_ = new DependencyScopes();
    private PublishInfo info_ = new PublishInfo();
    private final List<PublishArtifact> artifacts_ = new ArrayList<>();

    /**
     * Performs the publish operation.
     *
     * @since 1.5.7
     */
    public void execute() {
        if (repository() == null) {
            throw new OperationOptionException("ERROR: the publication repository should be specified");
        }

        var moment = moment_;
        if (moment == null) {
            moment = ZonedDateTime.now();
        }

        var current_versions = new ArrayList<VersionNumber>();
        var resolver = new DependencyResolver(List.of(repository()), new Dependency(info().groupId(), info().artifactId(), info().version()));
        try {
            current_versions.addAll(resolver.getMavenMetadata().getVersions());
        } catch (ArtifactNotFoundException e) {
            // no existing versions could be found
        }

        var client = HttpClient.newHttpClient();

        var info_version = info().version();
        var actual_version = info_version;
        String snapshot_qualifier = null;
        // treat a snapshot version differently
        if (info_version.isSnapshot()) {
            var snapshot_timestamp = SNAPSHOT_TIMESTAMP_FORMATTER.format(moment.withZoneSameInstant(ZoneId.of("UTC")));
            ;

            // determine with build number to use
            var snapshot_build_number = 1;
            try {
                var snapshot_meta = resolver.getSnapshotMavenMetadata();
                snapshot_build_number = snapshot_meta.getSnapshotBuildNumber() + 1;
            } catch (ArtifactNotFoundException e) {
                // start the build number from the beginning
            }

            // adapt the actual version that's use by the artifacts
            snapshot_qualifier = snapshot_timestamp + "-" + snapshot_build_number;
            actual_version = info_version.withQualifier(snapshot_qualifier);

            // include version information about each artifact in this snapshot
            var metadata = new MetadataBuilder();
            for (var artifact : artifacts()) {
                metadata.snapshotVersions().add(new SnapshotVersion(artifact.classifier(), artifact.type(), actual_version.toString(), moment));
            }
            metadata.snapshotVersions().add(new SnapshotVersion(null, "pom", actual_version.toString(), moment));

            // upload snapshot metadata
            uploadStringArtifact(client, metadata
                    .info(info())
                    .updated(moment)
                    .snapshot(moment, snapshot_build_number)
                    .build(),
                info_version + "/maven-metadata.xml");
        }

        // upload artifacts
        for (var artifact : artifacts()) {
            var artifact_name = new StringBuilder(info().artifactId()).append("-").append(actual_version);
            if (!artifact.classifier().isEmpty()) {
                artifact_name.append("-").append(artifact.classifier());
            }
            var type = artifact.type();
            if (type == null) {
                type = "jar";
            }
            artifact_name.append(".").append(type);

            uploadFileArtifact(client,
                artifact.file(),
                info_version + "/" + artifact_name);
        }

        // generate and upload pom
        uploadStringArtifact(client,
            new PomBuilder().info(info()).dependencies(dependencies()).build(),
            info_version + "/" + info().artifactId() + "-" + actual_version + ".pom");

        // upload metadata
        uploadStringArtifact(client,
            new MetadataBuilder()
                .info(info())
                .updated(moment)
                .otherVersions(current_versions)
                .build(),
            "maven-metadata.xml");
    }

    private void uploadStringArtifact(HttpClient client, String content, String path)
    throws UploadException {
        try {
            uploadArtifact(client, BodyPublishers.ofString(content), path);
            uploadArtifact(client, BodyPublishers.ofString(generateHash(content, "MD5")), path + ".md5");
            uploadArtifact(client, BodyPublishers.ofString(generateHash(content, "SHA-1")), path + ".sha1");
            uploadArtifact(client, BodyPublishers.ofString(generateHash(content, "SHA-256")), path + ".sha256");
            uploadArtifact(client, BodyPublishers.ofString(generateHash(content, "SHA-512")), path + ".sha512");
        } catch (NoSuchAlgorithmException e) {
            throw new UploadException(path, e);
        }
    }

    private void uploadFileArtifact(HttpClient client, File file, String path)
    throws UploadException {
        try {
            var digest_md5 = MessageDigest.getInstance("MD5");
            var digest_sha1 = MessageDigest.getInstance("SHA-1");
            var digest_sha256 = MessageDigest.getInstance("SHA-256");
            var digest_sha512 = MessageDigest.getInstance("SHA-512");

            try (var is = Files.newInputStream(file.toPath())) {
                var buffer = new byte[1024];
                var return_value = -1;
                while (-1 != (return_value = is.read(buffer))) {
                    digest_md5.update(buffer, 0, return_value);
                    digest_sha1.update(buffer, 0, return_value);
                    digest_sha256.update(buffer, 0, return_value);
                    digest_sha512.update(buffer, 0, return_value);
                }

                uploadArtifact(client, BodyPublishers.ofFile(file.toPath()), path);
                uploadArtifact(client, BodyPublishers.ofString(encodeHexLower(digest_md5.digest())), path + ".md5");
                uploadArtifact(client, BodyPublishers.ofString(encodeHexLower(digest_sha1.digest())), path + ".sha1");
                uploadArtifact(client, BodyPublishers.ofString(encodeHexLower(digest_sha256.digest())), path + ".sha256");
                uploadArtifact(client, BodyPublishers.ofString(encodeHexLower(digest_sha512.digest())), path + ".sha512");
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new UploadException(path, e);
        }
    }

    private static String generateHash(String content, String algorithm)
    throws NoSuchAlgorithmException {
        var digest = MessageDigest.getInstance(algorithm);
        digest.update(content.getBytes(StandardCharsets.UTF_8));
        return encodeHexLower(digest.digest());
    }

    private void uploadArtifact(HttpClient client, HttpRequest.BodyPublisher body, String path)
    throws UploadException {
        var url = repository().getArtifactUrl(info().groupId(), info().artifactId()) + path;
        System.out.print("Uploading: " + url + " ... ");
        System.out.flush();
        try {
            var builder = HttpRequest.newBuilder()
                .PUT(body)
                .uri(URI.create(url));
            if (repository().username() != null && repository().password() != null) {
                builder.header(HEADER_AUTHORIZATION, basicAuthorizationHeader(repository().username(), repository().password()));
            }
            var request = builder.build();

            HttpResponse<String> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                System.out.print("I/O error");
                throw new UploadException(url, e);
            } catch (InterruptedException e) {
                System.out.print("interrupted");
                throw new UploadException(url, e);
            }

            if (response.statusCode() >= 200 &&
                response.statusCode() < 300) {
                System.out.print("done");
            } else {
                System.out.print("failed");
                throw new UploadException(url, response.statusCode());
            }
        } finally {
            System.out.println();
        }
    }

    /**
     * Configures a publish operation from a {@link Project}.
     *
     * @param project the project to configure the publish operation from
     * @since 1.5.7
     */
    public PublishOperation fromProject(Project project) {
        repository(project.publishRepository());
        dependencies().include(project.dependencies());
        artifacts(List.of(new PublishArtifact(new File(project.buildDistDirectory(), project.jarFileName()), "", "jar")));
        var info = project.publishInfo();
        if (info != null) {
            info_ = info;
        }
        if (info_.groupId() == null) {
            info_.groupId(project.pkg());
        }
        if (info_.artifactId() == null) {
            info_.artifactId(project.name().toLowerCase());
        }
        if (info_.version() == null) {
            info_.version(project.version());
        }
        if (info_.name() == null) {
            info_.name(project.name());
        }
        return this;
    }

    /**
     * Provides the moment of publication.
     * <p>
     * If this is not provided, the publication will use the current data and time.
     *
     * @param moment the publication moment
     * @return this operation instance
     * @since 1.5.8
     */
    public PublishOperation moment(ZonedDateTime moment) {
        moment_ = moment;
        return this;
    }

    /**
     * Retrieves the moment of publication.
     *
     * @return the moment of publication; or
     * {@code null} if it wasn't provided
     * @since 1.5.8
     */
    public ZonedDateTime moment() {
        return moment_;
    }

    /**
     * Provides the repository to publish to
     *
     * @param repository the repository that the artifacts will be published to
     * @return this operation instance
     * @since 1.5.7
     */
    public PublishOperation repository(Repository repository) {
        repository_ = repository;
        return this;
    }

    /**
     * Provides scoped dependencies to reference in the publication.
     *
     * @param dependencies the dependencies that will be references in the publication
     * @return this operation instance
     * @since 1.5.7
     */
    public PublishOperation dependencies(DependencyScopes dependencies) {
        dependencies_.include(dependencies);
        return this;
    }

    /**
     * Provides the artifacts that will be published.
     *
     * @param artifacts the artifacts to publish
     * @return this operation instance
     * @since 1.5.7
     */
    public PublishOperation artifacts(List<PublishArtifact> artifacts) {
        artifacts_.addAll(artifacts);
        return this;
    }

    /**
     * Retrieves the repository that will be published to.
     *
     * @return the publishing repository
     * @since 1.5.7
     */
    public Repository repository() {
        return repository_;
    }

    /**
     * Retrieves the scoped dependencies to reference in the publication.
     * <p>
     * This is a modifiable structure that can be retrieved and changed.
     *
     * @return the scoped dependencies
     * @since 1.5.7
     */
    public DependencyScopes dependencies() {
        return dependencies_;
    }

    /**
     * Retrieves the publication info structure.
     * <p>
     * This is a modifiable structure that can be retrieved and changed.
     *
     * @return the publication info
     * @since 1.5.7
     */
    public PublishInfo info() {
        return info_;
    }

    /**
     * Retrieves the list of artifacts that will be published.
     * <p>
     * This is a modifiable list that can be retrieved and changed.
     *
     * @return the list of artifacts to publish
     * @since 1.5.7
     */
    public List<PublishArtifact> artifacts() {
        return artifacts_;
    }
}
