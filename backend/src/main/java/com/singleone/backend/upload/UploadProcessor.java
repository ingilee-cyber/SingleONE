package com.singleone.backend.upload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.singleone.backend.domain.upload.UploadBatch;
import com.singleone.backend.domain.upload.UploadBatchRepository;
import com.singleone.backend.domain.upload.UploadError;
import com.singleone.backend.domain.upload.UploadErrorRepository;
import com.singleone.backend.domain.upload.UploadStatus;
import com.singleone.backend.domain.upload.UploadType;
import com.singleone.backend.upload.journey.JourneyEventStore;
import com.singleone.backend.upload.journey.JourneyRow;
import com.singleone.backend.upload.journey.JourneyRowParser;
import com.singleone.backend.upload.master.MasterUpsertService;
import com.singleone.backend.upload.parse.RawRow;
import com.singleone.backend.upload.parse.RowSource;
import com.singleone.backend.upload.parse.RowSourceFactory;
import com.singleone.backend.upload.performance.PerformanceFactStore;
import com.singleone.backend.upload.performance.PerformanceRow;
import com.singleone.backend.upload.performance.PerformanceRowParser;

/**
 * PRD 11.7 업로드 상태 머신, PRD 11.8 비동기 처리. 실제 검증+ClickHouse 적재는 이 클래스의
 * @Async 메서드에서 수행한다 (다른 Bean에서 호출해야 프록시를 통해 비동기로 동작한다).
 */
@Component
public class UploadProcessor {

	private static final Logger log = LoggerFactory.getLogger(UploadProcessor.class);

	private final UploadBatchRepository uploadBatchRepository;
	private final UploadErrorRepository uploadErrorRepository;
	private final PerformanceFactStore performanceFactStore;
	private final JourneyEventStore journeyEventStore;
	private final MasterUpsertService masterUpsertService;

	public UploadProcessor(UploadBatchRepository uploadBatchRepository, UploadErrorRepository uploadErrorRepository,
			PerformanceFactStore performanceFactStore, JourneyEventStore journeyEventStore,
			MasterUpsertService masterUpsertService) {
		this.uploadBatchRepository = uploadBatchRepository;
		this.uploadErrorRepository = uploadErrorRepository;
		this.performanceFactStore = performanceFactStore;
		this.journeyEventStore = journeyEventStore;
		this.masterUpsertService = masterUpsertService;
	}

	@Async("uploadTaskExecutor")
	public void processPerformanceAsync(long batchId, Path tempFile, String originalFilename, String advertiserId) {
		try {
			doProcessPerformance(batchId, tempFile, originalFilename, advertiserId);
		} catch (Exception e) {
			log.error("성과 업로드 처리 중 예상치 못한 오류 (batchId={})", batchId, e);
			markFailed(batchId, List.of(new RowValidationError(0, "INTERNAL_ERROR", "처리 중 오류가 발생했습니다: " + e.getMessage())));
		} finally {
			deleteQuietly(tempFile);
		}
	}

	@Async("uploadTaskExecutor")
	public void processJourneyAsync(long batchId, Path tempFile, String originalFilename, String advertiserId) {
		try {
			doProcessJourney(batchId, tempFile, originalFilename, advertiserId);
		} catch (Exception e) {
			log.error("Journey 업로드 처리 중 예상치 못한 오류 (batchId={})", batchId, e);
			markFailed(batchId, List.of(new RowValidationError(0, "INTERNAL_ERROR", "처리 중 오류가 발생했습니다: " + e.getMessage())));
		} finally {
			deleteQuietly(tempFile);
		}
	}

