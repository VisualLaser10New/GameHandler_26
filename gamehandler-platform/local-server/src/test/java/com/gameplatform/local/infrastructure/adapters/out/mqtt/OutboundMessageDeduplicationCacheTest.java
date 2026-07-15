package com.gameplatform.local.infrastructure.adapters.out.mqtt;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OutboundMessageDeduplicationCacheTest {

    @Test
    void isRecentOutboundReturnsFalseForTopicsNeverPublished() {
        OutboundMessageDeduplicationCache cache = new OutboundMessageDeduplicationCache();
        assertThat(cache.isRecentOutbound("building/b1/game/g1/state", "{}".getBytes(StandardCharsets.UTF_8)))
                .isFalse();
    }

    @Test
    void recordOutboundMakesSameTopicAndBytesRecognised() {
        OutboundMessageDeduplicationCache cache = new OutboundMessageDeduplicationCache();
        String topic = "building/b1/game/g1/session/lobby/create";
        byte[] payload = "{\"sessionId\":\"s1\",\"gameType\":\"CHESS\",\"participants\":[\"u1\"]}"
                .getBytes(StandardCharsets.UTF_8);

        cache.recordOutbound(topic, payload);

        assertThat(cache.isRecentOutbound(topic, payload)).isTrue();
    }

    @Test
    void isRecentOutboundReturnsFalseWhenTopicDiffers() {
        OutboundMessageDeduplicationCache cache = new OutboundMessageDeduplicationCache();
        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);

        cache.recordOutbound("building/b1/game/g1/state", payload);

        assertThat(cache.isRecentOutbound("building/b1/game/g2/state", payload)).isFalse();
    }

    @Test
    void isRecentOutboundReturnsFalseWhenBytesDiffer() {
        OutboundMessageDeduplicationCache cache = new OutboundMessageDeduplicationCache();
        String topic = "building/b1/game/g1/session/lobby/create";

        cache.recordOutbound(topic, "payload-A".getBytes(StandardCharsets.UTF_8));

        assertThat(cache.isRecentOutbound(topic, "payload-B".getBytes(StandardCharsets.UTF_8))).isFalse();
    }

    @Test
    void singleByteDifferenceIsNotRecognised() {
        OutboundMessageDeduplicationCache cache = new OutboundMessageDeduplicationCache();
        String topic = "building/b1/game/g1/state";
        byte[] a = new byte[]{1, 2, 3};
        byte[] b = new byte[]{1, 2, 4};

        cache.recordOutbound(topic, a);

        assertThat(cache.isRecentOutbound(topic, b)).isFalse();
    }

    @Test
    void expiresAfterTtl() throws InterruptedException {
        OutboundMessageDeduplicationCache cache = new OutboundMessageDeduplicationCache();
        String topic = "building/b1/game/g1/state";
        byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);

        cache.recordOutbound(topic, payload);
        assertThat(cache.isRecentOutbound(topic, payload)).isTrue();

        Thread.sleep(OutboundMessageDeduplicationCache.TTL_MS + 50);

        assertThat(cache.isRecentOutbound(topic, payload)).isFalse();
    }

    @Test
    void recordOutboundTwiceKeepsTheLatestExpiry() {
        OutboundMessageDeduplicationCache cache = new OutboundMessageDeduplicationCache();
        String topic = "building/b1/game/g1/state";
        byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);

        cache.recordOutbound(topic, payload);
        cache.recordOutbound(topic, payload);
        cache.recordOutbound(topic, payload);

        assertThat(cache.isRecentOutbound(topic, payload)).isTrue();
    }

    @Test
    void nullTopicAndBytesAreSafeNoOps() {
        OutboundMessageDeduplicationCache cache = new OutboundMessageDeduplicationCache();
        cache.recordOutbound(null, "p".getBytes(StandardCharsets.UTF_8));
        cache.recordOutbound("topic", null);
        assertThat(cache.isRecentOutbound(null, "p".getBytes(StandardCharsets.UTF_8))).isFalse();
        assertThat(cache.isRecentOutbound("topic", null)).isFalse();
    }

    @Test
    void emptyByteArrayIsSupported() {
        OutboundMessageDeduplicationCache cache = new OutboundMessageDeduplicationCache();
        String topic = "building/b1/game/g1/state";
        byte[] empty = new byte[0];

        cache.recordOutbound(topic, empty);
        assertThat(cache.isRecentOutbound(topic, empty)).isTrue();
    }
}
