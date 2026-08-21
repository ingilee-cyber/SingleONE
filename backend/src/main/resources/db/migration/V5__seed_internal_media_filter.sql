-- PRD 8.3: 고정 테스트 필터율. 코드 if/else 하드코딩이 아니라 Backend 비공개 내부 설정(DB)으로 관리한다.
-- 이 테이블은 어떤 Controller/DTO에도 노출하지 않는다 (CLAUDE.md Hard Rule 7).
CREATE TABLE internal_media_filter (
    media       VARCHAR(20) NOT NULL,
    filter_rate DECIMAL(6, 4) NOT NULL,
    PRIMARY KEY (media),
    CONSTRAINT chk_internal_media_filter_media CHECK (media IN ('META', 'TIKTOK', 'GOOGLE', 'NAVER', 'CRITEO'))
) ENGINE = InnoDB;

INSERT INTO internal_media_filter (media, filter_rate) VALUES
    ('META', 0.6500),
    ('TIKTOK', 0.6200),
    ('GOOGLE', 0.6900),
    ('NAVER', 0.6400),
    ('CRITEO', 0.6100);
