package com.partnerportal.service.domain;

/**
 * Detects upload types from the file content, never from the name or the
 * declared content type (US-002 AC8: PDF, JPG, PNG only).
 */
public enum FileTypes {

	JPG("image/jpeg", "jpg", new int[] {0xFF, 0xD8, 0xFF}),
	PDF("application/pdf", "pdf", new int[] {0x25, 0x50, 0x44, 0x46, 0x2D}),
	PNG(
		"image/png", "png",
		new int[] {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});

	/**
	 * @return the detected type, or null when the content is none of the
	 *         accepted types
	 */
	public static FileTypes sniff(byte[] bytes) {
		if (bytes == null) {
			return null;
		}

		for (FileTypes fileType : values()) {
			if (fileType._matches(bytes)) {
				return fileType;
			}
		}

		return null;
	}

	/**
	 * A safe file name with the extension of the detected type.
	 */
	public String fileName(String originalName) {
		String base = (originalName == null) ? "" : originalName;

		int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));

		base = base.substring(slash + 1);

		int dot = base.lastIndexOf('.');

		if (dot > 0) {
			base = base.substring(0, dot);
		}

		base = base.replaceAll(
			"[^A-Za-z0-9._ -]", "_"
		).trim();

		if (base.isEmpty()) {
			base = "document";
		}

		if (base.length() > 100) {
			base = base.substring(0, 100);
		}

		return base + "." + _extension;
	}

	public String getContentType() {
		return _contentType;
	}

	private FileTypes(String contentType, String extension, int[] magic) {
		_contentType = contentType;
		_extension = extension;
		_magic = magic;
	}

	private boolean _matches(byte[] bytes) {
		if (bytes.length < _magic.length) {
			return false;
		}

		for (int i = 0; i < _magic.length; i++) {
			if ((bytes[i] & 0xFF) != _magic[i]) {
				return false;
			}
		}

		return true;
	}

	private final String _contentType;
	private final String _extension;
	private final int[] _magic;

}
