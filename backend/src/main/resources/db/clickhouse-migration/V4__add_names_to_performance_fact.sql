-- PRD 11.2는 advertiser_name/campaign_name/ad_group_name/ad_name도 성과 데이터 필드로 명시한다.
-- Master Upsert(PRD 11.10, 5.3 "최신 date 기준 표시 이름")에 필요하므로 누락분을 추가한다.
ALTER TABLE performance_fact
    ADD COLUMN advertiser_name String AFTER advertiser_id,
    ADD COLUMN campaign_name String AFTER campaign_id,
    ADD COLUMN ad_group_name String AFTER ad_group_id,
    ADD COLUMN ad_name String AFTER ad_id;
