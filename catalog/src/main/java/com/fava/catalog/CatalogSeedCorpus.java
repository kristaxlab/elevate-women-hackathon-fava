package com.fava.catalog;

import java.util.List;
import java.util.Optional;

/**
 * Fixed seed-only corpus (~30 enriched Saved Items) for manual Smart Search probing.
 * Not a production migration.
 */
public final class CatalogSeedCorpus {

	public static final List<String> THEME_NAMES = List.of("Food", "Fitness", "AI", "Travel");

	private CatalogSeedCorpus() {
	}

	public static List<SeedDraft> items() {
		return List.of(
				// Food / recipes
				draft(
						"Food",
						Optional.of("https://www.instagram.com/p/seedPasta01/"),
						"Quick weeknight cacio e pepe with black pepper and pecorino.",
						SourceType.RECIPE,
						"Weeknight cacio e pepe",
						Optional.of("Anna"),
						List.of("italian", "dinner", "pasta"),
						"A quick Italian pasta dinner recipe for cacio e pepe recommended by Anna.",
						"91001"),
				draft(
						"Food",
						Optional.of("https://www.instagram.com/p/seedBorscht02/"),
						"Классический борщ со сметаной — семейный рецепт на ужин.",
						SourceType.RECIPE,
						"Classic borscht",
						Optional.of("Anna"),
						List.of("soup", "dinner", "ukrainian"),
						"A classic Ukrainian borscht soup dinner recipe with sour cream, recommended by Anna.",
						"91002"),
				draft(
						"Food",
						Optional.of("https://www.youtube.com/watch?v=seedTacos03"),
						"Street-style tacos al pastor with pineapple salsa.",
						SourceType.RECIPE,
						"Tacos al pastor",
						Optional.of("Diego"),
						List.of("mexican", "dinner", "streetfood"),
						"Mexican street-food dinner recipe for tacos al pastor with pineapple salsa, recommended by Diego.",
						"91003"),
				draft(
						"Food",
						Optional.empty(),
						"Простой овсяный завтрак с ягодами и мёдом.",
						SourceType.RECIPE,
						"Berry oatmeal breakfast",
						Optional.empty(),
						List.of("breakfast", "oats", "quick"),
						"A quick oatmeal breakfast recipe with berries and honey.",
						"91004"),
				draft(
						"Food",
						Optional.of("https://www.instagram.com/reel/seedSalad05/"),
						"Crisp chopped salad with lemon tahini dressing.",
						SourceType.INSTAGRAM,
						"Lemon tahini chopped salad",
						Optional.of("Mia"),
						List.of("salad", "lunch", "vegetarian"),
						"An Instagram vegetarian lunch salad with lemon tahini dressing, recommended by Mia.",
						"91005"),
				draft(
						"Food",
						Optional.of("https://example.com/recipes/shakshuka"),
						"Shakshuka for two — eggs in spicy tomato sauce.",
						SourceType.RECIPE,
						"Shakshuka for two",
						Optional.of("Anna"),
						List.of("breakfast", "eggs", "middleeast"),
						"A Middle Eastern breakfast recipe: shakshuka eggs in spicy tomato sauce, recommended by Anna.",
						"91006"),
				draft(
						"Food",
						Optional.empty(),
						"Домашний хлеб на закваске — заметки по расстойке.",
						SourceType.NOTE,
						"Sourdough proofing notes",
						Optional.of("Leo"),
						List.of("baking", "bread", "sourdough"),
						"Sourdough bread baking notes about proofing, recommended by Leo.",
						"91007"),
				draft(
						"Food",
						Optional.of("https://www.youtube.com/watch?v=seedRamen08"),
						"Weeknight miso ramen with soft egg and greens.",
						SourceType.YOUTUBE,
						"Weeknight miso ramen",
						Optional.empty(),
						List.of("japanese", "dinner", "noodles"),
						"A YouTube Japanese dinner recipe for weeknight miso ramen with soft egg.",
						"91008"),

				// Fitness
				draft(
						"Fitness",
						Optional.of("https://www.youtube.com/watch?v=seedPilates09"),
						"Beginner reformer pilates tips for hip mobility.",
						SourceType.YOUTUBE,
						"Reformer pilates for beginners",
						Optional.of("Sofia"),
						List.of("pilates", "mobility", "beginner"),
						"YouTube beginner reformer pilates tips for hip mobility, recommended by Sofia.",
						"91009"),
				draft(
						"Fitness",
						Optional.empty(),
						"Утренняя зарядка на 15 минут: приседания, планка, растяжка.",
						SourceType.NOTE,
						"15-minute morning routine",
						Optional.of("Sofia"),
						List.of("routine", "strength", "morning"),
						"A 15-minute morning strength routine with squats, plank, and stretching, recommended by Sofia.",
						"91010"),
				draft(
						"Fitness",
						Optional.of("https://www.instagram.com/p/seedRun11/"),
						"Easy 5k progression plan for returning runners.",
						SourceType.INSTAGRAM,
						"Easy 5k progression",
						Optional.of("Marco"),
						List.of("running", "plan", "beginner"),
						"An Instagram beginner running plan for an easy 5k progression, recommended by Marco.",
						"91011"),
				draft(
						"Fitness",
						Optional.of("https://www.youtube.com/watch?v=seedYoga12"),
						"Bedtime yin yoga for tight hips and lower back.",
						SourceType.YOUTUBE,
						"Bedtime yin yoga",
						Optional.empty(),
						List.of("yoga", "recovery", "hips"),
						"A YouTube bedtime yin yoga session for tight hips and lower back recovery.",
						"91012"),
				draft(
						"Fitness",
						Optional.empty(),
						"Заметки по восстановлению после длинной пробежки: сон, белок, лёгкая ходьба.",
						SourceType.NOTE,
						"Long-run recovery notes",
						Optional.of("Marco"),
						List.of("running", "recovery", "nutrition"),
						"Recovery notes after a long run: sleep, protein, and easy walking, recommended by Marco.",
						"91013"),
				draft(
						"Fitness",
						Optional.of("https://example.com/articles/kettlebell-swings"),
						"Why kettlebell swings beat endless crunches for core power.",
						SourceType.ARTICLE,
						"Kettlebell swings for core",
						Optional.of("Sofia"),
						List.of("strength", "kettlebell", "core"),
						"An article arguing kettlebell swings beat crunches for core power, recommended by Sofia.",
						"91014"),
				draft(
						"Fitness",
						Optional.of("https://www.instagram.com/reel/seedStretch15/"),
						"Desk stretch sequence for neck and shoulders.",
						SourceType.INSTAGRAM,
						"Desk stretch sequence",
						Optional.empty(),
						List.of("mobility", "desk", "shoulders"),
						"An Instagram desk stretch sequence for neck and shoulder mobility.",
						"91015"),

				// AI
				draft(
						"AI",
						Optional.of("https://www.youtube.com/watch?v=seedEmbed16"),
						"Clear explainer on text embeddings for semantic search.",
						SourceType.YOUTUBE,
						"Text embeddings explainer",
						Optional.of("Priya"),
						List.of("embeddings", "search", "ml"),
						"A YouTube explainer on text embeddings for semantic search, recommended by Priya.",
						"91016"),
				draft(
						"AI",
						Optional.empty(),
						"Заметки: как писать промпты для классификации тем без галлюцинаций.",
						SourceType.NOTE,
						"Theme classification prompt notes",
						Optional.of("Priya"),
						List.of("prompts", "classification", "llm"),
						"Notes on writing prompts for theme classification without hallucinations, recommended by Priya.",
						"91017"),
				draft(
						"AI",
						Optional.of("https://example.com/articles/pgvector-cosine"),
						"Using pgvector cosine distance for catalog retrieval.",
						SourceType.ARTICLE,
						"pgvector cosine retrieval",
						Optional.empty(),
						List.of("pgvector", "postgres", "search"),
						"An article on using pgvector cosine distance for catalog retrieval in Postgres.",
						"91018"),
				draft(
						"AI",
						Optional.of("https://www.linkedin.com/posts/seed-rag-19"),
						"RAG vs structured list retrieval — when lists beat essays.",
						SourceType.LINKEDIN,
						"RAG vs structured lists",
						Optional.of("Alex"),
						List.of("rag", "product", "search"),
						"A LinkedIn post comparing RAG essays vs structured list retrieval, recommended by Alex.",
						"91019"),
				draft(
						"AI",
						Optional.of("https://www.youtube.com/watch?v=seedEval20"),
						"Практический разбор offline-оценки retrieval: recall@k и ручная проверка.",
						SourceType.YOUTUBE,
						"Offline retrieval evaluation",
						Optional.of("Priya"),
						List.of("evaluation", "retrieval", "ml"),
						"A practical YouTube walkthrough of offline retrieval evaluation with recall@k and manual checks, recommended by Priya.",
						"91020"),
				draft(
						"AI",
						Optional.empty(),
						"Book marked: Designing Data-Intensive Applications chapter on batch vs stream.",
						SourceType.BOOK,
						"DDIA batch vs stream notes",
						Optional.of("Alex"),
						List.of("books", "systems", "streaming"),
						"Book notes from Designing Data-Intensive Applications on batch vs stream processing, recommended by Alex.",
						"91021"),
				draft(
						"AI",
						Optional.of("https://www.instagram.com/p/seedTools22/"),
						"Screenshot dump of useful LLM tooling shortcuts.",
						SourceType.INSTAGRAM,
						"LLM tooling shortcuts",
						Optional.empty(),
						List.of("tools", "llm", "productivity"),
						"An Instagram dump of useful LLM tooling shortcuts for productivity.",
						"91022"),
				draft(
						"AI",
						Optional.of("https://example.com/notes/cross-lingual-search"),
						"English search_text lets Russian questions hit English saves.",
						SourceType.ARTICLE,
						"Cross-lingual search_text tip",
						Optional.of("Priya"),
						List.of("search", "i18n", "embeddings"),
						"An article tip: English search_text lets Russian questions hit English saves via embeddings, recommended by Priya.",
						"91023"),

				// Travel
				draft(
						"Travel",
						Optional.of("https://www.instagram.com/p/seedLisbon24/"),
						"Lisbon weekend: miradouros, pasteis, tram 28 without the crush.",
						SourceType.INSTAGRAM,
						"Lisbon weekend tips",
						Optional.of("Nina"),
						List.of("lisbon", "weekend", "europe"),
						"Instagram weekend tips for Lisbon viewpoints, pastries, and tram 28, recommended by Nina.",
						"91024"),
				draft(
						"Travel",
						Optional.empty(),
						"Список вещей в ручную кладь на ночной поезд: беруши, шарф, зарядка.",
						SourceType.NOTE,
						"Overnight train packing list",
						Optional.of("Nina"),
						List.of("packing", "train", "europe"),
						"A packing list for overnight trains: earplugs, scarf, charger, recommended by Nina.",
						"91025"),
				draft(
						"Travel",
						Optional.of("https://www.youtube.com/watch?v=seedTokyo26"),
						"Tokyo 48 hours: Asakusa morning, Shimokitazawa afternoon.",
						SourceType.YOUTUBE,
						"Tokyo 48-hour itinerary",
						Optional.of("Ken"),
						List.of("tokyo", "japan", "itinerary"),
						"A YouTube Tokyo 48-hour itinerary covering Asakusa and Shimokitazawa, recommended by Ken.",
						"91026"),
				draft(
						"Travel",
						Optional.of("https://example.com/articles/visa-run-checklist"),
						"Visa-run checklist before the border day.",
						SourceType.ARTICLE,
						"Visa-run checklist",
						Optional.empty(),
						List.of("visa", "checklist", "admin"),
						"An article checklist for a visa run before border day.",
						"91027"),
				draft(
						"Travel",
						Optional.of("https://www.instagram.com/reel/seedHike28/"),
						"Easy coastal hike near Barcelona with cafe at the top.",
						SourceType.INSTAGRAM,
						"Barcelona coastal hike",
						Optional.of("Nina"),
						List.of("hiking", "barcelona", "outdoors"),
						"An Instagram easy coastal hike near Barcelona with a cafe at the top, recommended by Nina.",
						"91028"),
				draft(
						"Travel",
						Optional.empty(),
						"Фильм на дорогу: Before Sunrise — медленный разговорный вайб.",
						SourceType.MOVIE,
						"Before Sunrise for the train",
						Optional.of("Ken"),
						List.of("movies", "train", "europe"),
						"Movie pick for travel: Before Sunrise, a slow conversational vibe for the train, recommended by Ken.",
						"91029"),
				draft(
						"Travel",
						Optional.of("https://www.youtube.com/watch?v=seedPack30"),
						"One-bag packing for two weeks in mixed climates.",
						SourceType.YOUTUBE,
						"One-bag packing guide",
						Optional.of("Nina"),
						List.of("packing", "onebag", "travel"),
						"A YouTube one-bag packing guide for two weeks in mixed climates, recommended by Nina.",
						"91030"));
	}

	private static SeedDraft draft(
			String themeName,
			Optional<String> url,
			String bodyText,
			SourceType sourceType,
			String title,
			Optional<String> recommendedBy,
			List<String> tags,
			String searchText,
			String userLibItemId) {
		return new SeedDraft(
				themeName, url, bodyText, sourceType, title, recommendedBy, tags, searchText, userLibItemId);
	}

	/**
	 * One seed Saved Item before persistence (synthetic Theme Topic library pointer).
	 */
	public record SeedDraft(
			String themeName,
			Optional<String> url,
			String bodyText,
			SourceType sourceType,
			String title,
			Optional<String> recommendedBy,
			List<String> tags,
			String searchText,
			String userLibItemId) {

		public SeedDraft {
			url = url == null ? Optional.empty() : url;
			recommendedBy = recommendedBy == null ? Optional.empty() : recommendedBy;
			tags = List.copyOf(tags);
		}
	}
}
