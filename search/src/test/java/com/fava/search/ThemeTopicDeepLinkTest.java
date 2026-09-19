package com.fava.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ThemeTopicDeepLinkTest {

	@Test
	void forMessage_stripsMinus100PrefixForSupergroups() {
		assertThat(ThemeTopicDeepLink.forMessage(-100777L, "9001"))
				.isEqualTo("https://t.me/c/777/9001");
	}
}
