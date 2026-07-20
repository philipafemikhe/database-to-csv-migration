package com.example.demo.migration.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class AdditionalParamsParser {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private AdditionalParamsParser() {
	}

	/** Returns null if the input is null/blank or not valid JSON. */
	public static JsonNode parse(String additionalParamsJson) {
		if (additionalParamsJson == null || additionalParamsJson.isBlank()) {
			return null;
		}
		try {
			return MAPPER.readTree(additionalParamsJson);
		} catch (Exception e) {
			return null;
		}
	}

	/** First non-blank, non-"null" text value found among the given keys, or null. */
	public static String firstText(JsonNode node, String... keys) {
		if (node == null) {
			return null;
		}
		for (String key : keys) {
			JsonNode v = node.get(key);
			if (v != null && !v.isNull()) {
				String text = v.asText();
				if (text != null && !text.isBlank() && !"null".equalsIgnoreCase(text)) {
					return text;
				}
			}
		}
		return null;
	}
}
