package com.sra.awsfiles

import grails.transaction.Transactional
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectListing
import com.amazonaws.services.s3.model.S3Object
import com.sra.awsfiles.CacheRefreshJob

@Transactional
class CacheService {

	def grailsApplication
	def inProgress=false

	def updateCache() {
		def results="" 
		long t0=System.currentTimeMillis()
		def cachedir=getLoc();
		def dir1=null
		if (cachedir.startsWith("/") || cachedir.indexOf(":/")==1) {
			dir1=new File(cachedir)
		} else {
			dir1=grailsApplication.parentContext.getResource(cachedir).file
		}
		def dir0=dir1.toString()
		results+="<li>Using Local Cache Directory: "+dir0+"</li>"
		results+="<li>Using S3 Bucket: "+grailsApplication.mergedConfig.grails.plugin.awsfiles.bucket+"</li>"
		println("Calling updateCache...")
		if (inProgress) {
			println("Update cache already in progress...")
			results+="<li>Update cache already in progress -- request aborted</li>"
			return results//only allow one scan in progress
		}
		inProgress=true
		def s3=new AmazonS3Client()
		ObjectListing list=null;
		try {
			while(true) {
				list=s3.listObjects(grailsApplication.mergedConfig.grails.plugin.awsfiles.bucket)
				results+=processObjects(list)
				if (list.isTruncated()) {
					list=s3.listNextBatchOfObjects(list)
				} else {
					break;
				}
			}
		} catch (Exception e) {
			e.printStackTrace()
			results+="<li><pre>"
			results+=e.toString()
			results+="</pre></li>"
		} finally {
			inProgress=false
		}
		long t1=System.currentTimeMillis()
		long dif=(t1-t0)
		float dif1=((int)(dif/10))/100
		results+="<li>Total time to sweep S3 and compare: "+dif1+" seconds</li>"
		return(results)
	}

	def getCacheFile(String path) {
		def cachedir=getLoc();
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

	def getCacheInfo(String path) {
		def cachedir=getLoc();
		def dir1=null
		if (cachedir.startsWith("/") || cachedir.indexOf(":/")==1) {
			dir1=new File(cachedir)
		} else {
			dir1=grailsApplication.parentContext.getResource(cachedir).file
		}
		def dir0=dir1.toString()
		def dir
		int pos=path.lastIndexOf("/")
		String filedir=null
		String stem=null
		if (pos>-1) {
			filedir=dir0+"/"+path.substring(0,pos)
			dir=new File(filedir)
			stem=path.substring(pos+1)
		} else {
			filedir=dir0
			dir=new File(filedir)
			stem=path
		}
		if (!dir.exists()) {
			dir.mkdirs()
		}
		["cacheDirFile":dir1,"filedir":filedir,"stem":stem,"file":new File(dir0+"/"+path)]
	}

	def processObjects(ObjectListing list) {
		def results=""
		list.getObjectSummaries().each { obj ->
			String key=obj.getKey()
			if (!key.endsWith("/")) {
				long size=obj.getSize()
				Date mod=obj.getLastModified()
				File cacheFile=getCacheFile(key)
				String local=""
				if (cacheFile.exists()) {
					long csize=cacheFile.length()
					long cmod=cacheFile.lastModified()
					if (csize!=size || mod.getTime()>cmod) {
						if (csize!=size) local+=" size mismatch"
						if (mod.getTime()>cmod) local+=", too old"
						local+=" ***needs refresh"
						cacheFile.delete() //delete it as soon as we notice
						println(key+" size="+size+" "+local)
						//results+="<li>"+key+" size="+size+" "+local+"</li>"
						results+=refreshObject(key,size)
					} else {
						local="cache up to date"
					}
				} else {
					local="no cache"
					println(key+" size="+size+" "+local)
					//results+="<li>"+key+" size="+size+" "+local+"</li>"
					results+=refreshObject(key,size)
				}
			}
		}
		return(results)
	}

	def refreshObject(String key,long size) {
		def bucket = grailsApplication.mergedConfig.grails.plugin.awsfiles.bucket;
		def results=""
		def s3=new AmazonS3Client()
		try {
			S3Object file=s3.getObject(bucket,key)
			InputStream in0=file.getObjectContent()
			int bufsize=grailsApplication.mergedConfig.grails.plugin.awsfiles.bufferSize
			if (size<bufsize) bufsize=size //or length of object if smaller
			byte[] buf=new byte[bufsize]
			int len=-1
			def ci=getCacheInfo(key)
			File tempFile=new File(ci.filedir+"/"+ci.stem+".tmp") //same dir with a .tmp on the end
			if (tempFile.exists()) tempFile.delete() //just in case
			FileOutputStream fout=new FileOutputStream(tempFile)
			while((len=in0.read(buf,0,bufsize))>-1) {
				if (len>0) {
					fout.write(buf,0,len)
				}
			}
			fout.close()
			//atomic switch of file
			tempFile.renameTo(ci.file) //.tmp file renamed to original (which should already be deleted if it was present)
			println(key+" copied from S3 to Cache...")
			results+="<li>"+key+" copied from S3 to Cache..."+"</li>"
		} catch (Exception e) {
			e.printStackTrace()
			println("S3 Retrieval Failed For:"+key)
			results+="<li>S3 Retrieval Failed For:"+key+"</li>"
		}
		return(results)
	}
	
	def getLoc() {
		grailsApplication.mergedConfig.grails.plugin.awsfiles.cacheLocation
	}
	
	def startJob() {
		CacheRefreshJob.schedule(grailsApplication.mergedConfig.grails.plugin.awsfiles.refreshInterval)
	}
}
