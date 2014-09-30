class UrlMappings {

	static mappings = {
        "/$controller/$action?/$id?(.$format)?"{
            constraints {
                // apply constraints here
            }
        }
		
		"/file/$path**"(controller:"file/get")
        "/"(view:"/index")
        "500"(view:'/error')
	}
}
