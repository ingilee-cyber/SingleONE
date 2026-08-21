package com.singleone.backend.upload.parse;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public final class RowSourceFactory {

	private RowSourceFactory() {
	}

	public static RowSource open(InputStream inputStream, String filename) throws IOException {
		String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".csv")) {
			return new CsvRowSource(inputStream);
		}
		if (lower.endsWith(".xlsx")) {
			return new XlsxRowSource(inputStream);
		}
		throw new UnsupportedFileTypeException(filename);
	}

}
