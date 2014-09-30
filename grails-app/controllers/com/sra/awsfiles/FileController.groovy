package com.sra.awsfiles

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.S3Object

class FileController {
	
	def test() {
		println 'Here'
	}

	def rangeSupport=false
	def copyToCache=false
	def grailsApplication

	static def typeMap=['doc':'application/msword',
		'png':'image/png',
		'jpg':'image/jpg',
		'pdf':'application/pdf',
		'docx':'application/msword',
		'webm':'video/webm',
		'wmv':'video/x-ms-wmv',
		'html':'text/html',
		'md':'text/x-markdown',
		'mp4':'video/mp4']


	/* default behavior should be to deliver from cache if there or deliver from S3 if not */

	static def typeToMime(String type) {
		def mapped=typeMap[type]
		if (mapped!=null) return(mapped)
		return('application/octet-stream')
	}

	static def fileNameToMime(String filename) {
		int pos=filename.lastIndexOf(".")
		if (pos==-1) return('application/octet-stream')
		String ext=filename.substring(pos+1).toLowerCase()
		return(typeToMime(ext))
	}

	//prepare for S3 direct fetch
	def get(String path) {
		try {
			//process Range header if present
			String range=request.getHeader("Range")
			long r0=-1;
			long r1=0;
			long rsize=0;
			if (rangeSupport) {
				if (range!=null) {
					if (range.startsWith("bytes")) {
						range=range.substring(range.indexOf("=")+1)
					}
					if (!range.equals("0-")) {
						int p=range.lastIndexOf("/")
						if (p>-1) range=range.substring(0,pos) //get rid of total if present
						int dash=range.indexOf("-")
						r0=Long.parseLong(range.substring(0,dash))
						r1=Long.parseLong(range.substring(dash+1))
						rsize=r1-r0
						println("r0="+r0)
						println("r1="+r1)
					}
					println("range="+range)
				}
			}
			//get filename
			int pos=path.lastIndexOf("/")
			String filename
			if (pos==-1) {
				filename=path
			} else {
				filename=path.substring(pos+1)
			}
			//set content type
			response.contentType=fileNameToMime(filename)
			//specify content disposition
			//inline = play in browser
			//attachment = download
			String type="attachment"
			if (filename.toLowerCase().endsWith(".txt") || filename.toLowerCase().endsWith(".pdf") || filename.toLowerCase().endsWith(".html")) {
				type="inline"
			}
			response.setHeader("Content-disposition",type+";filename="+filename)
			if (filename.toLowerCase().endsWith(".html")) {
				int posdot=path.lastIndexOf(".")
				def mdFile=getCacheFile(path.substring(0,posdot)+".md")
				if (mdFile.exists()) {
					return(["md":mdFile.text])
				}
			}
			def cacheFile=getCacheFile(path)
			if (cacheFile.exists()) {
				if (filename.toLowerCase().endsWith(".html")) {
					//special case for inlining html
					return(["md":cacheFile.text])
				}
				if (r0==-1) { //deliver whole file
					response.setHeader("Content-Length",cacheFile.length().toString())
					int bufsize=500000 //500K
					if (cacheFile.length()<bufsize) bufsize=cacheFile.length()
					byte[] buf=new byte[bufsize]
					int len=-1
					InputStream in0=new FileInputStream(cacheFile)
					while((len=in0.read(buf,0,bufsize))>-1) {
						if (len>0) {
							response.outputStream.write(buf,0,len) //full write
						}
					}
					response.outputStream.flush()
					//response.outputStream<<cacheFile.readBytes() //this allocates a byte array the size of the file -- not so good
					response.outputStream.flush()
					return
				} else {
					//TODO: handle reading partial files from cache
				}
			}
			//specify content length
			AmazonS3Client s3=new AmazonS3Client()
			try {
				S3Object file=s3.getObject("rad-content",path)
				Date mod=file.getObjectMetadata().getLastModified()
				long size=file.getObjectMetadata().getContentLength()
				if (r0>-1) {
					response.setHeader("Content-Length",rsize.toString())
				} else {
					response.setHeader("Content-Length",size.toString())
				}
				//println("path="+path+" mod date="+mod+" size="+size)
				InputStream in0=file.getObjectContent()
				if (r0>0) {
					in0.skip(r0) //possible skip (if supported)
				}
				int bufsize=500000 //500K
				if (size<bufsize) bufsize=size //or size of file whichever is smaller
				byte[] buf=new byte[bufsize]
				int len=-1
				long cnt=0
				FileOutputStream fout=null
				if (!rangeSupport && copyToCache) {
					fout=new FileOutputStream(cacheFile)
				}
				while((len=in0.read(buf,0,bufsize))>-1) {
					if (len>0) {
						if (r0>-1 && (cnt+len)>r1) {
							response.outputStream.write(buf,0,r1-cnt) //partial write
						} else {
							response.outputStream.write(buf,0,len) //full write
							if (fout!=null) fout.write(buf,0,len)
						}
					}
					cnt+=len
					if (r0>-1 && cnt>r1) break //bail if out of range
				}
				response.outputStream.flush()
				if (fout!=null) fout.close()
			} catch (Exception e) {
				println("S3 Retrieval Failed For:"+path)
				//e.printStackTrace()
			}
		} catch (Exception e) {
			println e
			println("File Retrieval Failed For:"+path)
		}
	}

	def getCacheFile(String path) {
		String cachedir = grailsApplication.mergedConfig.grails.plugin.awsfiles.cacheLocation;
		def dir0=null
		if (cachedir.startsWith("/") || cachedir.indexOf(":/")==1) {
			dir0=cachedir
		} else {
			dir0=grailsApplication.parentContext.getResource(cachedir).file.toString()
		}
		def dir
		int pos=path.lastIndexOf("/")
		if (pos>-1) {
			dir=new File(dir0+"/"+path.substring(0,pos))
		} else {
			dir=new File(dir0)
		}
		if (!dir.exists()) {
			dir.mkdirs()
		}
		def file=new File(dir0+"/"+path)
		return(file)
	}
}