	private void doProcessPerformance(long batchId, Path tempFile, String originalFilename, String advertiserId) throws IOException {
		List<RowValidationError> errors = new ArrayList<>();
		Set<String> naturalKeysInFile = new HashSet<>();
		List<PerformanceRow> buffer = new ArrayList<>(UploadLimits.INSERT_CHUNK_SIZE);
		long rowCount = 0;
		LocalDate minDate = null;
		LocalDate maxDate = null;

		try (InputStream in = Files.newInputStream(tempFile); RowSource source = RowSourceFactory.open(in, originalFilename)) {
			while (source.hasNext()) {
				RawRow raw = source.next();
				rowCount++;
				if (rowCount > UploadLimits.MAX_ROWS) {
					errors.add(new RowValidationError(raw.rowNo(), "TOO_MANY_ROWS",
						"파일당 최대 " + UploadLimits.MAX_ROWS + "행을 초과했습니다."));
					break;
				}

				PerformanceRowParser.Result result = PerformanceRowParser.parse(raw, advertiserId);
				if (!result.isValid()) {
					errors.addAll(result.errors());
					continue;
				}

				PerformanceRow row = result.row();
				if (!naturalKeysInFile.add(row.naturalKey())) {
					errors.add(new RowValidationError(raw.rowNo(), "DUPLICATE_NATURAL_KEY_IN_FILE",
						"파일 내에 동일한 natural key가 중복됩니다: " + row.naturalKey()));
					continue;
				}

				minDate = minDate == null || row.date().isBefore(minDate) ? row.date() : minDate;
				maxDate = maxDate == null || row.date().isAfter(maxDate) ? row.date() : maxDate;

				buffer.add(row);
				if (buffer.size() >= UploadLimits.INSERT_CHUNK_SIZE && errors.isEmpty()) {
					performanceFactStore.insertBatch(buffer, batchId);
					buffer.clear();
				}
			}
			if (errors.isEmpty() && !buffer.isEmpty()) {
				performanceFactStore.insertBatch(buffer, batchId);
			}
		}

		if (!errors.isEmpty()) {
			performanceFactStore.deleteByBatch(batchId);
			markFailed(batchId, errors);
			return;
		}

		if (rowCount == 0) {
			markFailed(batchId, List.of(new RowValidationError(0, "EMPTY_FILE", "파일에 데이터 행이 없습니다.")));
			return;
		}

		List<Long> successBatchIds = uploadBatchRepository
			.findByAdvertiserIdAndTypeAndStatus(advertiserId, UploadType.PERFORMANCE, UploadStatus.SUCCESS)
			.stream().map(UploadBatch::getUploadBatchId).toList();
		Set<String> duplicates = performanceFactStore.findExistingSuccessNaturalKeys(advertiserId, successBatchIds, minDate, maxDate);
		duplicates.retainAll(naturalKeysInFile);

		if (!duplicates.isEmpty()) {
			markStatus(batchId, UploadStatus.DUPLICATE_CONFIRMATION_REQUIRED, rowCount, null, null);
			return;
		}

		finalizeSuccess(batchId, advertiserId, rowCount);
	}

	private void doProcessJourney(long batchId, Path tempFile, String originalFilename, String advertiserId) throws IOException {
		List<RowValidationError> errors = new ArrayList<>();
		Set<String> naturalKeysInFile = new HashSet<>();
		List<JourneyRow> buffer = new ArrayList<>(UploadLimits.INSERT_CHUNK_SIZE);
		long rowCount = 0;
		Instant minTimestamp = null;
		Instant maxTimestamp = null;

		try (InputStream in = Files.newInputStream(tempFile); RowSource source = RowSourceFactory.open(in, originalFilename)) {
			while (source.hasNext()) {
				RawRow raw = source.next();
				rowCount++;
				if (rowCount > UploadLimits.MAX_ROWS) {
					errors.add(new RowValidationError(raw.rowNo(), "TOO_MANY_ROWS",
						"파일당 최대 " + UploadLimits.MAX_ROWS + "행을 초과했습니다."));
					break;
				}

				JourneyRowParser.Result result = JourneyRowParser.parse(raw, advertiserId);
				if (!result.isValid()) {
					errors.addAll(result.errors());
					continue;
				}

				JourneyRow row = result.row();
				if (!naturalKeysInFile.add(row.eventNaturalKey())) {
					errors.add(new RowValidationError(raw.rowNo(), "DUPLICATE_NATURAL_KEY_IN_FILE",
						"파일 내에 동일한 event natural key가 중복됩니다: " + row.eventNaturalKey()));
					continue;
				}

				minTimestamp = minTimestamp == null || row.eventTimestamp().isBefore(minTimestamp) ? row.eventTimestamp() : minTimestamp;
				maxTimestamp = maxTimestamp == null || row.eventTimestamp().isAfter(maxTimestamp) ? row.eventTimestamp() : maxTimestamp;

				buffer.add(row);
				if (buffer.size() >= UploadLimits.INSERT_CHUNK_SIZE && errors.isEmpty()) {
					journeyEventStore.insertBatch(buffer, batchId);
					buffer.clear();
				}
			}
			if (errors.isEmpty() && !buffer.isEmpty()) {
				journeyEventStore.insertBatch(buffer, batchId);
			}
		}

		if (!errors.isEmpty()) {
			journeyEventStore.deleteByBatch(batchId);
			markFailed(batchId, errors);
			return;
		}

		if (rowCount == 0) {
			markFailed(batchId, List.of(new RowValidationError(0, "EMPTY_FILE", "파일에 데이터 행이 없습니다.")));
			return;
		}

		List<Long> successBatchIds = uploadBatchRepository
			.findByAdvertiserIdAndTypeAndStatus(advertiserId, UploadType.JOURNEY, UploadStatus.SUCCESS)
			.stream().map(UploadBatch::getUploadBatchId).toList();
		Set<String> duplicates = journeyEventStore.findExistingSuccessEventKeys(advertiserId, successBatchIds, minTimestamp, maxTimestamp);
		duplicates.retainAll(naturalKeysInFile);

		if (!duplicates.isEmpty()) {
			markStatus(batchId, UploadStatus.DUPLICATE_CONFIRMATION_REQUIRED, rowCount, null, null);
			return;
		}

		markStatus(batchId, UploadStatus.SUCCESS, rowCount, rowCount, 0L);
	}

