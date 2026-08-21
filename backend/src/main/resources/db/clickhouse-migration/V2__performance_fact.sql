-- PRD 11.2(성과 데이터 Grain/필드), 12.2(Performance Fact)
CREATE TABLE IF NOT EXISTS performance_fact (
    date              Date,
    advertiser_id     String,
    media             LowCardinality(String),
    campaign_id       String,
    ad_group_id       String,
    ad_id             String,
    impressions       UInt64,
    clicks            UInt64,
    cost              Decimal(18, 4),
    add_to_cart       UInt64,
    purchases         UInt64,
    purchase_revenue  Decimal(18, 4),
    upload_batch_id   UInt64
) ENGINE = MergeTree
PARTITION BY toYYYYMM(date)
ORDER BY (advertiser_id, media, campaign_id, ad_group_id, ad_id, date);
