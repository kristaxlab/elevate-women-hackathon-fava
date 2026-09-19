package com.fava;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SeedPropertiesTest {

	@Test
	void requireChatId_parsesTelegramChatId() {
		assertThat(new SeedProperties(true, " -100160016 ").requireChatId()).isEqualTo(-100160016L);
	}

	@Test
	void requireChatId_whenBlank_failsClearly() {
		assertThatThrownBy(() -> new SeedProperties(true, "").requireChatId())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("FAVA_SEED_CHAT_ID");
	}
}
