package com.sra.awsfiles

class CacheRefreshJob {

	def CacheService

	def execute() {
		CacheService.updateCache()
	}
}