	/**
	 * DUPLICATE_CONFIRMATION_REQUIRED 상태에서 사용자가 덮어쓰기를 확인했을 때 호출한다.
	 * 데이터는 이미 ClickHouse에 적재되어 있으므로(비활성 상태), 상태만 SUCCESS로 바꾸고
	 * (성과 업로드라면) Master Upsert를 수행한다.
	 */
	@Transactional
	public void confirmOverwrite(UploadBatch batch) {
		long rowCount = batch.getTotalRows() == null ? 0 : batch.getTotalRows();
		if (batch.getType() == UploadType.PERFORMANCE) {
			finalizeSuccess(batch.getUploadBatchId(), batch.getAdvertiserId(), rowCount);
		} else {
			markStatus(batch.getUploadBatchId(), UploadStatus.SUCCESS, rowCount, rowCount, 0L);
		}
	}

	@Transactional
	public void cancel(UploadBatch batch) {
		if (batch.getType() == UploadType.PERFORMANCE) {
			performanceFactStore.deleteByBatch(batch.getUploadBatchId());
		} else {
			journeyEventStore.deleteByBatch(batch.getUploadBatchId());
		}
		markStatus(batch.getUploadBatchId(), UploadStatus.CANCELLED, batch.getTotalRows(), null, null);
	}

	private void finalizeSuccess(long batchId, String advertiserId, long rowCount) {
		markStatus(batchId, UploadStatus.IMPORTING, rowCount, null, null);
		masterUpsertService.upsertFromPerformanceBatch(batchId, advertiserId);
		markStatus(batchId, UploadStatus.SUCCESS, rowCount, rowCount, 0L);
	}

	// self-invocation(같은 클래스 내부 호출)이라 @Transactional 프록시가 적용되지 않는다.
	// 각 저장은 Spring Data JPA의 기본 동작대로 개별 트랜잭션으로 처리된다.
	protected void markFailed(long batchId, List<RowValidationError> errors) {
		for (RowValidationError error : errors) {
			uploadErrorRepository.save(new UploadError(batchId, error.rowNo(), error.errorCode(), error.message()));
		}
		markStatus(batchId, UploadStatus.FAILED, null, 0L, (long) errors.size());
	}

	protected void markStatus(long batchId, UploadStatus status, Long totalRows, Long successRows, Long errorRows) {
		UploadBatch batch = uploadBatchRepository.findById(batchId).orElseThrow();
		batch.setStatus(status);
		if (totalRows != null) {
			batch.setTotalRows(totalRows);
		}
		if (successRows != null) {
			batch.setSuccessRows(successRows);
		}
		if (errorRows != null) {
			batch.setErrorRows(errorRows);
		}
		uploadBatchRepository.save(batch);
	}

	private void deleteQuietly(Path tempFile) {
		try {
			Files.deleteIfExists(tempFile);
		} catch (IOException e) {
			log.warn("임시 업로드 파일 삭제 실패: {}", tempFile, e);
		}
	}

}
