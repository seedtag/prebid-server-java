package org.prebid.server.hooks.modules.ortb2.blocking.v1;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import io.vertx.core.Future;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.auction.versionconverter.OrtbVersion;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderInfo;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.execution.v1.bidder.BidderRequestPayloadImpl;
import org.prebid.server.hooks.modules.ortb2.blocking.core.config.ArrayOverride;
import org.prebid.server.hooks.modules.ortb2.blocking.core.config.Attribute;
import org.prebid.server.hooks.modules.ortb2.blocking.core.config.AttributeActionOverrides;
import org.prebid.server.hooks.modules.ortb2.blocking.core.config.Attributes;
import org.prebid.server.hooks.modules.ortb2.blocking.core.config.Conditions;
import org.prebid.server.hooks.modules.ortb2.blocking.core.config.ModuleConfig;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.BlockedAttributes;
import org.prebid.server.hooks.modules.ortb2.blocking.model.ModuleContext;
import org.prebid.server.hooks.modules.ortb2.blocking.v1.model.BidderInvocationContextImpl;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.bidder.BidderRequestPayload;
import org.prebid.server.spring.config.bidder.model.Ortb;

import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class Ortb2BlockingBidderRequestHookTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Mock(strictness = Mock.Strictness.LENIENT)
    private BidderCatalog bidderCatalog;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private BidRejectionTracker bidRejectionTracker;

    private Ortb2BlockingBidderRequestHook hook;

    @BeforeEach
    public void setUp() {
        given(bidderCatalog.bidderInfoByName(anyString()))
                .willReturn(bidderInfo(OrtbVersion.ORTB_2_5));

        hook = new Ortb2BlockingBidderRequestHook(bidderCatalog);
    }

    @Test
    public void shouldReturnResultWithNoActionWhenNoBlockingAttributes() {
        // given
        given(bidderCatalog.bidderInfoByName(anyString()))
                .willReturn(bidderInfo(OrtbVersion.ORTB_2_6));
        given(bidderCatalog.bidderInfoByName(eq("bidder1Base")))
                .willReturn(bidderInfo(OrtbVersion.ORTB_2_5));

        // when
        final Future<InvocationResult<BidderRequestPayload>> result = hook.call(
                BidderRequestPayloadImpl.of(emptyRequest()),
                BidderInvocationContextImpl.of(
                        "bidder1",
                        Map.of("bidder1", "bidder1Base"),
                        bidRejectionTracker,
                        null,
                        true));

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(InvocationResultImpl.builder()
                .status(InvocationStatus.success)
                .action(InvocationAction.no_action)
                .moduleContext(ModuleContext.create().with("bidder1", OrtbVersion.ORTB_2_5))
                .build());
    }

    @Test
    public void shouldReturnResultWithNoActionAndErrorWhenInvalidAccountConfig() {
        // given
        final ObjectNode accountConfig = MAPPER.createObjectNode()
                .put("attributes", 1);

        // when
        final Future<InvocationResult<BidderRequestPayload>> result = hook.call(
                BidderRequestPayloadImpl.of(emptyRequest()),
                BidderInvocationContextImpl.of("bidder1", bidRejectionTracker, accountConfig, true));

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(InvocationResultImpl.builder()
                .status(InvocationStatus.success)
                .action(InvocationAction.no_action)
                .moduleContext(ModuleContext.create().with("bidder1", OrtbVersion.ORTB_2_5))
                .errors(singletonList("attributes field in account configuration is not an object"))
                .build());
    }

    @Test
    public void shouldReturnResultWithNoActionAndNoErrorWhenInvalidAccountConfigAndDebugDisabled() {
        // given
        final ObjectNode accountConfig = MAPPER.createObjectNode()
                .put("attributes", 1);

        // when
        final Future<InvocationResult<BidderRequestPayload>> result = hook.call(
                BidderRequestPayloadImpl.of(emptyRequest()),
                BidderInvocationContextImpl.of("bidder1", bidRejectionTracker, accountConfig, false));

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(InvocationResultImpl.builder()
                .status(InvocationStatus.success)
                .action(InvocationAction.no_action)
                .moduleContext(ModuleContext.create().with("bidder1", OrtbVersion.ORTB_2_5))
                .build());
    }

    @Test
    public void shouldReturnResultWithModuleContextAndPayloadUpdate() {
        // given
        final ObjectNode accountConfig = toObjectNode(ModuleConfig.of(Attributes.builder()
                .badv(Attribute.badvBuilder()
                        .blocked(singletonList("domain1.com"))
                        .build())
                .bcat(Attribute.bcatBuilder()
                        .blocked(singletonList("cat1"))
                        .build())
                .build()));

        // when
        final Future<InvocationResult<BidderRequestPayload>> result = hook.call(
                BidderRequestPayloadImpl.of(emptyRequest()),
                BidderInvocationContextImpl.of("bidder1", bidRejectionTracker, accountConfig, true));

        // then
        assertThat(result.succeeded()).isTrue();
        final InvocationResult<BidderRequestPayload> invocationResult = result.result();
        assertSoftly(softly -> {
            softly.assertThat(invocationResult.status()).isEqualTo(InvocationStatus.success);
            softly.assertThat(invocationResult.action()).isEqualTo(InvocationAction.update);
            softly.assertThat(invocationResult.moduleContext())
                    .isNotNull()
                    .isInstanceOf(ModuleContext.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(ModuleContext.class))
                    .satisfies(context -> {
                        assertThat(context.ortbVersionOf("bidder1")).isSameAs(OrtbVersion.ORTB_2_5);
                        assertThat(context.blockedAttributesFor("bidder1"))
                                .isEqualTo(BlockedAttributes.builder()
                                        .badv(singletonList("domain1.com"))
                                        .bcat(singletonList("cat1"))
                                        .build());
                    });
            softly.assertThat(invocationResult.warnings()).isNull();
            softly.assertThat(invocationResult.errors()).isNull();
        });

        final PayloadUpdate<BidderRequestPayload> payloadUpdate = invocationResult.payloadUpdate();
        final BidderRequestPayloadImpl payloadToUpdate = BidderRequestPayloadImpl.of(BidRequest.builder().build());
        assertThat(payloadUpdate.apply(payloadToUpdate)).isEqualTo(BidderRequestPayloadImpl.of(
                BidRequest.builder()
                        .badv(singletonList("domain1.com"))
                        .bcat(singletonList("cat1"))
                        .build()));
    }

    @Test
    public void shouldReturnResultWithUpdateActionAndWarning() {
        // given
        final ObjectNode accountConfig = toObjectNode(ModuleConfig.of(Attributes.builder()
                .badv(Attribute.badvBuilder()
                        .actionOverrides(AttributeActionOverrides.blocked(
                                asList(
                                        ArrayOverride.of(
                                                Conditions.of(singletonList("bidder1"), null),
                                                singletonList("domain1.com")),
                                        ArrayOverride.of(
                                                Conditions.of(singletonList("bidder1"), null),
                                                singletonList("domain2.com")))))
                        .build())
                .build()));

        // when
        final Future<InvocationResult<BidderRequestPayload>> result = hook.call(
                BidderRequestPayloadImpl.of(emptyRequest()),
                BidderInvocationContextImpl.of("bidder1", bidRejectionTracker, accountConfig, true));

        // then
        assertThat(result.succeeded()).isTrue();
        final InvocationResult<BidderRequestPayload> invocationResult = result.result();
        assertSoftly(softly -> {
            softly.assertThat(invocationResult.status()).isEqualTo(InvocationStatus.success);
            softly.assertThat(invocationResult.action()).isEqualTo(InvocationAction.update);
            softly.assertThat(invocationResult.moduleContext())
                    .asInstanceOf(InstanceOfAssertFactories.type(ModuleContext.class))
                    .satisfies(context -> assertThat(context.blockedAttributesFor("bidder1"))
                            .isEqualTo(BlockedAttributes.builder().badv(singletonList("domain1.com")).build()));
            softly.assertThat(invocationResult.warnings()).isEqualTo(singletonList(
                    "More than one conditions matches request. Bidder: bidder1, request media types: [video]"));
            softly.assertThat(invocationResult.errors()).isNull();
        });
    }

    @Test
    public void shouldReturnResultWithUpdateActionAndNoWarningWhenDebugDisabled() {
        // given
        final ObjectNode accountConfig = toObjectNode(ModuleConfig.of(Attributes.builder()
                .badv(Attribute.badvBuilder()
                        .actionOverrides(AttributeActionOverrides.blocked(
                                asList(
                                        ArrayOverride.of(
                                                Conditions.of(singletonList("bidder1"), null),
                                                singletonList("domain1.com")),
                                        ArrayOverride.of(
                                                Conditions.of(singletonList("bidder1"), null),
                                                singletonList("domain2.com")))))
                        .build())
                .build()));

        // when
        final Future<InvocationResult<BidderRequestPayload>> result = hook.call(
                BidderRequestPayloadImpl.of(emptyRequest()),
                BidderInvocationContextImpl.of("bidder1", bidRejectionTracker, accountConfig, false));

        // then
        assertThat(result.succeeded()).isTrue();
        final InvocationResult<BidderRequestPayload> invocationResult = result.result();

        assertSoftly(softly -> {
            softly.assertThat(invocationResult.status()).isEqualTo(InvocationStatus.success);
            softly.assertThat(invocationResult.action()).isEqualTo(InvocationAction.update);
            softly.assertThat(invocationResult.moduleContext())
                    .asInstanceOf(InstanceOfAssertFactories.type(ModuleContext.class))
                    .satisfies(context -> assertThat(context.blockedAttributesFor("bidder1"))
                            .isEqualTo(BlockedAttributes.builder().badv(singletonList("domain1.com")).build()));
            softly.assertThat(invocationResult.warnings()).isNull();
            softly.assertThat(invocationResult.errors()).isNull();
        });
    }

    private static BidderInfo bidderInfo(OrtbVersion ortbVersion) {
        return BidderInfo.create(
                true,
                ortbVersion,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                false,
                false,
                null,
                Ortb.of(false),
                0L);
    }

    private static BidRequest emptyRequest() {
        return BidRequest.builder()
                .imp(singletonList(Imp.builder().video(Video.builder().build()).build()))
                .build();
    }

    private static ObjectNode toObjectNode(ModuleConfig config) {
        return MAPPER.valueToTree(config);
    }
}
