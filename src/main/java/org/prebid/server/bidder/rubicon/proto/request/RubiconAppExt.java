package org.prebid.server.bidder.rubicon.proto.request;

import lombok.Value;

@Value(staticConstructor = "of")
public class RubiconAppExt {

    RubiconSiteExtRp rp;
}
