package com.singleone.backend.upload.parse;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import com.github.pjfanning.xlsx.StreamingReader;

/**
 * PRD 11.1: .xlsx는 첫 번째 worksheet만 읽는다. PRD 11.11: Streaming 방식으로 파싱해
 * 전체 파일을 메모리에 적재하지 않는다 (POI의 일반 Workbook 대신 스트리밍 리더 사용).
 */
public class XlsxRowSource implements RowSource {

	private final Workbook workbook;
	private final Iterator<Row> rows;
	private final DataFormatter dataFormatter = new DataFormatter();
	private String[] headers;
	private long nextRowNo = 1;

	public XlsxRowSource(InputStream inputStream) {
		this.workbook = StreamingReader.builder()
			.rowCacheSize(100)
			.bufferSize(4096)
			.open(inputStream);
		Sheet sheet = workbook.getSheetAt(0);
		this.rows = sheet.iterator();
		if (rows.hasNext()) {
			this.headers = toValues(rows.next());
		} else {
			this.headers = new String[0];
		}
	}

	@Override
	public boolean hasNext() {
		return rows.hasNext();
	}

	@Override
	public RawRow next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		Row row = rows.next();
		String[] values = toValues(row);
		Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < headers.length; i++) {
			String value = i < values.length ? values[i] : null;
			map.put(headers[i], value);
		}
		return new RawRow(nextRowNo++, map);
	}

	private String[] toValues(Row row) {
		int lastCellNum = Math.max(row.getLastCellNum(), 0);
		String[] values = new String[lastCellNum];
		for (int i = 0; i < lastCellNum; i++) {
			Cell cell = row.getCell(i);
			values[i] = cell == null ? "" : dataFormatter.formatCellValue(cell).strip();
		}
		return values;
	}

	@Override
	public void close() {
		try {
			workbook.close();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
