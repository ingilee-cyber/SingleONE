package com.singleone.backend.upload;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.singleone.backend.domain.upload.UploadBatch;

/**
 * PRD 13.4 Upload API 그룹.
 */
@RestController
public class UploadController {

	private static final int DEFAULT_PAGE_SIZE = 50;
	private static final int MAX_PAGE_SIZE = 200;

	private final UploadService uploadService;

	public UploadController(UploadService uploadService) {
		this.uploadService = uploadService;
	}

	@PostMapping("/api/v1/uploads/performance")
	public ResponseEntity<UploadBatchResponse> uploadPerformance(
			@RequestParam("advertiserId") String advertiserId,
			@RequestPart("file") MultipartFile file) {
		UploadBatch batch = uploadService.initiatePerformanceUpload(advertiserId, file);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(UploadBatchResponse.from(batch));
	}

	@PostMapping("/api/v1/uploads/journey")
	public ResponseEntity<UploadBatchResponse> uploadJourney(
			@RequestParam("advertiserId") String advertiserId,
			@RequestPart("file") MultipartFile file) {
		UploadBatch batch = uploadService.initiateJourneyUpload(advertiserId, file);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(UploadBatchResponse.from(batch));
	}

	@GetMapping("/api/v1/uploads")
	public Page<UploadBatchResponse> listUploads(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
		int clampedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		return uploadService.listBatches(PageRequest.of(page, clampedSize)).map(UploadBatchResponse::from);
	}

	@GetMapping("/api/v1/uploads/{batchId}")
	public UploadBatchResponse getUpload(@PathVariable Long batchId) {
		return UploadBatchResponse.from(uploadService.getBatch(batchId));
	}

	@GetMapping("/api/v1/uploads/{batchId}/errors")
	public List<UploadErrorResponse> getUploadErrors(@PathVariable Long batchId) {
		return uploadService.getErrors(batchId).stream().map(UploadErrorResponse::from).toList();
	}

	@PostMapping("/api/v1/uploads/{batchId}/confirm-overwrite")
	public UploadBatchResponse confirmOverwrite(@PathVariable Long batchId) {
		return UploadBatchResponse.from(uploadService.confirmOverwrite(batchId));
	}

	@PostMapping("/api/v1/uploads/{batchId}/cancel")
	public UploadBatchResponse cancel(@PathVariable Long batchId) {
		return UploadBatchResponse.from(uploadService.cancel(batchId));
	}

}
