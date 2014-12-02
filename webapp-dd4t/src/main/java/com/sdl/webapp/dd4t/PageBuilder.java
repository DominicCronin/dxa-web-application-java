package com.sdl.webapp.dd4t;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.sdl.webapp.common.api.WebRequestContext;
import com.sdl.webapp.common.api.content.ContentProvider;
import com.sdl.webapp.common.api.content.ContentProviderException;
import com.sdl.webapp.common.api.localization.Localization;
import com.sdl.webapp.common.api.model.Entity;
import com.sdl.webapp.common.api.model.MvcData;
import com.sdl.webapp.common.api.model.Page;
import com.sdl.webapp.common.api.model.Region;
import com.sdl.webapp.common.api.model.page.PageImpl;
import com.sdl.webapp.common.api.model.region.RegionImpl;
import org.dd4t.contentmodel.*;
import org.dd4t.contentmodel.exceptions.ItemNotFoundException;
import org.dd4t.contentmodel.exceptions.SerializationException;
import org.dd4t.contentmodel.impl.BaseField;
import org.dd4t.core.resolvers.LinkResolver;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sdl.webapp.dd4t.fieldconverters.FieldUtils.getStringValue;

@Component
final class PageBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(PageBuilder.class);

    private static final String REGION_FOR_PAGE_TITLE_COMPONENT = "Main";
    private static final String STANDARD_METADATA_FIELD_NAME = "standardMeta";
    private static final String STANDARD_METADATA_TITLE_FIELD_NAME = "name";
    private static final String STANDARD_METADATA_DESCRIPTION_FIELD_NAME = "description";
    private static final String COMPONENT_PAGE_TITLE_FIELD_NAME = "headline";

    private static final String DEFAULT_AREA_NAME = "Core";

    private static final String PAGE_CONTROLLER_NAME = "Page";
    private static final String PAGE_ACTION_NAME = "Page";

    private static final String REGION_CONTROLLER_NAME = "Region";
    private static final String REGION_ACTION_NAME = "Region";

    private static final String DEFAULT_REGION_NAME = "Main";

    private static final Pattern REGION_VIEW_NAME_PATTERN = Pattern.compile(".*\\[(.*)\\]");

    private final EntityBuilder entityBuilder;

    private final LinkResolver linkResolver;

    private final WebRequestContext webRequestContext;

    @Autowired
    PageBuilder(EntityBuilder entityBuilder, LinkResolver linkResolver, WebRequestContext webRequestContext) {
        this.entityBuilder = entityBuilder;
        this.linkResolver = linkResolver;
        this.webRequestContext = webRequestContext;
    }

    Page createPage(GenericPage genericPage, Localization localization, ContentProvider contentProvider)
            throws ContentProviderException {
        final PageImpl page = new PageImpl();

        page.setId(genericPage.getId());

        // It's confusing, but what DD4T calls the "title" is what is called the "name" in the view model
        page.setName(genericPage.getTitle());

        final Map<String, String> pageMeta = new HashMap<>();
        final String title = processPageMetadata(genericPage, pageMeta, localization);
        page.setTitle(title);
        page.setMeta(pageMeta);

        String localizationPath = localization.getPath();
        if (!localizationPath.endsWith("/")) {
            localizationPath = localizationPath + "/";
        }

        // Get and add includes
        final String pageTypeId = genericPage.getPageTemplate().getId().split("-")[1];
        for (String include : localization.getIncludes(pageTypeId)) {
            final String includeUrl = localizationPath + include;
            final Page includePage = contentProvider.getPageModel(includeUrl, localization);
            page.getIncludes().put(includePage.getName(), includePage);
        }

        final Map<String, RegionImpl> regions = new LinkedHashMap<>();

        for (ComponentPresentation componentPresentation : genericPage.getComponentPresentations()) {
            final Entity entity = entityBuilder.createEntity(componentPresentation, localization);

            String regionName = getRegionName(componentPresentation);
            if (!Strings.isNullOrEmpty(regionName)) {
                RegionImpl region = regions.get(regionName);
                if (region == null) {
                    LOG.debug("Creating region: {}", regionName);
                    region = new RegionImpl();

                    region.setName(regionName);
                    region.setMvcData(createRegionMvcData(componentPresentation.getComponentTemplate()));

                    regions.put(regionName, region);
                }

                region.addEntity(entity);
            }
        }

        final Map<String, Region> regionMap = new LinkedHashMap<>();
        regionMap.putAll(regions);
        page.setRegions(regionMap);

        page.setPageData(createPageData(genericPage, localization));
        page.setMvcData(createPageMvcData(genericPage.getPageTemplate()));

        return page;
    }

    private String processPageMetadata(GenericPage page, Map<String, String> pageMeta, Localization localization) {
        // Process page metadata fields
        if (page.getMetadata() != null) {
            for (Field field : page.getMetadata().values()) {
                processMetadataField(field, pageMeta);
            }
        }

        String description = pageMeta.get("description");
        String title = pageMeta.get("title");
        String image = pageMeta.get("image");

        if (Strings.isNullOrEmpty(title) || Strings.isNullOrEmpty(description)) {
            for (ComponentPresentation cp : page.getComponentPresentations()) {
                if (REGION_FOR_PAGE_TITLE_COMPONENT.equals(getRegionName(cp))) {
                    final GenericComponent component = cp.getComponent();

                    final Map<String, Field> metadata = component.getMetadata();
                    BaseField standardMetaField = (BaseField) metadata.get(STANDARD_METADATA_FIELD_NAME);
                    if (standardMetaField != null && !standardMetaField.getEmbeddedValues().isEmpty()) {
                        final Map<String, Field> standardMeta = standardMetaField.getEmbeddedValues().get(0).getContent();
                        if (Strings.isNullOrEmpty(title) && standardMeta.containsKey(STANDARD_METADATA_TITLE_FIELD_NAME)) {
                            title = standardMeta.get(STANDARD_METADATA_TITLE_FIELD_NAME).getValues().get(0).toString();
                        }
                        if (Strings.isNullOrEmpty(description) && standardMeta.containsKey(STANDARD_METADATA_DESCRIPTION_FIELD_NAME)) {
                            description = standardMeta.get(STANDARD_METADATA_DESCRIPTION_FIELD_NAME).getValues().get(0).toString();
                        }
                    }

                    final Map<String, Field> content = component.getContent();
                    if (Strings.isNullOrEmpty(title) && content.containsKey(COMPONENT_PAGE_TITLE_FIELD_NAME)) {
                        title = content.get(COMPONENT_PAGE_TITLE_FIELD_NAME).getValues().get(0).toString();
                    }

                    if (Strings.isNullOrEmpty(image) && content.containsKey("image")) {
                        image = ((GenericComponent) ((BaseField) content.get("image")).getLinkedComponentValues().get(0))
                                .getMultimedia().getUrl();
                    }

                    break;
                }
            }
        }

        // Use page title if no title found
        if (Strings.isNullOrEmpty(title)) {
            title = page.getTitle().replace("^\\d(3)\\s", "");
            if (title.equalsIgnoreCase("index") || title.equalsIgnoreCase("default")) {
                // Use default page title from configuration if nothing better was found
                title = localization.getResource("core.defaultPageTitle");
            }
        }

        pageMeta.put("twitter:card", "summary");
        pageMeta.put("og:title", title);
        pageMeta.put("og:url", webRequestContext.getFullUrl());
        pageMeta.put("og:type", "article");
        pageMeta.put("og:locale", localization.getCulture());

        if (!Strings.isNullOrEmpty(description)) {
            pageMeta.put("og:description", description);
        }

        if (!Strings.isNullOrEmpty(image)) {
            pageMeta.put("og:image", webRequestContext.getBaseUrl() + webRequestContext.getContextPath() + image);
        }

        if (!pageMeta.containsKey("description")) {
            pageMeta.put("description", !Strings.isNullOrEmpty(description) ? description : title);
        }

        return title + " " + localization.getResource("core.pageTitleSeparator") + " " +
                localization.getResource("core.pageTitlePostfix");
    }

    private void processMetadataField(Field field, Map<String, String> pageMeta) {
        // If it's an embedded field, then process the subfields
        if (field.getFieldType() == FieldType.Embedded) {
            final List<FieldSet> embeddedValues = ((BaseField) field).getEmbeddedValues();
            if (embeddedValues != null && !embeddedValues.isEmpty()) {
                for (Field subfield : embeddedValues.get(0).getContent().values()) {
                    processMetadataField(subfield, pageMeta);
                }
            }
        } else {
            final String fieldName = field.getName();

            String value;
            switch (fieldName) {
                case "internalLink":
                    final String componentId = ((BaseField) field).getTextValues().get(0);
                    try {
                        value = linkResolver.resolve(componentId);
                    } catch (SerializationException | ItemNotFoundException e) {
                        LOG.warn("Error while resolving link: {}", componentId);
                        value = componentId;
                    }
                    break;
                case "image":
                    value = ((GenericComponent) ((BaseField) field).getLinkedComponentValues().get(0))
                            .getMultimedia().getUrl();
                    break;
                default:
                    value = Joiner.on(',').join(field.getValues());
                    break;
            }

            if (!Strings.isNullOrEmpty(value) && !pageMeta.containsKey(fieldName)) {
                pageMeta.put(fieldName, value);
            }
        }
    }

    private String getRegionName(ComponentPresentation cp) {
        final Map<String, Field> templateMeta = cp.getComponentTemplate().getMetadata();
        if (templateMeta != null) {
            String regionName = getStringValue(templateMeta, "regionView");
            if (Strings.isNullOrEmpty(regionName)) {
                regionName = DEFAULT_REGION_NAME;
            }
            return regionName;
        }

        return null;
    }

    private Map<String, String> createPageData(GenericPage page, Localization localization) {
        final PageTemplate pageTemplate = page.getPageTemplate();

        ImmutableMap.Builder<String, String> pageDataBuilder = ImmutableMap.builder();
        pageDataBuilder.put("PageID", page.getId());
        pageDataBuilder.put("PageModified", ISODateTimeFormat.dateHourMinuteSecond().print(page.getRevisionDate()));
        pageDataBuilder.put("PageTemplateID", pageTemplate.getId());
        pageDataBuilder.put("PageTemplateModified",
                ISODateTimeFormat.dateHourMinuteSecond().print(pageTemplate.getRevisionDate()));

        pageDataBuilder.put("CmsUrl", localization.getConfiguration("core.cmsurl"));

        return pageDataBuilder.build();
    }

    private MvcData createPageMvcData(PageTemplate pageTemplate) {
        final MvcDataImpl mvcData = new MvcDataImpl();

        mvcData.setControllerAreaName(DEFAULT_AREA_NAME);
        mvcData.setControllerName(PAGE_CONTROLLER_NAME);
        mvcData.setActionName(PAGE_ACTION_NAME);

        final String[] viewNameParts = getPageViewNameParts(pageTemplate);
        mvcData.setAreaName(viewNameParts[0]);
        mvcData.setViewName(viewNameParts[1]);

        return mvcData;
    }

    private MvcData createRegionMvcData(ComponentTemplate componentTemplate) {
        final MvcDataImpl mvcData = new MvcDataImpl();

        mvcData.setControllerAreaName(DEFAULT_AREA_NAME);
        mvcData.setControllerName(REGION_CONTROLLER_NAME);
        mvcData.setActionName(REGION_ACTION_NAME);

        final String[] viewNameParts = getRegionViewNameParts(componentTemplate);
        mvcData.setAreaName(viewNameParts[0]);
        mvcData.setViewName(viewNameParts[1]);

        return mvcData;
    }

    private String[] getPageViewNameParts(PageTemplate pageTemplate) {
        String fullName = getStringValue(pageTemplate.getMetadata(), "view");
        if (Strings.isNullOrEmpty(fullName)) {
            fullName = pageTemplate.getTitle().replaceAll(" ", "");
        }
        return splitName(fullName);
    }

    private String[] getRegionViewNameParts(ComponentTemplate componentTemplate) {
        String fullName = getStringValue(componentTemplate.getMetadata(), "regionView");
        if (Strings.isNullOrEmpty(fullName)) {
            final Matcher matcher = REGION_VIEW_NAME_PATTERN.matcher(componentTemplate.getTitle());
            fullName = matcher.matches() ? matcher.group(1) : DEFAULT_REGION_NAME;
        }
        return splitName(fullName);
    }

    private String[] splitName(String name) {
        final String[] parts = name.split(":");
        return parts.length > 1 ? parts : new String[] { DEFAULT_AREA_NAME, name };
    }
}
