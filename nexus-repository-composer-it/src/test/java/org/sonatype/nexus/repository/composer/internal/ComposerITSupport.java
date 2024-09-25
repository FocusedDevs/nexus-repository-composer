/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2018-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.repository.composer.internal;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.junit.Rule;
import java.io.IOException;
import java.net.URL;

import javax.annotation.Nonnull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.junit.After;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.sonatype.goodies.httpfixture.server.fluent.Behaviours;
import org.sonatype.goodies.httpfixture.server.fluent.Server;
import org.sonatype.nexus.pax.exam.NexusPaxExamSupport;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.composer.internal.fixtures.RepositoryRuleComposer;
import org.sonatype.nexus.repository.content.facet.ContentFacet;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.manager.RepositoryManager;
import org.sonatype.nexus.testsuite.testsupport.NexusBaseITSupport;
import org.sonatype.nexus.repository.http.HttpStatus;
import org.sonatype.nexus.testsuite.testsupport.NexusITSupport;
import javax.inject.Inject;
import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.sonatype.nexus.testsuite.testsupport.FormatClientSupport.bytes;
import static org.sonatype.nexus.testsuite.testsupport.FormatClientSupport.status;
import static org.testcontainers.shaded.org.hamcrest.text.MatchesPattern.matchesPattern;

