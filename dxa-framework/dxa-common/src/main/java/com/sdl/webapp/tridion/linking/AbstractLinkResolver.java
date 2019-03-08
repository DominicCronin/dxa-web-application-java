package com.sdl.webapp.tridion.linking;

import com.google.common.base.Strings;
import com.sdl.dxa.common.util.PathUtils;
import com.sdl.webapp.common.api.content.LinkResolver;
import com.sdl.webapp.common.util.TcmUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;

@Slf4j
public abstract class AbstractLinkResolver implements LinkResolver {

    @Value("${dxa.web.link-resolver.remove-extension:#{true}}")
    private boolean shouldRemoveExtension;

    @Value("${dxa.web.link-resolver.strip-index-path:#{true}}")
    private boolean shouldStripIndexPath;

    @Override
    @Cacheable(value = "defaultCache", key = "{ #root.methodName,  #url, #localizationId, #resolveToBinary, #contextId }")
    public String resolveLink(@Nullable String url, @Nullable String localizationId, boolean resolveToBinary, @Nullable String contextId) {
        final int publicationId = !Strings.isNullOrEmpty(localizationId) ? Integer.parseInt(localizationId) : 0;

        String resolvedLink = _resolveLink(url, publicationId, resolveToBinary, contextId);
        String resolvedUrl = shouldStripIndexPath ? PathUtils.stripIndexPath(resolvedLink) : resolvedLink;
        return shouldRemoveExtension ? PathUtils.stripDefaultExtension(resolvedUrl) : resolvedUrl;
    }

    @Contract("null, _, _ -> null; !null, _, _ -> !null")
    private String _resolveLink(String uri, int publicationId, boolean isBinary, String contextId) {
        if (uri == null || !TcmUtils.isTcmUri(uri)) {
            return uri;
        }

        //Page ID is either tcm uri or int (in string form) -1 means no page context
        int pageId = -1;
        if (TcmUtils.isTcmUri(contextId)) {
            pageId = TcmUtils.getItemId(contextId);
        } else {
            pageId = NumberUtils.toInt(contextId, -1);
        }

        int itemId = TcmUtils.getItemId(uri);

        ResolvingData resolvingData;
        if (publicationId <= 0) {
            resolvingData = new ResolvingData(TcmUtils.getPublicationId(uri), itemId, uri, pageId);
        } else {
            resolvingData = new ResolvingData(publicationId, itemId, uri, pageId);
        }

        String result = "";
        switch (TcmUtils.getItemType(uri)) {
            case TcmUtils.COMPONENT_ITEM_TYPE:
                if (isBinary) {
                    result = resolveBinary(resolvingData);
                } else {
                    result = resolveComponent(resolvingData);
                }
                break;
            case TcmUtils.PAGE_ITEM_TYPE:
                result = resolvePage(resolvingData);
                break;
            default:
                log.warn("Could not resolve link: {}", uri);
                return "";
        }

        return result;
    }

    protected abstract String resolveComponent(ResolvingData resolvingData);

    protected abstract String resolvePage(ResolvingData resolvingData);

    protected abstract String resolveBinary(ResolvingData resolvingData);

    @AllArgsConstructor
    @Getter
    protected static class ResolvingData {

        private int publicationId;

        private int itemId;

        private String uri;

        private int pageId;
    }
}
