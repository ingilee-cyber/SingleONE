package com.singleone.backend.upload.master;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.singleone.backend.domain.advertiser.Advertiser;
import com.singleone.backend.domain.advertiser.AdvertiserRepository;
import com.singleone.backend.domain.common.Media;
import com.singleone.backend.domain.master.AdGroupMaster;
import com.singleone.backend.domain.master.AdGroupMasterId;
import com.singleone.backend.domain.master.AdGroupMasterRepository;
import com.singleone.backend.domain.master.AdMaster;
import com.singleone.backend.domain.master.AdMasterId;
import com.singleone.backend.domain.master.AdMasterRepository;
import com.singleone.backend.domain.master.CampaignMaster;
import com.singleone.backend.domain.master.CampaignMasterId;
import com.singleone.backend.domain.master.CampaignMasterRepository;
import com.singleone.backend.upload.performance.PerformanceFactStore;

/**
 * PRD 11.10 Master Upsert, PRD 5.3 "최신 date, 동일 date면 최신 SUCCESS upload batch" 규칙.
 * 성과 데이터 업로드가 SUCCESS가 될 때만 호출한다.
 */
@Service
public class MasterUpsertService {

	private final PerformanceFactStore performanceFactStore;
	private final AdvertiserRepository advertiserRepository;
	private final CampaignMasterRepository campaignMasterRepository;
	private final AdGroupMasterRepository adGroupMasterRepository;
	private final AdMasterRepository adMasterRepository;

	public MasterUpsertService(PerformanceFactStore performanceFactStore, AdvertiserRepository advertiserRepository,
			CampaignMasterRepository campaignMasterRepository, AdGroupMasterRepository adGroupMasterRepository,
			AdMasterRepository adMasterRepository) {
		this.performanceFactStore = performanceFactStore;
		this.advertiserRepository = advertiserRepository;
		this.campaignMasterRepository = campaignMasterRepository;
		this.adGroupMasterRepository = adGroupMasterRepository;
		this.adMasterRepository = adMasterRepository;
	}

	@Transactional
	public void upsertFromPerformanceBatch(long uploadBatchId, String advertiserId) {
		upsertAdvertiser(uploadBatchId, advertiserId);
		upsertCampaigns(uploadBatchId, advertiserId);
		upsertAdGroups(uploadBatchId, advertiserId);
		upsertAds(uploadBatchId, advertiserId);
	}

	private void upsertAdvertiser(long uploadBatchId, String advertiserId) {
		PerformanceFactStore.LatestName candidate = performanceFactStore.latestAdvertiserName(uploadBatchId);
		if (candidate == null) {
			return;
		}
		Advertiser advertiser = advertiserRepository.findById(advertiserId)
			.orElseGet(() -> new Advertiser(advertiserId, candidate.name()));
		if (isNewerCandidate(candidate.date(), uploadBatchId, advertiser.getLatestSourceDate(), advertiser.getLatestUploadBatchId())) {
			advertiser.setAdvertiserName(candidate.name());
			advertiser.setLatestSourceDate(candidate.date());
			advertiser.setLatestUploadBatchId(uploadBatchId);
		}
		advertiserRepository.saveAndFlush(advertiser);
	}

	private void upsertCampaigns(long uploadBatchId, String advertiserId) {
		for (PerformanceFactStore.CampaignCandidate candidate : performanceFactStore.latestCampaignNames(uploadBatchId)) {
			CampaignMasterId id = new CampaignMasterId(advertiserId, Media.valueOf(candidate.media()), candidate.campaignId());
			CampaignMaster master = campaignMasterRepository.findById(id)
				.orElseGet(() -> new CampaignMaster(id, candidate.name()));
			if (isNewerCandidate(candidate.date(), uploadBatchId, master.getLatestSourceDate(), master.getLatestUploadBatchId())) {
				master.setLatestName(candidate.name());
				master.setLatestSourceDate(candidate.date());
				master.setLatestUploadBatchId(uploadBatchId);
			}
			campaignMasterRepository.saveAndFlush(master);
		}
	}

	private void upsertAdGroups(long uploadBatchId, String advertiserId) {
		for (PerformanceFactStore.AdGroupCandidate candidate : performanceFactStore.latestAdGroupNames(uploadBatchId)) {
			AdGroupMasterId id = new AdGroupMasterId(advertiserId, Media.valueOf(candidate.media()),
				candidate.campaignId(), candidate.adGroupId());
			AdGroupMaster master = adGroupMasterRepository.findById(id)
				.orElseGet(() -> new AdGroupMaster(id, candidate.name()));
			if (isNewerCandidate(candidate.date(), uploadBatchId, master.getLatestSourceDate(), master.getLatestUploadBatchId())) {
				master.setLatestName(candidate.name());
				master.setLatestSourceDate(candidate.date());
				master.setLatestUploadBatchId(uploadBatchId);
			}
			adGroupMasterRepository.saveAndFlush(master);
		}
	}

	private void upsertAds(long uploadBatchId, String advertiserId) {
		for (PerformanceFactStore.AdCandidate candidate : performanceFactStore.latestAdNames(uploadBatchId)) {
			AdMasterId id = new AdMasterId(advertiserId, Media.valueOf(candidate.media()),
				candidate.campaignId(), candidate.adGroupId(), candidate.adId());
			AdMaster master = adMasterRepository.findById(id)
				.orElseGet(() -> new AdMaster(id, candidate.name()));
			if (isNewerCandidate(candidate.date(), uploadBatchId, master.getLatestSourceDate(), master.getLatestUploadBatchId())) {
				master.setLatestName(candidate.name());
				master.setLatestSourceDate(candidate.date());
				master.setLatestUploadBatchId(uploadBatchId);
			}
			adMasterRepository.saveAndFlush(master);
		}
	}

	/**
	 * PRD 5.3: 최신 date가 우선이고, date가 같으면 더 최근에 SUCCESS된 batch(= batch_id가 더 큰 쪽)가 우선한다.
	 */
	private boolean isNewerCandidate(LocalDate candidateDate, long candidateBatchId, LocalDate storedDate, Long storedBatchId) {
		if (storedDate == null) {
			return true;
		}
		if (candidateDate.isAfter(storedDate)) {
			return true;
		}
		if (candidateDate.isEqual(storedDate)) {
			return storedBatchId == null || candidateBatchId > storedBatchId;
		}
		return false;
	}

}
