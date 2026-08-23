package com.singleone.backend.upload;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import com.singleone.backend.AbstractIntegrationTest;
import com.singleone.backend.domain.advertiser.AdvertiserRepository;
import com.singleone.backend.domain.common.Media;
import com.singleone.backend.domain.master.AdGroupMasterId;
import com.singleone.backend.domain.master.AdGroupMasterRepository;
import com.singleone.backend.domain.master.AdMasterId;
import com.singleone.backend.domain.master.AdMasterRepository;
import com.singleone.backend.domain.master.CampaignMasterId;
import com.singleone.backend.domain.master.CampaignMasterRepository;
import com.singleone.backend.domain.upload.UploadBatch;
import com.singleone.backend.domain.upload.UploadStatus;

/**
 * PRD 11장 업로드 플로우(정상 처리/기존 데이터 중복/중복 확인/취소/Master Upsert)를
 * Testcontainers로 검증한다. 행 단위 검증 규칙(필수 컬럼/날짜/숫자/음수/미지원 media/파일 내
 * 중복)은 PerformanceRowParserTest/JourneyRowParserTest에서 이미 커버하므로 반복하지 않는다.
 */
class UploadFlowIntegrationTest extends AbstractIntegrationTest {

	private static final String PERFORMANCE_HEADER =
		"date,advertiser_id,advertiser_name,media,campaign_id,campaign_name,ad_group_id,ad_group_name,"
			+ "ad_id,ad_name,impressions,clicks,cost,add_to_cart,purchases,purchase_revenue";

	@Autowired
	private UploadService uploadService;

	@Autowired
	private AdvertiserRepository advertiserRepository;

	@Autowired
	private CampaignMasterRepository campaignMasterRepository;

	@Autowired
	private AdGroupMasterRepository adGroupMasterRepository;

	@Autowired
	private AdMasterRepository adMasterRepository;

	@Autowired
	@Qualifier("clickHouseJdbcTemplate")
	private JdbcTemplate clickHouseJdbcTemplate;

	@Test
	void performanceCsvUploadSucceedsAndUpsertsMaster() {
		String advertiserId = "adv-flow-1";
		UploadBatch batch = uploadService.initiatePerformanceUpload(advertiserId,
			csvFile("perf.csv", performanceCsv(advertiserId, "2026-08-01")));

		UploadBatch result = waitForTerminalStatus(batch.getUploadBatchId());

		assertThat(result.getStatus()).isEqualTo(UploadStatus.SUCCESS);
		assertThat(result.getSuccessRows()).isEqualTo(1L);
		assertThat(advertiserRepository.findById(advertiserId).orElseThrow().getAdvertiserName()).isEqualTo("플로우광고주");
		assertThat(campaignMasterRepository.findById(new CampaignMasterId(advertiserId, Media.META, "camp-flow"))
			.orElseThrow().getLatestName()).isEqualTo("캠페인플로우");
		assertThat(adGroupMasterRepository.findById(new AdGroupMasterId(advertiserId, Media.META, "camp-flow", "ag-flow"))
			.orElseThrow().getLatestName()).isEqualTo("광고그룹플로우");
		assertThat(adMasterRepository.findById(new AdMasterId(advertiserId, Media.META, "camp-flow", "ag-flow", "ad-flow"))
			.orElseThrow().getLatestName()).isEqualTo("광고플로우");
	}

	@Test
	void performanceXlsxUploadSucceeds() throws IOException {
		String advertiserId = "adv-flow-2";
		UploadBatch batch = uploadService.initiatePerformanceUpload(advertiserId,
			new MockMultipartFile("file", "perf.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
				performanceXlsx(advertiserId, "2026-08-02")));

		UploadBatch result = waitForTerminalStatus(batch.getUploadBatchId());

		assertThat(result.getStatus()).isEqualTo(UploadStatus.SUCCESS);
		assertThat(result.getSuccessRows()).isEqualTo(1L);
	}

	@Test
	void journeyCsvUploadSucceeds() {
		String advertiserId = "adv-flow-3";
		String csv = "event_id,advertiser_id,anonymous_user_id,event_timestamp,event_type,media,campaign_id,ad_group_id,ad_id,order_id,purchase_revenue\n"
			+ "evt-1," + advertiserId + ",user-1,2026-08-06T00:00:00Z,CLICK,META,camp-flow,ag-flow,ad-flow,,\n"
			+ "evt-2," + advertiserId + ",user-1,2026-08-06T01:00:00Z,PURCHASE,,,,,order-1,50000\n";

		UploadBatch batch = uploadService.initiateJourneyUpload(advertiserId, csvFile("journey.csv", csv));

		UploadBatch result = waitForTerminalStatus(batch.getUploadBatchId());

		assertThat(result.getStatus()).isEqualTo(UploadStatus.SUCCESS);
		assertThat(result.getSuccessRows()).isEqualTo(2L);
	}

