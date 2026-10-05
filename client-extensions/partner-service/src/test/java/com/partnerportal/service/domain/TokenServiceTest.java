package com.partnerportal.service.domain;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TokenServiceTest {

	@Test
	public void testGenerateIs32UrlSafeRandomBytes() {
		Set<String> tokens = new HashSet<>();

		for (int i = 0; i < 100; i++) {
			String token = _tokenService.generate();

			Assertions.assertTrue(token.matches("[A-Za-z0-9_-]{43}"), token);
			Assertions.assertEquals(
				32,
				Base64.getUrlDecoder(
				).decode(
					token
				).length);

			tokens.add(token);
		}

		Assertions.assertEquals(100, tokens.size());
	}

	@Test
	public void testHashIsSha256Hex() {
		Assertions.assertEquals(
			"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
			_tokenService.hash("abc"));
	}

	@Test
	public void testMatches() {
		String token = _tokenService.generate();

		String hash = _tokenService.hash(token);

		Assertions.assertTrue(_tokenService.matches(token, hash));
		Assertions.assertFalse(_tokenService.matches(token + "x", hash));
		Assertions.assertFalse(_tokenService.matches("", hash));
		Assertions.assertFalse(_tokenService.matches(null, hash));
		Assertions.assertFalse(_tokenService.matches(token, null));
	}

	private final TokenService _tokenService = new TokenService();

}
