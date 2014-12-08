image-download
==============

Web page images downloader

  * ImageDownloader


		// direct download
		ImageDownloader downloader = new ImageDownloader("image url", "local destination path");
		File image = downloader.download();

		//using ExcutorService
		ExecutorService service = Executors.newFixedThreadPool(1);
		Future<File> fileFuture = service.submit(downloader);
		service.shutdown();
		File image = fileFuture.get();

  * PageImageDownloader

		PageImageDownloader pageDownloader = new PageImageDownloader("image page url", "local destination path");
		pageDownloader.setMinimumImageSize(50 * FileUtils.ONE_KB);

		// direct download
		DownloadResult result = pageDownloader.download();
		
		//using ExcutorService
		ExecutorService service = Executors.newFixedThreadPool(1);
		Future<DownloadResult> resultFuture = pageDownloader.download(service);
		service.shutdown();
		DownloadResult result = resultFuture.get();

#### Depends on
1. slf4j
2. commons-io
3. commons-lang3
4. jsoup
5. httpclient
6. JDK 1.7 over


  