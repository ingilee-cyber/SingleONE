package com.singleone.backend.upload;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.singleone.backend.domain.advertiser.Advertiser;
import com.singleone.backend.domain.advertiser.AdvertiserRepository;
import com.singleone.backend.domain.upload.UploadBatch;
import com.singleone.backend.domain.upload.UploadBatchRepository;
import com.singleone.backend.domain.upload.UploadError;
import com.singleone.backend.domain.upload.UploadErrorRepository;
import com.singleone.backend.domain.upload.UploadStatus;
import com.singleone.backend.domain.upload.UploadType;

/**
 * PRD 13.4 Upload API 그룹의 진입점. 실제 검증/적재는 {@link UploadProcessor}(@Async)에 위임한다.
 */
@Service
public class UploadService {

	private final UploadBatchRepository uploadBatchRepository;
	private final UploadErrorRepository uploadErrorRepository;
	private final AdvertiserRepository advertiserRepository;
	private final UploadProcessor uploadProcessor;

	public UploadService(UploadBatchRepository uploadBatchRepository, UploadErrorRepository uploadErrorRepository,
			AdvertiserRepository advertiserRepository, UploadProcessor uploadProcessor) {
		this.uploadBatchRepository = uploadBatchRepository;
		this.uploadErrorRepository = uploadErrorRepository;
		this.advertiserRepository = advertiserRepository;
		this.uploadProcessor = uploadProcessor;
	}

	public UploadBatch initiatePerformanceUpload(String advertiserId, MultipartFile file) {
		return initiate(UploadType.PERFORMANCE, advertiserId, file, uploadProcessor::processPerformanceAsync);
	}

	public UploadBatch initiateJourneyUpload(String advertiserId, MultipartFile file) {
		return initiate(UploadType.JOURNEY, advertiserId, file, uploadProcessor::processJourneyAsync);
	}

	private interface AsyncStarter {
		void start(long batchId, Path tempFile, String originalFilename, String advertiserId);
	}

	// self-invocation(initiatePerformanceUpload/initiateJourneyUpload에서 호출)이라 @Transactional
	// 프록시가 적용되지 않는다. 아래에서 saveAndFlush를 사용해 비동기 Job이 시작되기 전에
	// batch/advertiser row가 확실히 커밋되도록 한다.
	protected UploadBatch initiate(UploadType type, String advertiserId, MultipartFile file, AsyncStarter starter) {
		if (file.isEmpty()) {
			throw new UploadRequestException("업로드할 파일이 비어 있습니다.");
		}
		if (file.getSize() > UploadLimits.MAX_FILE_SIZE_BYTES) {
			throw new UploadRequestException("파일 크기는 최대 50MB까지 허용됩니다.");
		}
		String filename = file.getOriginalFilename();
		String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
		if (!lower.endsWith(".csv") && !lower.endsWith(".xlsx")) {
			throw new UploadRequestException("CSV 또는 XLSX 파일만 업로드할 수 있습니다.");
		}

		ensureAdvertiserExists(advertiserId);

		UploadBatch batch = new UploadBatch(advertiserId, type, filename, UploadStatus.VALIDATING);
		batch = uploadBatchRepository.saveAndFlush(batch);

		Path tempFile = copyToTempFile(file, batch.getUploadBatchId());
		starter.start(batch.getUploadBatchId(), tempFile, filename, advertiserId);
		return batch;
	}

	/**
	 * upload_batch.advertiser_id는 advertiser 테이블을 참조하는 FK다. 새 광고주의 첫 업로드
	 * 시점에는 아직 Advertiser row가 없을 수 있어(실제 이름은 성과 업로드 성공 시 Master
	 * Upsert가 채운다, PRD 11.10), placeholder 이름으로 먼저 만들어둔다.
	 */
	private void ensureAdvertiserExists(String advertiserId) {
		if (advertiserRepository.existsById(advertiserId)) {
			return;
		}
		advertiserRepository.saveAndFlush(new Advertiser(advertiserId, advertiserId));
	}

	private Path copyToTempFile(MultipartFile file, long batchId) {
		try {
			Path tempFile = Files.createTempFile("singleone-upload-" + batchId + "-", suffixOf(file.getOriginalFilename()));
			file.transferTo(tempFile.toFile());
			return tempFile;
		} catch (IOException e) {
			throw new UncheckedIOException("업로드 파일을 임시 저장하지 못했습니다.", e);
		}
	}

	private String suffixOf(String filename) {
		if (filename == null) {
			return "";
		}
		int dot = filename.lastIndexOf('.');
		return dot >= 0 ? filename.substring(dot) : "";
	}

	@Transactional
	public UploadBatch confirmOverwrite(Long batchId) {
		UploadBatch batch = requireBatchInStatus(batchId, UploadStatus.DUPLICATE_CONFIRMATION_REQUIRED);
		uploadProcessor.confirmOverwrite(batch);
		return uploadBatchRepository.findById(batchId).orElseThrow();
	}

	@Transactional
	public UploadBatch cancel(Long batchId) {
		UploadBatch batch = requireBatchInStatus(batchId, UploadStatus.DUPLICATE_CONFIRMATION_REQUIRED);
		uploadProcessor.cancel(batch);
		return uploadBatchRepository.findById(batchId).orElseThrow();
	}

	private UploadBatch requireBatchInStatus(Long batchId, UploadStatus expected) {
		UploadBatch batch = uploadBatchRepository.findById(batchId)
			.orElseThrow(() -> new UploadRequestException("존재하지 않는 업로드 배치입니다: " + batchId));
		if (batch.getStatus() != expected) {
			throw new UploadRequestException("현재 상태(%s)에서는 이 작업을 수행할 수 없습니다.".formatted(batch.getStatus()));
		}
		return batch;
	}

	public UploadBatch getBatch(Long batchId) {
		return uploadBatchRepository.findById(batchId)
			.orElseThrow(() -> new UploadRequestException("존재하지 않는 업로드 배치입니다: " + batchId));
	}

	/**
	 * PRD 4.2/11.9: 업로드 이력 목록. 기본 50 / 최대 200 pagination은 Controller에서 강제한다.
	 */
	public Page<UploadBatch> listBatches(Pageable pageable) {
		return uploadBatchRepository.findAllByOrderByCreatedAtDesc(pageable);
	}

	public List<UploadError> getErrors(Long batchId) {
		return uploadErrorRepository.findByUploadBatchIdOrderByRowNoAsc(batchId);
	}

}
