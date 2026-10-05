package com.partnerportal.service.domain;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Short-lived signed sessions for the guest registration wizard
 * (architecture "Register" step 2). A session names one invitation and one
 * application and expires after {@code partner.registration.session.minutes}
 * (default 60); every authenticated call returns a renewed session, so the
 * limit works as an idle timeout.
 */
@Component
public class RegistrationSessions {

	public static final String AUDIENCE = "partner-registration";

	public RegistrationSessions(
		@Value("${partner.registration.session.secret:}") String secret,
		@Value("${partner.registration.session.minutes:60}") long minutes,
		Clock clock) {

		byte[] key;

		if ((secret == null) || secret.isEmpty()) {
			key = new byte[32];

			new SecureRandom().nextBytes(key);

			_log.warn(
				"partner.registration.session.secret is not set; sessions " +
					"will not survive a restart");
		}
		else {
			key = Base64.getDecoder(
			).decode(
				secret
			);
		}

		if (key.length < 32) {
			throw new IllegalArgumentException(
				"partner.registration.session.secret must be at least 32 " +
					"bytes, base64 encoded");
		}

		_key = key;
		_duration = Duration.ofMinutes(minutes);
		_clock = clock;
	}

	/**
	 * @param tokenHash the invitation's current token hash; a resend changes
	 *        it, which ends every session opened with the old link
	 */
	public String issue(
		long invitationId, long applicationId, String tokenHash) {

		Instant now = Instant.now(_clock);

		JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder(
		).audience(
			AUDIENCE
		).claim(
			"app", applicationId
		).claim(
			"inv", invitationId
		).claim(
			"tkh", _fingerprint(tokenHash)
		).expirationTime(
			Date.from(now.plus(_duration))
		).issueTime(
			Date.from(now)
		).build();

		try {
			SignedJWT signedJWT = new SignedJWT(
				new JWSHeader(JWSAlgorithm.HS256), jwtClaimsSet);

			signedJWT.sign(new MACSigner(_key));

			return signedJWT.serialize();
		}
		catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}

	/**
	 * @return the session, or null when the token is missing, forged or
	 *         expired
	 */
	public Session verify(String token) {
		if ((token == null) || token.isEmpty()) {
			return null;
		}

		try {
			SignedJWT signedJWT = SignedJWT.parse(token);

			if (!JWSAlgorithm.HS256.equals(
					signedJWT.getHeader(
					).getAlgorithm()) ||
				!signedJWT.verify(new MACVerifier(_key))) {

				return null;
			}

			JWTClaimsSet jwtClaimsSet = signedJWT.getJWTClaimsSet();

			Date expirationTime = jwtClaimsSet.getExpirationTime();

			if ((expirationTime == null) ||
				!expirationTime.toInstant(
				).isAfter(
					Instant.now(_clock)
				) ||
				!jwtClaimsSet.getAudience(
				).contains(
					AUDIENCE
				)) {

				return null;
			}

			return new Session(
				jwtClaimsSet.getLongClaim("inv"),
				jwtClaimsSet.getLongClaim("app"),
				jwtClaimsSet.getStringClaim("tkh"));
		}
		catch (Exception exception) {
			return null;
		}
	}

	public static class Session {

		public Session(
			long invitationId, long applicationId, String tokenFingerprint) {

			this.invitationId = invitationId;
			this.applicationId = applicationId;
			this.tokenFingerprint = tokenFingerprint;
		}

		/**
		 * @return whether the session was opened with the link whose hash is
		 *         {@code tokenHash}
		 */
		public boolean isFor(String tokenHash) {
			return (tokenFingerprint != null) &&
				   tokenFingerprint.equals(_fingerprint(tokenHash));
		}

		public final long applicationId;
		public final long invitationId;
		public final String tokenFingerprint;

	}

	private static String _fingerprint(String tokenHash) {
		if ((tokenHash == null) || (tokenHash.length() < 16)) {
			return null;
		}

		return tokenHash.substring(0, 16);
	}

	private static final Log _log = LogFactory.getLog(
		RegistrationSessions.class);

	private final Clock _clock;
	private final Duration _duration;
	private final byte[] _key;

}
