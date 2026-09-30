package com.example.demo.migration.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OutputRecordTest {

	private static String render(Object value) {
		return new OutputRecord(new String[] {"c"}).set("c", value).get("c");
	}

	@Test
	void numbersAreNeverExponential() {
		assertEquals("1000", render(new BigDecimal("1E+3")));
		assertEquals("0.0000005", render(new BigDecimal("5E-7")));
		assertEquals("263771234567", render(2.63771234567E11));
		assertEquals("12345678", render(1.2345678E7f));
	}

	@Test
	void scientificNotationStringsAreExpanded() {
		assertEquals("263771234567", render("2.63771234567E+11"));
		assertEquals("263771234567", render("2.63771234567E11"));
		assertEquals("100000000000", render("1E+11"));
	}

	@Test
	void nonNumericValuesAreLeftAlone() {
		assertEquals("12E4", render("12E4"));
		assertEquals("ABC1.5E+3", render("ABC1.5E+3"));
		assertEquals("0771234567", render("0771234567"));
		assertEquals("", render(null));
	}
}