	@Test
	void duplicateAgainstExistingSuccessRequiresConfirmationThenConfirms() {
		String advertiserId = "adv-flow-4";
		String csv = performanceCsv(advertiserId, "2026-08-03");

		UploadBatch first = uploadService.initiatePerformanceUpload(advertiserId, csvFile("perf.csv", csv));
		waitForTerminalStatus(first.getUploadBatchId());

		UploadBatch second = uploadService.initiatePerformanceUpload(advertiserId, csvFile("perf.csv", csv));
		UploadBatch pending = waitForTerminalStatus(second.getUploadBatchId());
		assertThat(pending.getStatus()).isEqualTo(UploadStatus.DUPLICATE_CONFIRMATION_REQUIRED);

		UploadBatch confirmed = uploadService.confirmOverwrite(second.getUploadBatchId());
		assertThat(confirmed.getStatus()).isEqualTo(UploadStatus.SUCCESS);
	}

	@Test
	void cancelDeletesStagedDataAndMarksCancelled() {
		String advertiserId = "adv-flow-5";
		String csv = performanceCsv(advertiserId, "2026-08-04");

		UploadBatch first = uploadService.initiatePerformanceUpload(advertiserId, csvFile("perf.csv", csv));
		waitForTerminalStatus(first.getUploadBatchId());

		UploadBatch second = uploadService.initiatePerformanceUpload(advertiserId, csvFile("perf.csv", csv));
		waitForTerminalStatus(second.getUploadBatchId());

		UploadBatch cancelled = uploadService.cancel(second.getUploadBatchId());

		assertThat(cancelled.getStatus()).isEqualTo(UploadStatus.CANCELLED);
		assertThat(countPerformanceRowsByBatch(second.getUploadBatchId())).isZero();
	}

	@Test
	void invalidRowFailsWholeBatchWithRowSpecificErrorsAndNoStagedData() {
		String advertiserId = "adv-flow-6";
		String csv = PERFORMANCE_HEADER + "\n"
			+ "2026-08-05," + advertiserId + ",플로우광고주,META,camp-flow,캠페인플로우,ag-flow,광고그룹플로우,ad-flow,광고플로우,100,10,-1,1,1,1000\n";

		UploadBatch batch = uploadService.initiatePerformanceUpload(advertiserId, csvFile("perf.csv", csv));

		UploadBatch result = waitForTerminalStatus(batch.getUploadBatchId());

		assertThat(result.getStatus()).isEqualTo(UploadStatus.FAILED);
		assertThat(uploadService.getErrors(batch.getUploadBatchId())).hasSize(1);
		assertThat(countPerformanceRowsByBatch(batch.getUploadBatchId())).isZero();
	}

	private long countPerformanceRowsByBatch(long batchId) {
		Long count = clickHouseJdbcTemplate.queryForObject(
			"SELECT count() FROM performance_fact WHERE upload_batch_id = " + batchId, Long.class);
		return count == null ? 0 : count;
	}

	private MockMultipartFile csvFile(String filename, String content) {
		return new MockMultipartFile("file", filename, "text/csv", content.getBytes(StandardCharsets.UTF_8));
	}

	private String performanceCsv(String advertiserId, String date) {
		return PERFORMANCE_HEADER + "\n"
			+ date + "," + advertiserId + ",플로우광고주,META,camp-flow,캠페인플로우,ag-flow,광고그룹플로우,ad-flow,광고플로우,100,10,5000,1,1,10000\n";
	}

	private byte[] performanceXlsx(String advertiserId, String date) throws IOException {
		try (XSSFWorkbook workbook = new XSSFWorkbook()) {
			Sheet sheet = workbook.createSheet("data");
			String[] headers = PERFORMANCE_HEADER.split(",");
			Row headerRow = sheet.createRow(0);
			for (int i = 0; i < headers.length; i++) {
				headerRow.createCell(i).setCellValue(headers[i]);
			}
			String[] values = {date, advertiserId, "플로우광고주", "META", "camp-flow", "캠페인플로우", "ag-flow",
				"광고그룹플로우", "ad-flow", "광고플로우", "100", "10", "5000", "1", "1", "10000"};
			Row dataRow = sheet.createRow(1);
			for (int i = 0; i < values.length; i++) {
				dataRow.createCell(i).setCellValue(values[i]);
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			workbook.write(out);
			return out.toByteArray();
		}
	}

	private UploadBatch waitForTerminalStatus(long batchId) {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
		while (Instant.now().isBefore(deadline)) {
			UploadBatch batch = uploadService.getBatch(batchId);
			if (batch.getStatus() != UploadStatus.VALIDATING && batch.getStatus() != UploadStatus.IMPORTING) {
				return batch;
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e);
			}
		}
		throw new IllegalStateException("업로드 처리 완료를 10초 내에 확인하지 못했습니다 (batchId=" + batchId + ")");
	}

}
