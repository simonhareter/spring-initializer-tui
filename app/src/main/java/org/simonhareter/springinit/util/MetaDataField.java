package org.simonhareter.springinit.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record MetaDataField(
        String type,
        @JsonProperty("default") String defaultValue,
        List<MetaDataOption> values) {
}
