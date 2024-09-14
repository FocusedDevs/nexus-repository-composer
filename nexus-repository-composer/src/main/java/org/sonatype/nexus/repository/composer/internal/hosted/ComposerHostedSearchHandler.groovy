package org.sonatype.nexus.repository.composer.internal.hosted

import org.sonatype.nexus.repository.Repository
import org.sonatype.nexus.repository.composer.ComposerContentFacet
import org.sonatype.nexus.repository.composer.internal.ComposerJsonProcessor
import org.sonatype.nexus.repository.content.fluent.FluentComponents
import org.sonatype.nexus.repository.http.HttpResponses
import org.sonatype.nexus.repository.view.Content
import org.sonatype.nexus.repository.view.ContentTypes
import org.sonatype.nexus.repository.view.Context
import org.sonatype.nexus.repository.view.Handler
import org.sonatype.nexus.repository.view.Response
import org.sonatype.nexus.repository.view.payloads.StringPayload

import javax.annotation.Nonnull
import javax.inject.Inject

import static com.google.common.base.Preconditions.checkNotNull

class ComposerHostedSearchHandler implements Handler {

    public static final String DO_NOT_REWRITE = "ComposerProviderHandler.doNotRewrite";

    private final ComposerJsonProcessor composerJsonProcessor;

    @Inject
    public ComposerHostedSearchHandler(final ComposerJsonProcessor composerJsonProcessor) {
        this.composerJsonProcessor = checkNotNull(composerJsonProcessor);
    }

    @Nonnull
    @Override
    Response handle(@Nonnull Context context) throws Exception {
        Repository currentRepository = context.getRepository();
        FluentComponents comps = currentRepository.facet(ComposerContentFacet.class).components();
        Content content = composerJsonProcessor.generateSearchFromComponents(currentRepository, comps);

        return HttpResponses.ok(content);
    }

}
