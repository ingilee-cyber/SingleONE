package com.singleone.backend.domain.filter;

import com.singleone.backend.domain.common.Media;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InternalMediaFilterRepository extends JpaRepository<InternalMediaFilter, Media> {
}
