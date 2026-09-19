package com.fava.intent;

/**
 * Classifies Action Intent for a {@link RoutedRequest}.
 * Failures must surface as {@link ActionIntentClassificationException} (fail closed).
 */
public interface ActionIntentClassifier {

	ActionIntent classify(RoutedRequest request);
}
