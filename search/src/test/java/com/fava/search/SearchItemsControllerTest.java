package com.fava.search;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fava.catalog.CatalogNotFoundException;
import com.fava.catalog.EmbeddingProviderException;
import com.fava.catalog.SavedItem;
import com.fava.catalog.SourceType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class SearchItemsControllerTest {

	StructuredItemsSearcher searcher;
	MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		searcher = mock(StructuredItemsSearcher.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new SearchItemsController(searcher))
				.setControllerAdvice(new SearchApiExceptionHandler())
				.setMessageConverters(new JacksonJsonHttpMessageConverter(JsonMapper.builder().build()))
				.build();
	}

	@Test
	void search_returns200WithItemAndDistance_noEmbedding() throws Exception {
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
		when(searcher.search(eq(-100L), any(StructuredQuery.class), eq(0.45)))
				.thenReturn(List.of(new RankedSavedItem(saved, 0.12)));

		mockMvc.perform(post("/api/search/items")
						.param("chatId", "-100")
						.contentType(MediaType.APPLICATION_JSON)
						.content(
								"""
								{
								  "query": "pilates tips",
								  "limit": 3,
								  "filters": {}
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].item.id").value(7))
				.andExpect(jsonPath("$[0].item.chatId").value(-100))
				.andExpect(jsonPath("$[0].distance").value(0.12))
				.andExpect(jsonPath("$[0].embedding").doesNotExist());
	}

	@Test
	void search_omittedMaxDistance_defaultsTo045() throws Exception {
		when(searcher.search(eq(-100L), any(StructuredQuery.class), eq(0.45))).thenReturn(List.of(hit()));

		mockMvc.perform(post("/api/search/items")
						.param("chatId", "-100")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"query":"tips","limit":3,"filters":{}}
								"""))
				.andExpect(status().isOk());

		ArgumentCaptor<Double> maxDistance = ArgumentCaptor.forClass(Double.class);
		verify(searcher).search(eq(-100L), any(StructuredQuery.class), maxDistance.capture());
		org.assertj.core.api.Assertions.assertThat(maxDistance.getValue()).isEqualTo(0.45);
	}

	@Test
	void search_customMaxDistance_isPassedThrough() throws Exception {
		when(searcher.search(eq(-100L), any(StructuredQuery.class), eq(0.3))).thenReturn(List.of(hit()));

		mockMvc.perform(post("/api/search/items")
						.param("chatId", "-100")
						.param("maxDistance", "0.3")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"query":"tips","limit":3,"filters":{}}
								"""))
				.andExpect(status().isOk());

		verify(searcher).search(eq(-100L), any(StructuredQuery.class), eq(0.3));
	}

	@Test
	void search_missingChatId_returns400Problem() throws Exception {
		mockMvc.perform(post("/api/search/items")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"query":"tips","limit":3,"filters":{}}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("https://fava.local/problems/validation"));
	}

	@Test
	void search_blankQuery_returns400Problem() throws Exception {
		mockMvc.perform(post("/api/search/items")
						.param("chatId", "-100")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"query":"   ","limit":3,"filters":{}}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("https://fava.local/problems/validation"));
	}

	@Test
	void search_malformedJson_returns400Problem() throws Exception {
		mockMvc.perform(post("/api/search/items")
						.param("chatId", "-100")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{not-json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("https://fava.local/problems/validation"));
	}

	@Test
	void search_catalogMissing_returns404CatalogNotFound() throws Exception {
		when(searcher.search(eq(-100L), any(StructuredQuery.class), anyDouble()))
				.thenThrow(new CatalogNotFoundException(-100L));

		mockMvc.perform(post("/api/search/items")
						.param("chatId", "-100")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"query":"tips","limit":3,"filters":{}}
								"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.type").value("https://fava.local/problems/catalog-not-found"));
	}

	@Test
	void search_zeroHits_returns404NoSearchHits() throws Exception {
		when(searcher.search(eq(-100L), any(StructuredQuery.class), anyDouble()))
				.thenThrow(new NoSearchHitsException(-100L));

		mockMvc.perform(post("/api/search/items")
						.param("chatId", "-100")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"query":"tips","limit":3,"filters":{}}
								"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.type").value("https://fava.local/problems/no-search-hits"));
	}

	@Test
	void search_providerFailure_returns502Problem() throws Exception {
		when(searcher.search(eq(-100L), any(StructuredQuery.class), anyDouble()))
				.thenThrow(new EmbeddingProviderException("Embedding provider failed"));

		mockMvc.perform(post("/api/search/items")
						.param("chatId", "-100")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"query":"tips","limit":3,"filters":{}}
								"""))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.type").value("https://fava.local/problems/provider"));
	}

	private static RankedSavedItem hit() {
		return new RankedSavedItem(
				SavedItem.of(1L, -100L, Optional.empty(), "body", "AI", 0L),
				0.1);
	}
}
