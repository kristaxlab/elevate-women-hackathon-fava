package com.fava.search;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class StructuredQueriesControllerTest {

	MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		StructuredQueryParser parser = new StructuredQueryParser((system, user) -> """
				{"query":"pilates tips","limit":2,"filters":{"theme_name":"Wellness","tags":["stretching"]}}
				""");
		mockMvc = MockMvcBuilders.standaloneSetup(new StructuredQueriesController(parser))
				.setControllerAdvice(new SearchApiExceptionHandler())
				.setMessageConverters(new JacksonJsonHttpMessageConverter(JsonMapper.builder().build()))
				.build();
	}

	@Test
	void parse_returns200WithStructuredQuery() throws Exception {
		mockMvc.perform(post("/api/search/structured-queries")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"query":"find me pilates tips"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.query").value("pilates tips"))
				.andExpect(jsonPath("$.limit").value(2))
				.andExpect(jsonPath("$.filters.themeName").value("Wellness"))
				.andExpect(jsonPath("$.filters.tags[0]").value("stretching"));
	}

	@Test
	void parse_blankQuery_returns400Problem() throws Exception {
		mockMvc.perform(post("/api/search/structured-queries")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"query":"   "}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("https://fava.local/problems/validation"));
	}

	@Test
	void parse_missingQuery_returns400Problem() throws Exception {
		mockMvc.perform(post("/api/search/structured-queries")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("https://fava.local/problems/validation"));
	}

	@Test
	void parse_chatFailure_returns502Problem_noFallbackBody() throws Exception {
		StructuredQueryParser parser = new StructuredQueryParser((system, user) -> {
			throw new IllegalStateException("boom");
		});
		mockMvc = MockMvcBuilders.standaloneSetup(new StructuredQueriesController(parser))
				.setControllerAdvice(new SearchApiExceptionHandler())
				.setMessageConverters(new JacksonJsonHttpMessageConverter(JsonMapper.builder().build()))
				.build();

		mockMvc.perform(post("/api/search/structured-queries")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"query":"find me pilates tips"}
								"""))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.type").value("https://fava.local/problems/provider"))
				.andExpect(jsonPath("$.detail").value("Chat model failed"));
	}
}
