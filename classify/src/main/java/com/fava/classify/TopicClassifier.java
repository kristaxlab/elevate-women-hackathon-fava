package com.fava.classify;

import java.util.List;

/**
 * Maps draft text/URL + Theme Topic names to a {@link ClassifierDecision}.
 */
public interface TopicClassifier {

	ClassifierDecision classify(String draftText, List<String> themeNames);
}
