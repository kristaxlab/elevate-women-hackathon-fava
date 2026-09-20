package com.fava.catalog;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class CatalogItemsControllerTest {

	CatalogItemCreator creator;
	CatalogItemLister lister;
	MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		creator = mock(CatalogItemCreator.class);
		lister = mock(CatalogItemLister.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new CatalogItemsController(creator, lister))
				.setControllerAdvice(new CatalogApiExceptionHandler())
				.setMessageConverters(new JacksonJsonHttpMessageConverter(JsonMapper.builder().build()))
				.build();
	}

	@Test
	void list_returns200WithItemAndEmbeddingEnvelope() throws Exception {
		SavedItem saved = new SavedItem(
				7L,
				-100L,
				Optional.of("https://example.com/a"),
				"body",
				"AI",
				0L,
				Optional.empty(),
				Optional.empty(),
				Optional.of(SourceType.ARTICLE),
				Optional.of("Title"),
				Optional.empty(),
				List.of(),
				Optional.of("search"));
		StoredEmbedding embedding = new StoredEmbedding("test-model", 3, new float[] {0.1f, 0.2f, 0.3f});
		when(lister.list(eq(-100L)))
				.thenReturn(List.of(new ListedSavedItem(saved, Optional.of(embedding))));

		mockMvc.perform(get("/api/catalog/items").param("chatId", "-100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].item.id").value(7))
				.andExpect(jsonPath("$[0].item.chatId").value(-100))
				.andExpect(jsonPath("$[0].embedding.modelId").value("test-model"))
				.andExpect(jsonPath("$[0].embedding.dimensions").value(3))
				.andExpect(jsonPath("$[0].embedding.vector[0]").value(0.1));
	}

	@Test
	void list_itemWithoutVector_returnsNullEmbedding() throws Exception {
		SavedItem saved = SavedItem.of(3L, -100L, Optional.of("https://example.com/b"), "body", "AI", 0L);
		when(lister.list(eq(-100L))).thenReturn(List.of(new ListedSavedItem(saved, Optional.empty())));

		mockMvc.perform(get("/api/catalog/items").param("chatId", "-100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].item.id").value(3))
				.andExpect(jsonPath("$[0].embedding").value(nullValue()));
	}

	@Test
	void list_emptyCatalog_returns200EmptyArray() throws Exception {
		when(lister.list(eq(-100L))).thenReturn(List.of());

		mockMvc.perform(get("/api/catalog/items").param("chatId", "-100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void list_catalogMissing_returns404Problem() throws Exception {
		when(lister.list(eq(-100L))).thenThrow(new CatalogNotFoundException(-100L));

		mockMvc.perform(get("/api/catalog/items").param("chatId", "-100"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.type").value("https://fava.local/problems/catalog-not-found"));
	}

	@Test
	void create_returns201WithItemAndEmbedding() throws Exception {
		SavedItem saved = new SavedItem(
				7L,
				-100L,
				Optional.of("https://example.com/a"),
				"body",
				"AI",
				0L,
				Optional.empty(),
				Optional.empty(),
				Optional.of(SourceType.ARTICLE),
				Optional.of("Title"),
				Optional.empty(),
				List.of(),
				Optional.of("search"));
		StoredEmbedding embedding = new StoredEmbedding("test-model", 3, new float[] {0.1f, 0.2f, 0.3f});
		when(creator.create(any())).thenReturn(new SavedItemCreateResult.Created(saved, embedding));

		mockMvc.perform(post("/api/catalog/items")
						.contentType(MediaType.APPLICATION_JSON)
						.content(
								"""
								{
								  "chatId": -100,
								  "url": "https://example.com/a",
								  "bodyText": "body",
								  "themeName": "AI",
								  "sourceType": "ARTICLE",
								  "title": "Title",
								  "searchText": "search"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.item.id").value(7))
				.andExpect(jsonPath("$.item.chatId").value(-100))
				.andExpect(jsonPath("$.embedding.modelId").value("test-model"))
				.andExpect(jsonPath("$.embedding.dimensions").value(3))
				.andExpect(jsonPath("$.embedding.vector[0]").value(0.1));
	}

	@Test
	void create_duplicate_returns200WithStoredEmbedding() throws Exception {
		SavedItem saved = SavedItem.of(3L, -100L, Optional.of("https://example.com/dup"), "body", "AI", 0L);
		StoredEmbedding embedding = new StoredEmbedding("test-model", 2, new float[] {0.5f, 0.25f});
		when(creator.create(any()))
				.thenReturn(new SavedItemCreateResult.Duplicate(saved, Optional.of(embedding)));

		mockMvc.perform(post("/api/catalog/items")
						.contentType(MediaType.APPLICATION_JSON)
						.content(
								"""
								{
								  "chatId": -100,
								  "url": "https://example.com/dup",
								  "bodyText": "body",
								  "themeName": "AI"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.item.id").value(3))
				.andExpect(jsonPath("$.embedding.modelId").value("test-model"))
				.andExpect(jsonPath("$.embedding.vector[0]").value(0.5));
	}

	@Test
	void create_nonNullId_returns400Problem() throws Exception {
		when(creator.create(any())).thenThrow(new InvalidSavedItemCreateException("id must be null; server assigns ids"));

		mockMvc.perform(post("/api/catalog/items")
						.contentType(MediaType.APPLICATION_JSON)
						.content(
								"""
								{
								  "id": 9,
								  "chatId": -100,
								  "bodyText": "body",
								  "themeName": "AI"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("https://fava.local/problems/validation"))
				.andExpect(jsonPath("$.title").value("Validation Failed"));
	}

	@Test
	void create_catalogMissing_returns404Problem() throws Exception {
		when(creator.create(any())).thenThrow(new CatalogNotFoundException(-100L));

		mockMvc.perform(post("/api/catalog/items")
						.contentType(MediaType.APPLICATION_JSON)
						.content(
								"""
								{
								  "chatId": -100,
								  "bodyText": "body",
								  "themeName": "AI"
								}
								"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.type").value("https://fava.local/problems/catalog-not-found"));
	}

	@Test
	void create_themeMissing_returns404Problem() throws Exception {
		when(creator.create(any())).thenThrow(new ThemeNotFoundException(-100L, "Nope"));

		mockMvc.perform(post("/api/catalog/items")
						.contentType(MediaType.APPLICATION_JSON)
						.content(
								"""
								{
								  "chatId": -100,
								  "bodyText": "body",
								  "themeName": "Nope"
								}
								"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.type").value("https://fava.local/problems/theme-not-found"));
	}

	@Test
	void create_providerFailure_returns502Problem() throws Exception {
		when(creator.create(any())).thenThrow(new EmbeddingProviderException("Embedding provider failed"));

		mockMvc.perform(post("/api/catalog/items")
						.contentType(MediaType.APPLICATION_JSON)
						.content(
								"""
								{
								  "chatId": -100,
								  "bodyText": "body",
								  "themeName": "AI"
								}
								"""))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.type").value("https://fava.local/problems/provider"));
	}

	@Test
	void create_malformedJson_returns400Problem() throws Exception {
		mockMvc.perform(post("/api/catalog/items")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{not-json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("https://fava.local/problems/validation"));
	}

	@Test
	void create_blankBodyText_returns400Problem() throws Exception {
		mockMvc.perform(post("/api/catalog/items")
						.contentType(MediaType.APPLICATION_JSON)
						.content(
								"""
								{
								  "chatId": -100,
								  "bodyText": "   ",
								  "themeName": "AI"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("https://fava.local/problems/validation"));
	}
}
