package com.singleone.backend.advertiser;

import java.util.Comparator;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.singleone.backend.domain.advertiser.Advertiser;
import com.singleone.backend.domain.advertiser.AdvertiserRepository;

/**
 * 전역 광고주 선택 UI용 목록 API. 성과 업로드가 SUCCESS되면 Master Upsert가 advertiser 행을
 * 항상 만들어두므로(PRD 11.10), advertiser 테이블 전체가 곧 "실제 데이터가 있는 광고주 목록"이다.
 */
@RestController
public class AdvertiserController {

	private final AdvertiserRepository advertiserRepository;

	public AdvertiserController(AdvertiserRepository advertiserRepository) {
		this.advertiserRepository = advertiserRepository;
	}

	@GetMapping("/api/v1/advertisers")
	public List<AdvertiserResponse> listAdvertisers() {
		return advertiserRepository.findAll().stream()
			.sorted(Comparator.comparing(Advertiser::getAdvertiserId))
			.map(a -> new AdvertiserResponse(a.getAdvertiserId(), a.getAdvertiserName()))
			.toList();
	}

}
