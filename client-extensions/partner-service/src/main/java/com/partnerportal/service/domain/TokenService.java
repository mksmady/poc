package com.partnerportal.service.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Service;

/**
 * Invitation and revision link tokens (CLAUDE.md rule 6): 32 random bytes,
 * URL-safe base64 without padding. Only the SHA-256 hex hash is stored, and
 * hashes are compared in constant time.
 */
@Service
public class TokenService {

	public String generate() {
		byte[] bytes = new byte[32];

		_secureRandom.nextBytes(bytes);

		return Base64.getUrlEncoder(
		).withoutPadding(
		).encodeToString(
			bytes
		);
	}

	public String hash(String token) {
		try {
			MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");

			byte[] digest = messageDigest.digest(
				token.getBytes(StandardCharsets.UTF_8));

			StringBuilder sb = new StringBuilder(digest.length * 2);

			for (byte b : digest) {
				sb.append(Character.forDigit((b >> 4) & 0xF, 16));
				sb.append(Character.forDigit(b & 0xF, 16));
			}

			return sb.toString();
		}
		catch (NoSuchAlgorithmException noSuchAlgorithmException) {
			throw new IllegalStateException(noSuchAlgorithmException);
		}
	}

	/**
	 * @return whether the token hashes to the stored hash; false for blank
	 *         input, compared in constant time otherwise
	 */
	public boolean matches(String token, String storedHash) {
		if ((token == null) || token.isEmpty() || (storedHash == null) ||
			storedHash.isEmpty()) {

			return false;
		}

		return MessageDigest.isEqual(
			hash(
				token
			).getBytes(
				StandardCharsets.US_ASCII
			),
			storedHash.getBytes(StandardCharsets.US_ASCII));
	}

	private final SecureRandom _secureRandom = new SecureRandom();

}
