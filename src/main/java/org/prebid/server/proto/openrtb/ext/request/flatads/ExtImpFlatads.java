package org.prebid.server.proto.openrtb.ext.request.flatads;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpFlatads {

    String token;

    @JsonProperty("publisherId")
    String publisherId;
}
