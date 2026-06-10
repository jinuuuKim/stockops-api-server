package com.stockops.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CrudRequestLoggingFilterTest {

    @Test
    void masksSensitiveJsonFieldsCaseInsensitively() {
        String body = "{\"password\":\"secret\",\"accessToken\":\"abc\",\"nested\":{\"apiKey\":\"key\"}}";

        String masked = CrudRequestLoggingFilter.maskForTest(body);

        assertThat(masked).contains("\"password\":\"***\"");
        assertThat(masked).contains("\"accessToken\":\"***\"");
        assertThat(masked).contains("\"apiKey\":\"***\"");
        assertThat(masked).doesNotContain("secret");
        assertThat(masked).doesNotContain("\"abc\"");
        assertThat(masked).doesNotContain("\"key\"");
    }

    @Test
    void masksAuthorizationLikeValues() {
        String body = "{\"authorization\":\"Bearer raw-token\",\"webhookUrl\":\"https://example.test/hook\"}";

        String masked = CrudRequestLoggingFilter.maskForTest(body);

        assertThat(masked).contains("\"authorization\":\"***\"");
        assertThat(masked).contains("\"webhookUrl\":\"***\"");
        assertThat(masked).doesNotContain("raw-token");
        assertThat(masked).doesNotContain("example.test/hook");
    }
}
