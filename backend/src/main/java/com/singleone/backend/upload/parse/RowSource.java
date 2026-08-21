package com.singleone.backend.upload.parse;

import java.util.Iterator;

/**
 * PRD 11.11: Streaming 방식으로 파싱해 전체 파일을 메모리에 적재하지 않는다.
 * CSV/XLSX 각각의 구현이 파일을 한 행씩 순차적으로 넘겨준다.
 */
public interface RowSource extends Iterator<RawRow>, AutoCloseable {

	@Override
	void close();

}
