package com.fava.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ThemePickCallbackTest {

	@Test
	void encodeAndParse_roundTrip() {
		String data = ThemePickCallback.encode(1_234_567_890L, 2);

		assertThat(data).isEqualTo("f6:1234567890:2");
		assertThat(data.length()).isLessThanOrEqualTo(64);
		assertThat(ThemePickCallback.parse(data))
				.contains(new ThemePickCallback.Parsed(1_234_567_890L, 2));
	}

	@Test
	void nonPickData_isRejected() {
		assertThat(ThemePickCallback.isThemePick("url:https://x")).isFalse();
		assertThat(ThemePickCallback.parse("nope")).isEmpty();
		assertThat(ThemePickCallback.parse("f6:bad")).isEmpty();
	}
}
