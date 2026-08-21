package com.singleone.backend.upload.parse;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * PRD 11.1: CSV는 UTF-8을 권장한다.
 */
public class CsvRowSource implements RowSource {

	private final CSVParser parser;
	private final Iterator<CSVRecord> records;

	public CsvRowSource(InputStream inputStream) throws IOException {
		Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
		this.parser = CSVFormat.DEFAULT.builder()
			.setHeader()
			.setSkipHeaderRecord(true)
			.setTrim(true)
			.setIgnoreSurroundingSpaces(true)
			.build()
			.parse(reader);
		this.records = parser.iterator();
	}

	@Override
	public boolean hasNext() {
		return records.hasNext();
	}

	@Override
	public RawRow next() {
		CSVRecord record = records.next();
		return new RawRow(record.getRecordNumber(), record.toMap());
	}

	@Override
	public void close() {
		try {
			parser.close();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
