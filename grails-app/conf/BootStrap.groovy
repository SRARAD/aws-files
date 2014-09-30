class BootStrap {
	
	def cacheService

	def init = { servletContext ->
		log.info 'Boostrapping'
		
		cacheService.startJob()
	}
	def destroy = {
		
	}
}
