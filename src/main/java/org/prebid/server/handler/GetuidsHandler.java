package org.prebid.server.handler;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import lombok.Value;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.model.Endpoint;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.verticles.server.HttpEndpoint;
import org.prebid.server.vertx.verticles.server.application.ApplicationResource;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class GetuidsHandler implements ApplicationResource {

    private final UidsCookieService uidsCookieService;
    private final JacksonMapper mapper;

    public GetuidsHandler(UidsCookieService uidsCookieService, JacksonMapper mapper) {
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public List<HttpEndpoint> endpoints() {
        return Collections.singletonList(HttpEndpoint.of(HttpMethod.GET, Endpoint.getuids.value()));
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final Map<String, String> uids = uidsFrom(routingContext);
        final String body = mapper.encodeToString(BuyerUids.of(uids));

        HttpUtil.executeSafely(routingContext, Endpoint.getuids, response -> response
                .putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpUtil.APPLICATION_JSON_CONTENT_TYPE)
                .end(body));
    }

    private Map<String, String> uidsFrom(RoutingContext routingContext) {
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(routingContext);
        return uidsCookie.getCookieUids().getUids().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        // Extract just the uid for each bidder
                        uidEntry -> uidEntry.getValue().getUid()));
    }

    @Value(staticConstructor = "of")
    private static class BuyerUids {

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        Map<String, String> buyeruids;
    }
}
