package com.singleone.backend.advertiser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.singleone.backend.domain.advertiser.Advertiser;
import com.singleone.backend.domain.advertiser.AdvertiserRepository;

/**
 * 전역 광고주 선택 UI용 목록 API. {@link AdvertiserController}는 Repository 하나에만
 * 의존하는 얇은 컨트롤러라 Mockito로 의존성을 목 처리한 순수 단위 테스트로 검증한다(Docker
 * 불필요, UploadServiceFileSizeLimitTest와 동일한 패턴).
 */
class AdvertiserControllerTest {

	private final AdvertiserRepository advertiserRepository = mock(AdvertiserRepository.class);
	private final AdvertiserController controller = new AdvertiserController(advertiserRepository);

	@Test
	void returnsAdvertisersSortedByAdvertiserId() {
		when(advertiserRepository.findAll()).thenReturn(List.of(
			new Advertiser("zzz-brand", "ZZZ Brand"),
			new Advertiser("abc-brand", "ABC Brand")));

		List<AdvertiserResponse> result = controller.listAdvertisers();

		assertThat(result).containsExactly(
			new AdvertiserResponse("abc-brand", "ABC Brand"),
			new AdvertiserResponse("zzz-brand", "ZZZ Brand"));
	}

	@Test
	void returnsEmptyListWhenNoAdvertiserHasUploadedData() {
		when(advertiserRepository.findAll()).thenReturn(List.of());

		assertThat(controller.listAdvertisers()).isEmpty();
	}

}
