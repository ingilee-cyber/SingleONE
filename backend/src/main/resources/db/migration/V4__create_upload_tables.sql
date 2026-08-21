CREATE TABLE upload_batch (
    upload_batch_id BIGINT AUTO_INCREMENT,
    advertiser_id   VARCHAR(100) NOT NULL,
    type            VARCHAR(20)  NOT NULL,
    filename        VARCHAR(255) NOT NULL,
    status          VARCHAR(40)  NOT NULL,
    total_rows      BIGINT NULL,
    success_rows    BIGINT NULL,
    error_rows      BIGINT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (upload_batch_id),
    CONSTRAINT chk_upload_batch_type CHECK (type IN ('PERFORMANCE', 'JOURNEY')),
    CONSTRAINT chk_upload_batch_status CHECK (status IN (
        'VALIDATING', 'DUPLICATE_CONFIRMATION_REQUIRED', 'IMPORTING', 'SUCCESS', 'FAILED', 'CANCELLED'
    )),
    CONSTRAINT fk_upload_batch_advertiser FOREIGN KEY (advertiser_id) REFERENCES advertiser (advertiser_id)
) ENGINE = InnoDB;

CREATE TABLE upload_error (
    id              BIGINT AUTO_INCREMENT,
    upload_batch_id BIGINT NOT NULL,
    row_no          BIGINT NOT NULL,
    error_code      VARCHAR(100) NOT NULL,
    message         VARCHAR(1000) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_upload_error_batch FOREIGN KEY (upload_batch_id) REFERENCES upload_batch (upload_batch_id)
) ENGINE = InnoDB;
