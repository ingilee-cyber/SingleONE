-- PRD 5.3: Advertiser의 "latest advertiser_name"(12.1)도 Campaign/AdGroup/Ad Master와 동일하게
-- "최신 date, 동일 date면 최신 SUCCESS batch" 규칙을 따라야 하므로 추적 컬럼을 추가한다.
ALTER TABLE advertiser
    ADD COLUMN latest_source_date DATE NULL AFTER advertiser_name,
    ADD COLUMN latest_upload_batch_id BIGINT NULL AFTER latest_source_date;
