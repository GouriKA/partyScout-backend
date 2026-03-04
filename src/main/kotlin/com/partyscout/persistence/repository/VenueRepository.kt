package com.partyscout.persistence.repository

import com.partyscout.persistence.entity.VenueEntity
import org.springframework.data.jpa.repository.JpaRepository

interface VenueRepository : JpaRepository<VenueEntity, String>
