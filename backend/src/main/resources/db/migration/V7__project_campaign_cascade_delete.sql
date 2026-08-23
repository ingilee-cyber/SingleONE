-- PRD 5.1: 프로젝트 삭제 시 하위 project_campaign도 함께 제거되어야 한다.
ALTER TABLE project_campaign DROP FOREIGN KEY fk_project_campaign_project;
ALTER TABLE project_campaign
    ADD CONSTRAINT fk_project_campaign_project FOREIGN KEY (project_id)
        REFERENCES project (project_id) ON DELETE CASCADE;
