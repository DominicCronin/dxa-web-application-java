package com.sdl.dxa.api.model.data;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Value;

@Value
@JsonTypeName
public class BinaryContentData {

    private String fileName;

    private long fileSize;

    private String mimeType;

    private String url;
}
