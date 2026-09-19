package com.fava.classify;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deterministic stub Classifier Decision: first Theme Topic in list order.
 * Used when OpenRouter API key is empty so local demos still file without AI.
 */
public final class FirstThemeTopicClassifier implements TopicClassifier {

	private static final Logger log = LoggerFactory.getLogger(FirstThemeTopicClassifier.class);

	public FirstThemeTopicClassifier() {
		log.warn(
				"OpenRouter API key is empty; using first-Theme-Topic stub Classifier Decision (no AI)");
	}

	@Override
	public ClassifierDecision classify(String draftText, List<String> themeNames) {
		if (themeNames == null || themeNames.isEmpty()) {
			return new ClassifierDecision.NeedsUserPick();
		}
		return new ClassifierDecision.Confident(themeNames.getFirst());
	}
}
