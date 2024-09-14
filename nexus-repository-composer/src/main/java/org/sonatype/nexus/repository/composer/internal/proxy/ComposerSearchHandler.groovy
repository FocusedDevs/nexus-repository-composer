package org.sonatype.nexus.repository.composer.internal.proxy

import org.sonatype.nexus.repository.composer.internal.ComposerJsonProcessor
import org.sonatype.nexus.repository.http.HttpResponses
import org.sonatype.nexus.repository.view.ContentTypes
import org.sonatype.nexus.repository.view.Context
import org.sonatype.nexus.repository.view.Handler
import org.sonatype.nexus.repository.view.Payload
import org.sonatype.nexus.repository.view.Response
import org.sonatype.nexus.repository.view.payloads.StringPayload

import javax.annotation.Nonnull
import javax.inject.Inject

import static com.google.common.base.Preconditions.checkNotNull

class ComposerSearchHandler implements Handler {
    public static final String DO_NOT_REWRITE = "ComposerProviderHandler.doNotRewrite";

    private final ComposerJsonProcessor composerJsonProcessor;

    @Inject
    public ComposerSearchHandler(final ComposerJsonProcessor composerJsonProcessor) {
        this.composerJsonProcessor = checkNotNull(composerJsonProcessor);
    }

    @Nonnull
    @Override
    Response handle(@Nonnull Context context) throws Exception {
        Payload searchPayload = new StringPayload(new String("{\"foo\":\"bar\"}"), ContentTypes.APPLICATION_JSON);
        return HttpResponses.ok(searchPayload);
    }
}
