package com.sdl.dxa.tridion.content;

import com.google.common.primitives.Ints;
import com.sdl.dxa.common.dto.StaticContentRequestDto;
import com.sdl.dxa.tridion.pcaclient.ApiClientProvider;
import com.sdl.dxa.tridion.pcaclient.GraphQLUtils;
import com.sdl.web.pca.client.contentmodel.ContextData;
import com.sdl.web.pca.client.contentmodel.generated.BinaryComponent;
import com.sdl.web.pca.client.contentmodel.generated.BinaryVariant;
import com.sdl.web.pca.client.contentmodel.generated.BinaryVariantEdge;
import com.sdl.web.pca.client.contentmodel.generated.Publication;
import com.sdl.webapp.common.api.content.ContentProviderException;
import com.sdl.webapp.common.api.content.StaticContentItem;
import com.sdl.webapp.common.api.content.StaticContentNotFoundException;
import com.sdl.webapp.common.exceptions.DxaItemNotFoundException;
import com.sdl.webapp.common.util.ImageUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.context.WebApplicationContext;

import java.io.File;
import java.util.List;

import static com.sdl.dxa.tridion.common.ContextDataCreator.createContextData;
import static com.sdl.webapp.common.util.FileUtils.isToBeRefreshed;

@Slf4j
@Service("graphQLStaticContentResolver")
@Profile("!cil.providers.active")
public class GraphQLStaticContentResolver extends GenericStaticContentResolver implements StaticContentResolver {

    private static final Object LOCK = new Object();

    private ApiClientProvider apiClientProvider;
    private BinaryContentDownloader contentDownloader;

    @Autowired
    public GraphQLStaticContentResolver(WebApplicationContext webApplicationContext,
                                        ApiClientProvider apiClientProvider,
                                        BinaryContentDownloader contentDownloader) {
        this.apiClientProvider = apiClientProvider;
        this.contentDownloader = contentDownloader;
        this.webApplicationContext = webApplicationContext;
    }

    @NotNull
    protected StaticContentItem createStaticContentItem(
            StaticContentRequestDto requestDto,
            File file,
            int publicationId,
            ImageUtils.StaticContentPathInfo pathInfo,
            String urlPath) throws ContentProviderException {
        BinaryComponent binaryComponent = apiClientProvider.getClient().getBinaryComponent(
                GraphQLUtils.convertUriToGraphQLContentNamespace(requestDto.getUriType()),
                publicationId,
                pathInfo.getFileName(),
                "",
                createContextData(requestDto.getClaims()));

        return this.processBinaryComponent(binaryComponent, requestDto, file, urlPath, pathInfo);
    }

    @Override
    protected @NotNull StaticContentItem getStaticContentItemById(int binaryId, StaticContentRequestDto requestDto) throws ContentProviderException {
        BinaryComponent binaryComponent = apiClientProvider.getClient().getBinaryComponent(
                GraphQLUtils.convertUriToGraphQLContentNamespace(requestDto.getUriType()),
                Ints.tryParse(requestDto.getLocalizationId()),
                binaryId,
                null,
                null);

        if (binaryComponent == null) {
            throw new DxaItemNotFoundException("Item not found");
        }
        String parentPath = getPublicationPath(requestDto.getLocalizationId());
        List<BinaryVariantEdge> edges = binaryComponent.getVariants().getEdges();
        String path = edges.get(0).getNode().getPath();

        final File file = new File(parentPath, path);
        final ImageUtils.StaticContentPathInfo pathInfo = new ImageUtils.StaticContentPathInfo(path);

        String urlPath = prependFullUrlIfNeeded(pathInfo.getFileName(), requestDto.getBaseUrl());

        return this.processBinaryComponent(binaryComponent, requestDto, file, urlPath, pathInfo);
    }

    private byte[] downloadBinary(File file, ImageUtils.StaticContentPathInfo pathInfo, BinaryComponent binaryComponent) throws ContentProviderException {
        String downloadUrl = binaryComponent.getVariants().getEdges().get(0).getNode().getDownloadUrl();
        return contentDownloader.downloadContent(file, downloadUrl);
    }

    public String resolveLocalizationPath(StaticContentRequestDto requestDto) {
        int publicationId = Integer.parseInt(requestDto.getLocalizationId());
        ContextData contextData = createContextData(requestDto.getClaims());
        Publication publication = apiClientProvider.getClient().getPublication(
                GraphQLUtils.convertUriToGraphQLContentNamespace(requestDto.getUriType()),
                publicationId,
                "",
                contextData);
        return publication.getPublicationUrl();

    };

    private boolean isVersioned(String path) {
        return (path != null) && path.contains("/system/");
    }

    private StaticContentItem processBinaryComponent(BinaryComponent binaryComponent, StaticContentRequestDto requestDto, File file, String urlPath, ImageUtils.StaticContentPathInfo pathInfo) throws ContentProviderException {
        if (binaryComponent == null) {
            throw new StaticContentNotFoundException("No binary found for pubId: [" +
                    requestDto.getLocalizationId() + "] and urlPath: " + urlPath);
        }
        if (!requestDto.isNoMediaCache()) {
            downloadBinaryWhenNeeded(binaryComponent, file, pathInfo);
        } else {
            log.debug("File cannot be cached: {}", file.getAbsolutePath());
        }
        BinaryVariant variant = binaryComponent.getVariants().getEdges().get(0).getNode();
        String binaryComponentType = variant.getType();
        String contentType = StringUtils.isEmpty(binaryComponentType) ? DEFAULT_CONTENT_TYPE : binaryComponentType;
        boolean versioned = isVersioned(variant.getPath());
        return new StaticContentItem(contentType, file, versioned);
    }

    private void downloadBinaryWhenNeeded(BinaryComponent binaryComponent, File file, ImageUtils.StaticContentPathInfo pathInfo) throws ContentProviderException {
        long componentTime = new DateTime(binaryComponent.getLastPublishDate()).getMillis();
        boolean toBeRefreshed = false;
        synchronized (LOCK) {
            toBeRefreshed = isToBeRefreshed(file, componentTime);
        }
        if (!toBeRefreshed) {
            log.debug("File does not need to be refreshed: {}", file.getAbsolutePath());
            return;
        }
        log.debug("File needs to be refreshed: {}", file.getAbsolutePath());
        byte[] content = downloadBinary(file, pathInfo, binaryComponent);
        synchronized (LOCK) {
            if (content != null) {
                refreshBinary(file, pathInfo, content);
            }
        }
    }
}
