package com.partyscout.persistence.repository

import com.partyscout.persistence.entity.SearchEntity
import org.springframework.data.jpa.repository.JpaRepository

interface SearchRepository : JpaRepository<SearchEntity, Long>
