-- PRD 9.5(이벤트 스키마), 12.2(Journey Event)
-- PURCHASE 이벤트는 media/campaign_id/ad_group_id/ad_id가 비고, CLICK 이벤트는 order_id/purchase_revenue가 비므로 Nullable로 둔다.
CREATE TABLE IF NOT EXISTS journey_event (
    event_id          String,
    advertiser_id     String,
    anonymous_user_id String,
    event_timestamp   DateTime,
    event_type        Enum8('CLICK' = 1, 'PURCHASE' = 2),
    media             Nullable(String),
    campaign_id       Nullable(String),
    ad_group_id       Nullable(String),
    ad_id             Nullable(String),
    order_id          Nullable(String),
    purchase_revenue  Nullable(Decimal(18, 4)),
    upload_batch_id   UInt64
) ENGINE = MergeTree
PARTITION BY toYYYYMM(event_timestamp)
ORDER BY (advertiser_id, anonymous_user_id, event_timestamp);
