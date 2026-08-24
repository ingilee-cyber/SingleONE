package com.singleone.backend.upload;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import com.singleone.backend.domain.advertiser.AdvertiserRepository;
import com.singleone.backend.domain.upload.UploadBatchRepository;
import com.singleone.backend.domain.upload.UploadErrorRepository;

/**
 * PRD 11.11/AC-32: 50MB 초과 파일은 업로드 검증에서 거부된다. 크기 확인은
 * {@link UploadService#initiate}에서 DB 접근보다 먼저 일어나므로 Mockito로 의존성을 목
 * 처리한 순수 단위 테스트로 검증할 수 있다(Docker 불필요). 100만 행 초과 거부(같은 AC-32의
 * 나머지 절반)는 {@link UploadProcessor}가 실제 파일을 스트리밍하며 판단하는 로직이라 이
 * 테스트 범위 밖이며, 코드 검토로 확인함({@code UploadProcessor.java}의
 * {@code rowCount > UploadLimits.MAX_ROWS} 체크, 두 업로드 타입 모두 동일하게 적용).
 */
class UploadServiceFileSizeLimitTest {

	private final UploadBatchRepository uploadBatchRepository = mock(UploadBatchRepository.class);
	private final UploadErrorRepository uploadErrorRepository = mock(UploadErrorRepository.class);
	private final AdvertiserRepository advertiserRepository = mock(AdvertiserRepository.class);
	private final UploadProcessor uploadProcessor = mock(UploadProcessor.class);

	private final UploadService uploadService = new UploadService(uploadBatchRepository, uploadErrorRepository,
		advertiserRepository, uploadProcessor);

	@Test
	void ac32_fileOverFiftyMegabytesIsRejected() {
		MultipartFile file = mock(MultipartFile.class);
		when(file.isEmpty()).thenReturn(false);
		when(file.getSize()).thenReturn(UploadLimits.MAX_FILE_SIZE_BYTES + 1);
		when(file.getOriginalFilename()).thenReturn("big.csv");

		assertThatThrownBy(() -> uploadService.initiatePerformanceUpload("adv-1", file))
			.isInstanceOf(UploadRequestException.class)
			.hasMessageContaining("50MB");
	}

	@Test
	void fileExactlyAtTheLimitIsAccepted() {
		MultipartFile file = mock(MultipartFile.class);
		when(file.isEmpty()).thenReturn(false);
		when(file.getSize()).thenReturn(UploadLimits.MAX_FILE_SIZE_BYTES);
		when(file.getOriginalFilename()).thenReturn("ok.csv");
		when(advertiserRepository.existsById("adv-1")).thenReturn(true);

		// 크기 제한은 통과하므로 이후 advertiser 존재 확인 단계까지 진행돼야 한다(DB 저장은
		// UploadBatchRepository 목이 null을 반환해 NPE가 나므로, 크기 검증만 분리 확인하는
		// 것이 이 테스트의 목적이라 여기서 예외가 나더라도 "파일 크기" 문구만 아니면 된다).
		try {
			uploadService.initiatePerformanceUpload("adv-1", file);
		} catch (Exception e) {
			org.assertj.core.api.Assertions.assertThat(e.getMessage()).doesNotContain("50MB");
		}
	}

}
