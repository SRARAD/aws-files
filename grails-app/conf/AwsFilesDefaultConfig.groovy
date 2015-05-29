grails {
	plugin {
		awsfiles {
			cache = true
			cacheLocation = 'cache'
			rangeSupport = false
			refreshInterval = 300000
			inline = ['txt', 'pdf', 'html']
			transform = ['html': 'md']
			bufferSize = 1000000
			encrypt = false
			cacheSync = false
		}
	}
}