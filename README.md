AWS File Plugin
=========

SRA plugin for storing files on AWS S3, caching them locally, and piping them through the application.

## Setup

- Add `"/file/$path**"(controller:"file/get")` to **UrlMappings.groovy**
- Change **bucket** to the appropriate AWS S3 bucket name.
- Customize any other default configs by following the instructions below
  - If **cache** is true and you want the cache to regularly update inject `cacheService` into **BootStrap.groovy** and call `cacheService.startJob()` at the end of `init`

## Use

- The project must be run using `-Daws.accessKeyId=[myKey]` and `-Daws.secretKey=[mySecretKey]` denoting AWS credentials with S3 permissions

## Config

All config items can be overwritten in **Config.groovy** by prepending `grails.plugin.awsfile.` onto the option name.

### cache

Boolean for if local caching of files is on.

**Default** - true

### cacheLocation

Path of where cached files are stored. Only used if **cache** is true.

**Default** - 'cache'

### cacheSync

Turning on cacheSync keeps the local cache in sync with the remote bucket, not just up to date with the latest files. This means the plugin will **delete local files which are not in S3**. Make sure the **cacheLocation** is for cache items only, otherwise the other items will be deleted.

**Default** - false

### rangeSupprt

Boolean for if byte range support is on.

**Default** - false

### refreshInterval

Time between cache refreshes in ms. Only used if **cache** is true and `cacheService.startJob()` was called in the **BootStrap.groovy**.

**Default** - 300000

### inline

Array of file endings for file types which will be rendered inline in the browser when browsed to.

**Default** - ['txt', 'pdf', 'html']

### transform

Map of file endings to file endings of file types whose file ending will be changed during retrieval. For example, if **html** is mapped to **md** and **MyFile.html** is requested the plugin will look for a file named **MyFile.md** and if it exists the plugin will return a map of the contents of **MyFile.md** and the real file ending of the file. The form of the map returned is `[contents: fileContents, type: fileEnding]` where **fileContents** is the file contents and **fileEnding** is the real file ending.

A use case is rendring markdown files inline. If `MyFile.md` is requested the file will be downloaded because markdown files can not be rendered inline by the browser. To get around this **html** can be mapped to **md** and when `MyFile.html` is requested the contents of `MyFile.md` will be returned. This contents can then be sent to the **get.gsp** page where it can be rendered based on the real file type.

**Default** - ['html': 'md']

### bucket

The name of the AWS S3 bucket serving as the virtual directory.

### bufferSize

The size of the buffer used to retrieve files from S3 in bytes.

**Default** - 1000000

### encrypt

Boolean for if the files in S3 are encrypted.

**Default** - false

### key

Encryption key for files in S3. Required if **encrypt** is true.