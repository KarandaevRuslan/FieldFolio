package com.karandaev.fieldfolio.core.data.repository

import com.karandaev.fieldfolio.core.domain.repository.InspectionReportRepository
import com.karandaev.fieldfolio.core.library.ReportLibrary

/** Compatibility facade; all report access now passes through the protected virtual library. */
class LocalInspectionReportRepository(library: ReportLibrary) :
  InspectionReportRepository by library
