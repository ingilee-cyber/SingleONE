CREATE TABLE project (
    project_id      BIGINT AUTO_INCREMENT,
    advertiser_id   VARCHAR(100) NOT NULL,
    project_name    VARCHAR(255) NOT NULL,
    system_default  BOOLEAN NOT NULL DEFAULT FALSE,
    reference_only  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id),
    CONSTRAINT uk_project_advertiser_name UNIQUE (advertiser_id, project_name),
    CONSTRAINT fk_project_advertiser FOREIGN KEY (advertiser_id) REFERENCES advertiser (advertiser_id)
) ENGINE = InnoDB;

CREATE TABLE project_campaign (
    project_id    BIGINT NOT NULL,
    advertiser_id VARCHAR(100) NOT NULL,
    media         VARCHAR(20)  NOT NULL,
    campaign_id   VARCHAR(100) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id, advertiser_id, media, campaign_id),
    CONSTRAINT chk_project_campaign_media CHECK (media IN ('META', 'TIKTOK', 'GOOGLE', 'NAVER', 'CRITEO')),
    CONSTRAINT fk_project_campaign_project FOREIGN KEY (project_id) REFERENCES project (project_id),
    CONSTRAINT fk_project_campaign_campaign FOREIGN KEY (advertiser_id, media, campaign_id)
        REFERENCES campaign_master (advertiser_id, media, campaign_id)
) ENGINE = InnoDB;
