/*
 * Copyright (c) Bosch Software Innovations GmbH 2016-2019.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.antenna.maven;

import org.eclipse.sw360.antenna.exceptions.FailedToDownloadException;
import org.eclipse.sw360.antenna.model.artifact.facts.java.MavenCoordinates;
import org.eclipse.sw360.antenna.util.HttpHelper;
import org.eclipse.sw360.antenna.util.ProxySettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Requests jar files for artifacts by making HTTP requests.
 */
public class HttpRequester extends IArtifactRequester {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequester.class);
    private static final String GROUP_ID_PLACEHOLDER = "{groupId}";
    private static final String ARTIFACT_ID_PLACEHOLDER = "{artifactId}";
    private static final String VERSION_PLACEHOLDER = "{version}";
    private static final String MAVEN_CENTRAL_URL = "http://repo2.maven.org/maven2/" + GROUP_ID_PLACEHOLDER + "/" + ARTIFACT_ID_PLACEHOLDER + "/" + VERSION_PLACEHOLDER + "/";

    private HttpHelper httpHelper;
    private Optional<URL> sourceRepositoryUrl;

    public HttpRequester(ProxySettings proxySettings, URL sourceRepositoryUrl) {
        super();
        httpHelper = new HttpHelper(proxySettings);
        this.sourceRepositoryUrl = Optional.of(sourceRepositoryUrl);
    }

    public HttpRequester(ProxySettings proxySettings) {
        super();
        httpHelper = new HttpHelper(proxySettings);
        this.sourceRepositoryUrl = Optional.empty();
    }

    @Override
    public Optional<File> requestFile(MavenCoordinates coordinates, Path targetDirectory, ClassifierInformation classifierInformation) {
        String jarBaseName = getExpectedJarBaseName(coordinates, classifierInformation);
        File localJarFile = targetDirectory.resolve(jarBaseName).toFile();

        if (localJarFile.exists()) {
            LOGGER.info("The file " + localJarFile + " already exists and won't be downloaded again");
            return Optional.of(localJarFile);
        }

        Optional<File> downloadedFile = downloadFileFromUserUrl(coordinates, targetDirectory, jarBaseName);

        if (!downloadedFile.isPresent()) {
            String mavenCentralJarUrl = getJarUrl(coordinates, jarBaseName, MAVEN_CENTRAL_URL);
            return tryFileDownload(mavenCentralJarUrl, targetDirectory, jarBaseName);
        }
        return downloadedFile;
    }

    private Optional<File> downloadFileFromUserUrl(MavenCoordinates coordinates, Path targetDirectory, String jarBaseName) {
        if (sourceRepositoryUrl.isPresent()) {
            String jarUrl = convertToJarUrlTemplate(coordinates, jarBaseName, sourceRepositoryUrl.get().toString());
            return tryFileDownload(jarUrl, targetDirectory, jarBaseName);
        }
        return Optional.empty();
    }

    private String convertToJarUrlTemplate(MavenCoordinates coordinates, String jarBaseName, String repoTemplate) {
        String enrichedTemplate = repoTemplate;
        enrichedTemplate += repoTemplate.endsWith("/") ? "" : "/";
        enrichedTemplate += GROUP_ID_PLACEHOLDER + "/" + ARTIFACT_ID_PLACEHOLDER + "/" + VERSION_PLACEHOLDER + "/";
        return getJarUrl(coordinates, jarBaseName, enrichedTemplate);
    }

    private Optional<File> tryFileDownload(String jarUrl, Path targetDirectory, String jarBaseName) {
        try {
            LOGGER.info("HttpRequester with download path " + jarUrl);
            return Optional.ofNullable(httpHelper.downloadFile(jarUrl, targetDirectory, jarBaseName));
        } catch (FailedToDownloadException | IOException e) {
            LOGGER.warn("Failed to find jar: " + e.getMessage());
            return Optional.empty();
        }
    }

    private String getJarUrl(MavenCoordinates coordinates, String remoteFileName, String repoTemplate) {
        // Construct URL (substitute in groupID, artifactID and version
        // NOTE: There should be no dots in the groupID. Dots delimit
        // directories, so are converted to slashes.
        String repo = repoTemplate
                .replace(GROUP_ID_PLACEHOLDER, coordinates.getGroupId()
                        .replace('.', '/'))
                .replace(ARTIFACT_ID_PLACEHOLDER, coordinates.getArtifactId())
                .replace(VERSION_PLACEHOLDER, coordinates.getVersion());

        return repo + remoteFileName;
    }
}
