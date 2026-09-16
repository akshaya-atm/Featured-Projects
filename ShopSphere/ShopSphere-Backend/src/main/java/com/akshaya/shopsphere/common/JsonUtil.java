package com.akshaya.shopsphere.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

// Single source of truth for building the app's Jackson ObjectMapper, so date/time fields
// serialize as ISO-8601 strings the frontend's `new Date(...)` can parse.
public final class JsonUtil {

    private JsonUtil() {
    }

    public static ObjectMapper newObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
