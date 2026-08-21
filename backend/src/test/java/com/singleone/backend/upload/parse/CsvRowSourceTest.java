package com.singleone.backend.upload.parse;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class CsvRowSourceTest {

	@Test
	void readsHeaderAndRowsInOrder() throws Exception {
		String csv = "date,media,cost\n2026-08-12,META,100\n2026-08-13,GOOGLE,200\n";

		List<RawRow> rows = new ArrayList<>();
		try (RowSource source = RowSourceFactory.open(
				new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), "performance.csv")) {
			while (source.hasNext()) {
				rows.add(source.next());
			}
		}

		assertThat(rows).hasSize(2);
		assertThat(rows.get(0).rowNo()).isEqualTo(1);
		assertThat(rows.get(0).get("media")).isEqualTo("META");
		assertThat(rows.get(1).get("cost")).isEqualTo("200");
	}

	@Test
	void unsupportedExtensionThrows() {
		org.junit.jupiter.api.Assertions.assertThrows(UnsupportedFileTypeException.class,
			() -> RowSourceFactory.open(new ByteArrayInputStream(new byte[0]), "data.txt"));
	}

}
