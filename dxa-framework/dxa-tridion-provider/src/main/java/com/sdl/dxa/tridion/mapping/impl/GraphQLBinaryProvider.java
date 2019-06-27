package com.sdl.dxa.tridion.mapping.impl;

import com.google.common.base.Strings;
import com.google.common.primitives.Ints;
import com.rometools.utils.Lists;
import com.sdl.dxa.tridion.pcaclient.ApiClientProvider;
import com.sdl.web.pca.client.contentmodel.enums.ContentNamespace;
import com.sdl.web.pca.client.contentmodel.generated.BinaryComponent;
import com.sdl.web.pca.client.contentmodel.generated.BinaryVariant;
import com.sdl.webapp.common.api.content.ContentProvider;
import com.sdl.webapp.common.api.content.ContentProviderException;
import com.sdl.webapp.common.api.content.StaticContentItem;
import com.sdl.webapp.common.exceptions.DxaItemNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.context.WebApplicationContext;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static com.sdl.web.pca.client.contentmodel.enums.ContentNamespace.Docs;
import static com.sdl.web.pca.client.contentmodel.enums.ContentNamespace.Sites;

@Slf4j
public class GraphQLBinaryProvider {
    private static final String STATIC_FILES_DIR = "BinaryData";

    private ApiClientProvider pcaClientProvider;
    private WebApplicationContext webApplicationContext;


    public GraphQLBinaryProvider(
            ApiClientProvider pcaClientProvider, WebApplicationContext webApplicationContext) {
        this.pcaClientProvider = pcaClientProvider;
        this.webApplicationContext = webApplicationContext;
    }

    public StaticContentItem getStaticContent(
            ContentProvider provider,
            String contentNamespace,
            int binaryId,
            String localizationId,
            String localizationPath
    ) throws ContentProviderException {
        Path pathToBinaries = getPathToBinaryFiles(localizationId);
        String[] files = getFiles(contentNamespace, binaryId, localizationId, pathToBinaries);

        return processBinaryFile(provider, contentNamespace, binaryId, localizationId, localizationPath, files);
    }

    FilenameFilter getFilenameFilter(String nameSpace, int binaryId, String localizationId) {
        return (path, name) -> name.matches(".*_" + nameSpace + localizationId + "-" + binaryId + "\\D.*");
    }

    @NotNull
    Path getPathToBinaryFiles(String localizationId) {
        String parentPath = StringUtils.join(new String[]{getBasePath(), STATIC_FILES_DIR, localizationId, "media"}, File.separator);
        return Paths.get(parentPath);
    }

    StaticContentItem processBinaryFile(ContentProvider provider,
                                        String contentNamespace,
                                        int binaryId,
                                        String localizationId,
                                        String localizationPath,
                                        String[] files) throws ContentProviderException {
        if (files == null || files.length <= 0) {
            return downloadBinary(provider, contentNamespace, binaryId, localizationId, localizationPath);
        }
        if (files.length > 1) {
            log.warn("There are more than 1 file with binaryId: {} for localizationId: {} found, see {}. Taking 1st one...", binaryId, localizationId, Arrays.toString(files));
        }
        return provider.getStaticContent(contentNamespace, files[0], localizationId, localizationPath);
    }

    StaticContentItem downloadBinary(ContentProvider provider,
                                     String contentNamespace,
                                     int binaryId,
                                     String localizationId,
                                     String localizationPath) throws ContentProviderException {
        log.debug("There is no binary file with binaryId: {} for localizationId: {} on FS. Trying to download it in namespace: {}", binaryId, localizationId, contentNamespace);
        BinaryComponent binaryComponent = pcaClientProvider.getClient().getBinaryComponent(getContentNamespace(contentNamespace),
                Ints.tryParse(localizationId),
                binaryId,
                null,
                null);
        if (binaryComponent == null) {
            throw new DxaItemNotFoundException("There is no binary with binaryId: " + binaryId + " for publication: " + localizationId);
        }
        try {
            if (binaryComponent.getVariants() == null) {
                log.error("Unable to get binary data (Variants null) for binary component with id: {} in namespace: {}", binaryComponent.getId(), contentNamespace);
                return null;
            }
            if (Lists.isEmpty(binaryComponent.getVariants().getEdges())) {
                log.error("Unable to get binary data (Edges null) for binary component with id: {} in namespace: {}", binaryComponent.getId(), contentNamespace);
                return null;
            }
            BinaryVariant variant = binaryComponent.getVariants().getEdges().get(0).getNode();
            if (Strings.isNullOrEmpty(variant.getDownloadUrl())) {
                log.error("Binary variant download Url is missing for binary component with id: {} in namespace: {}", binaryComponent.getId(), contentNamespace);
                return null;
            }
            log.debug("Attempting to get binary content for {} from binary component with id: {} in namespace: {}", variant.getDownloadUrl(), binaryComponent.getId(), contentNamespace);
            return provider.getStaticContent(contentNamespace, variant.getPath(), localizationId, localizationPath);
        }
        catch(Exception ex) {
            String message = "Could not get binary file with binaryId: " + binaryId + " for localizationId: " + localizationId + " in namespace: " + contentNamespace;
            throw new ContentProviderException(message, ex);
        }
    }

    @NotNull
    private ContentNamespace getContentNamespace(String contentNamespace) {
        switch (contentNamespace) {
            case "ish":
                return Docs;
            case "tcm":
                return Sites;
        }
        return null;
    }

    @NotNull
    String getBasePath() {
        String basePath = getAppRealPath();
        if (basePath.endsWith(File.separator)) {
            basePath = basePath.substring(0, basePath.length()-1);
        }
        return basePath;
    }

    String[] getFiles(String contentNamespace, int binaryId, String localizationId, Path pathToBinaries) {
        return pathToBinaries.toFile().list(getFilenameFilter(contentNamespace, binaryId, localizationId));
    }

    String getAppRealPath() {
        return webApplicationContext.getServletContext().getRealPath("/");
    }
}
