package com.sra.awsfiles

import grails.transaction.Transactional
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectListing
import com.amazonaws.services.s3.model.S3Object
import com.sra.awsfiles.CacheRefreshJob

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.AmazonS3EncryptionClient
import com.amazonaws.services.s3.model.EncryptionMaterials
import com.amazonaws.services.s3.model.S3Object
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.apache.commons.codec.binary.Base64
import groovy.io.FileType

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
		def dir0=dir1.path
		results+="<li>Using Local Cache Directory: "+dir0+"</li>"
		results+="<li>Using S3 Bucket: "+grailsApplication.mergedConfig.grails.plugin.awsfiles.bucket+"</li>"
		println("Calling updateCache...")
		if (inProgress) {
			println("Update cache already in progress...")
			results+="<li>Update cache already in progress -- request aborted</li>"
			return results//only allow one scan in progress
		}
		inProgress=true
		def s3=getS3Client();
		ObjectListing list=null;
		Collection<String> files = [];
		try {
			while(true) {
				list=s3.listObjects(grailsApplication.mergedConfig.grails.plugin.awsfiles.bucket)
				Map resultMap = processObjects(list);
				results += resultMap.results;
				files += resultMap.files
				if (list.isTruncated()) {
					list=s3.listNextBatchOfObjects(list);
				} else {
					break;
				}
			}
			if (grailsApplication.mergedConfig.grails.plugin.awsfiles.cacheSync) {
				crawlLocalCache(files);
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
	
	void crawlLocalCache(Collection<String> files) {
		File root = getRootDir();
		String rootDir = root.path;
		root.eachFileRecurse (FileType.FILES) { file ->
			String path = (file.path - (rootDir))[1..-1];
			if (!files.contains(path.replaceAll('\\\\', '/'))) {
				println path + ' deleting, no longer in S3';
				file.delete();
			}
		}
		int num = 1;
		while (num != 0) {
			num = cleanEmptyDirs(root);
		}
	}
	
	int cleanEmptyDirs(File root) {
		Collection<File> empty = [];
		root.eachFileRecurse (FileType.DIRECTORIES) { dir ->
			if (dir.list().size() == 0) {
				empty.push(dir);
			}
		}
		int num = empty.size();
		empty.each { it.delete() };
		return num;
	}

	def getCacheFile(String path) {
		String dir0 = getRootDir().path;
		File dir;
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
	
	File getRootDir() {
		String cachedir = getLoc();
		return (cachedir.startsWith("/") || cachedir.indexOf(":/") == 1) ? new File(cachedir) : grailsApplication.parentContext.getResource(cachedir).file;
	}

	def getCacheInfo(String path) {
		File dir1 = getRootDir();
		String dir0 = dir1.path;
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

	Map processObjects(ObjectListing list) {
		String results="";
		Collection<String> files = [];
		list.getObjectSummaries().each { obj ->
			String key=obj.getKey();
			if (!key.endsWith("/")) {
				files.push(key);
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
						results+=refreshObject(key)
					} else {
						local="cache up to date"
					}
				} else {
					local="no cache"
					println(key+" size="+size+" "+local)
					//results+="<li>"+key+" size="+size+" "+local+"</li>"
					results+=refreshObject(key)
				}
			}
		}
		return([files: files, results: results])
	}

	def refreshObject(String key) {
		def bucket = grailsApplication.mergedConfig.grails.plugin.awsfiles.bucket;
		def results=""
		def s3=getS3Client();
		try {
			S3Object file=s3.getObject(bucket,key)
			long size = file.getObjectMetadata().getContentLength();
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
	
	String getLoc() {
		return grailsApplication.mergedConfig.grails.plugin.awsfiles.cacheLocation;
	}
	
	def startJob() {
		CacheRefreshJob.schedule(grailsApplication.mergedConfig.grails.plugin.awsfiles.refreshInterval)
	}
	
	def getS3Client() {
		boolean encrypt = grailsApplication.mergedConfig.grails.plugin.awsfiles.encrypt;
		def client;
		if (encrypt) {
			def key = grailsApplication.mergedConfig.grails.plugin.awsfiles.key;
			if (key != null) {
				SecretKey skey = new SecretKeySpec(Base64.decodeBase64(key.getBytes()), "AES")
				EncryptionMaterials materials = new EncryptionMaterials(skey)
				AWSCredentialsProvider credprov = new DefaultAWSCredentialsProviderChain()
				client = new AmazonS3EncryptionClient(credprov.getCredentials(), materials)
			} else {
				println "awsfiles.key must be defined to perform encrypted backups (use grails create-key command to generate one)"
				println "backup not performed"
				return
			}
		} else {
			client = new AmazonS3Client();
		}
		return client;
	}
}