public class ComposerITSupport
    extends NexusBaseITSupport
{

  @Inject
  protected RepositoryManager repositoryManager;

  protected static final String ZIPBALL_FILE_NAME = "rjkip-ftp-php-v1.1.0.zip";

  protected static final String NAME_VENDOR = "rjkip";

  protected static final String NAME_PROJECT = "ftp-php";

  protected static final String NAME_VERSION = "v1.1.0";

  protected static final String NAME_PACKAGES = "packages";

  protected static final String EXTENSION_ZIP = ".zip";

  protected static final String VALID_ZIPBALL_BASE_URL = NAME_VENDOR + "/" + NAME_PROJECT + "/" + NAME_VERSION;

  protected static final String FILE_ZIPBALL = NAME_VENDOR + "-" + NAME_PROJECT + "-" + NAME_VERSION + EXTENSION_ZIP;

  protected static final String VALID_ZIPBALL_URL = VALID_ZIPBALL_BASE_URL + "/" + FILE_ZIPBALL;

  protected static final String NAME_LIST = "list";

  protected static final String EXTENSION_JSON = ".json";

  protected static final String FILE_PROVIDER = NAME_PROJECT + EXTENSION_JSON;

  protected static final String FILE_PACKAGES = NAME_PACKAGES + EXTENSION_JSON;

  protected static final String FILE_LIST = NAME_LIST + EXTENSION_JSON;

  protected static final String PACKAGE_BASE_PATH = "p/" + NAME_VENDOR + "/";

  protected static final String LIST_BASE_PATH = "packages/";

  protected static final String VALID_PROVIDER_URL = PACKAGE_BASE_PATH + FILE_PROVIDER;

  protected static final String VALID_LIST_URL = LIST_BASE_PATH + FILE_LIST;

  protected static final String MIME_TYPE_JSON = "application/json";

  protected static final String MIME_TYPE_ZIP = "application/zip";
  protected static final String BAD_PATH = "/this/path/is/not/valid";

  @Rule
  public RepositoryRuleComposer repos = new RepositoryRuleComposer(() -> repositoryManager);
  protected Server server;

  public ComposerITSupport() {
    testData.addDirectory(NexusPaxExamSupport.resolveBaseFile("target/it-resources/composer"));
  }

  @Nonnull
  protected ComposerClient composerClient(final Repository repository) throws Exception {
    checkNotNull(repository);

    return composerClient(repositoryBaseUrl(repository));
  }

  protected ComposerClient composerClient(final URL repositoryUrl) throws Exception {
    return new ComposerClient(
        clientBuilder(repositoryUrl).build(),
        clientContext(),
        repositoryUrl.toURI()
    );
  }

  @Nonnull
  protected URL repositoryBaseUrl(final Repository repository) {
    return resolveUrl(nexusUrl(), "/repository/" + repository.getName() + "/");
  }

  protected HttpClientBuilder clientBuilder(final URL nexusUrl) throws Exception {
    AuthScope scope = new AuthScope(nexusUrl.getHost(), -1);
    CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(scope, new UsernamePasswordCredentials("admin", "admin123"));

    KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
    try (FileInputStream instream = new FileInputStream(resolveBaseFile("target/it-resources/ssl/client.jks"))) {
      trustStore.load(instream, "password".toCharArray());
    }
    SSLContext context = SSLContexts.custom().loadTrustMaterial(trustStore, new TrustSelfSignedStrategy()).build();

    return HttpClients.custom()
        .setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.DEFAULT).build())
        .setDefaultCredentialsProvider(credentialsProvider)
        .setSSLSocketFactory(new SSLConnectionSocketFactory(context, NoopHostnameVerifier.INSTANCE));
  }

  /**
   * @return Context with preemptive auth enabled for Nexus
   */
  protected HttpClientContext clientContext() {
    String hostname = nexusUrl().getHost();
    AuthCache authCache = new BasicAuthCache();
    HttpHost hostHttp = new HttpHost(hostname, nexusUrl().getPort(), "http");
    HttpHost hostHttps = new HttpHost(hostname, nexusUrl().getPort(), "https");
    authCache.put(hostHttp, new BasicScheme());
    authCache.put(hostHttps, new BasicScheme());

    HttpClientContext context = HttpClientContext.create();
    context.setAuthCache(authCache);

    return context;
  }

  public static Optional<FluentAsset> findAsset(Repository repository, String path) {
    if (!path.startsWith("/")) {
      path = "/" + path;
    }
    return repository.facet(ContentFacet.class).assets().path(path).find();
  }

  @Configuration
  public static Option[] configureNexus() {
    return NexusPaxExamSupport.options(
        NexusITSupport.configureNexusBase(),
        nexusFeature("org.sonatype.nexus.plugins", "nexus-repository-composer")
    );
  }

  protected void startServer() throws Exception {
    server = Server.withPort(0)
        .serve("/" + FILE_PACKAGES)
        .withBehaviours(Behaviours.file(testData.resolveFile(FILE_PACKAGES)))
        .serve("/" + VALID_LIST_URL)
        .withBehaviours(Behaviours.file(testData.resolveFile(FILE_LIST)))
        .serve("/" + VALID_PROVIDER_URL)
        .withBehaviours(Behaviours.file(testData.resolveFile(FILE_PROVIDER)))
        .serve("/" + VALID_ZIPBALL_URL)
        .withBehaviours(Behaviours.file(testData.resolveFile(ZIPBALL_FILE_NAME)))
        .start();
  }

  @After
  public void tearDown() throws Exception {
    server.stop();
  }

  protected void getAndUpdateConfig(JsonObject expected, String repoName, String repoType, ComposerClient repoClient) throws IOException {
    // when
    CloseableHttpResponse response = repoClient.get("/service/rest/v1/repositories/composer/"+ repoType + "/" + repoName);
    String config = new String(bytes(response));
    JsonObject jsonConfig = (JsonObject) new JsonParser().parse(config);

    // then
    assertThat(status(response), is(HttpStatus.OK));
    // check url field matches http://localhost:[0-9]*/repository/composer-test-<name>
    assertThat(matchesPattern("http://localhost:[0-9]*/repository/" + repoName).matches(jsonConfig.get("url").getAsString()), is(true));
    // compare ignoring url field
    jsonConfig.remove("url");
    assertThat(jsonConfig, is(expected));

    // set online to false, remove optional connection properties, then update repository
    jsonConfig.remove("online");
    jsonConfig.remove("connection");
    jsonConfig.addProperty("online", false);
    int code = repoClient.put("/service/rest/v1/repositories/composer/"+ repoType + "/" + repoName, jsonConfig.toString());
    assertThat(code, is(HttpStatus.NO_CONTENT));
  }
}
