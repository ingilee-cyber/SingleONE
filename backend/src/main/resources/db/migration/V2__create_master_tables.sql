CREATE TABLE advertiser (
    advertiser_id   VARCHAR(100) NOT NULL,
    advertiser_name VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (advertiser_id)
) ENGINE = InnoDB;

CREATE TABLE campaign_master (
    advertiser_id         VARCHAR(100) NOT NULL,
    media                 VARCHAR(20)  NOT NULL,
    campaign_id           VARCHAR(100) NOT NULL,
    latest_name           VARCHAR(255) NOT NULL,
    latest_source_date    DATE NULL,
    latest_upload_batch_id BIGINT NULL,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (advertiser_id, media, campaign_id),
    CONSTRAINT chk_campaign_master_media CHECK (media IN ('META', 'TIKTOK', 'GOOGLE', 'NAVER', 'CRITEO')),
    CONSTRAINT fk_campaign_master_advertiser FOREIGN KEY (advertiser_id) REFERENCES advertiser (advertiser_id)
) ENGINE = InnoDB;

CREATE TABLE ad_group_master (
    advertiser_id         VARCHAR(100) NOT NULL,
    media                 VARCHAR(20)  NOT NULL,
    campaign_id           VARCHAR(100) NOT NULL,
    ad_group_id           VARCHAR(100) NOT NULL,
    latest_name           VARCHAR(255) NOT NULL,
    latest_source_date    DATE NULL,
    latest_upload_batch_id BIGINT NULL,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (advertiser_id, media, campaign_id, ad_group_id),
    CONSTRAINT chk_ad_group_master_media CHECK (media IN ('META', 'TIKTOK', 'GOOGLE', 'NAVER', 'CRITEO')),
    CONSTRAINT fk_ad_group_master_campaign FOREIGN KEY (advertiser_id, media, campaign_id)
        REFERENCES campaign_master (advertiser_id, media, campaign_id)
) ENGINE = InnoDB;

CREATE TABLE ad_master (
    advertiser_id         VARCHAR(100) NOT NULL,
    media                 VARCHAR(20)  NOT NULL,
    campaign_id           VARCHAR(100) NOT NULL,
    ad_group_id           VARCHAR(100) NOT NULL,
    ad_id                 VARCHAR(100) NOT NULL,
    latest_name           VARCHAR(255) NOT NULL,
    latest_source_date    DATE NULL,
    latest_upload_batch_id BIGINT NULL,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (advertiser_id, media, campaign_id, ad_group_id, ad_id),
    CONSTRAINT chk_ad_master_media CHECK (media IN ('META', 'TIKTOK', 'GOOGLE', 'NAVER', 'CRITEO')),
    CONSTRAINT fk_ad_master_ad_group FOREIGN KEY (advertiser_id, media, campaign_id, ad_group_id)
        REFERENCES ad_group_master (advertiser_id, media, campaign_id, ad_group_id)
) ENGINE = InnoDB;
