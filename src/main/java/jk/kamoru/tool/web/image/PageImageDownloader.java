package jk.kamoru.tool.web.image;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Web page image downloader
 * <pre>Usage
 *  PageImageDownloader pageDownloader = new PageImageDownloader("page url string", "dest string");
 *  pageDownloader.setMinimumDownloadSize(50 * FileUtils.ONE_KB);
 *  DownloadResult result = pageDownloader.download();
 * or
 *  ExecutorService service = Executors.newFixedThreadPool(1);
 *  Future&lt;DownloadResult&gt; resultFuture = pageDownloader.download(service);
 *  service.shutdown();
 *  DownloadResult result = resultFuture.get();
 * </pre>
 * @author kamoru
 */
public class PageImageDownloader {

	private static final Logger logger = LoggerFactory.getLogger(PageImageDownloader.class);

	/** web page URL */
	private String pageUrl;
	/** local destination folder */
	private String downloadDir;
	/** title prefix */
	private String titlePrefix;
	/** CSS Query for image title */
	private String titleCssQuery;
	/** web page no. ex) bbs */
	private int pageNo;

	/** minimun image size. bytes */
	private long minimumSize;
	
	private boolean proxy;
	private String proxyHostName;
	private String proxyHostValue;
	private String proxyPortName;
	private int proxyPortValue;
	
	/**
	 * constructor<br>
	 * if need to proxy setting, add {@link #setProxyInfo(boolean, String, String, String, int)}
	 * @param pageUrl web page URL
	 * @param downloadDir local destination folder
	 */
	public PageImageDownloader(String pageUrl, String downloadDir) {
		this(pageUrl, downloadDir, 0, null, null);
	}
	
	/**
	 * constructor<br>
	 * if need to proxy setting, add {@link #setProxyInfo(boolean, String, String, String, int)}
	 * @param pageUrl web page URL
	 * @param downloadDir local destination folder
	 * @param pageNo web page no. ex) bbs
	 */
	public PageImageDownloader(String pageUrl, String downloadDir, int pageNo) {
		this(pageUrl, downloadDir, pageNo, null, null);
	}
	
	/**
	 * constructor<br>
	 * if need to proxy setting, add {@link #setProxyInfo(boolean, String, String, String, int)}
	 * @param pageUrl web page URL
	 * @param downloadDir local destination folder
	 * @param pageNo web page no. ex) bbs
	 * @param titlePrefix image title prefix
	 */
	public PageImageDownloader(String pageUrl, String downloadDir, int pageNo, String titlePrefix) {
		this(pageUrl, downloadDir, pageNo, titlePrefix, null);
	}
	
	/**
	 * constructor<br>
	 * if need to proxy setting, add {@link #setProxyInfo(boolean, String, String, String, int)}
	 * @param pageUrl web page URL
	 * @param downloadDir local destination folder
	 * @param pageNo web page no. ex) bbs
	 * @param titlePrefix image title prefix
	 * @param titleCssQuery CSS Query for image title 
	 */
	public PageImageDownloader(String pageUrl, String downloadDir, int pageNo, String titlePrefix, String titleCssQuery) {
		super();
		this.pageUrl = pageUrl;
		this.downloadDir = downloadDir;
		this.pageNo = pageNo;
		this.titlePrefix = titlePrefix;
		this.titleCssQuery = titleCssQuery;
	}
	
	/**
	 * minimun image size. bytes
	 * @param minimumSize
	 */
	public void setMinimumImageSize(long minimumSize) {
		this.minimumSize = minimumSize;
	}
	
	/**
	 * proxy config<br>
	 * names<br>
	 *   - http(s).proxyHost, http(s).proxyPort<br> 
	 *   - socksProxyHost, socksProxyPort<br> 
	 * ref. http://docs.oracle.com/javase/6/docs/technotes/guides/net/proxies.html<br>
	 * @param proxy if proxy use, true
	 * @param proxyHostName 
	 * @param proxyHostValue
	 * @param proxyPortName
	 * @param proxyPortValue
	 */
	public void setProxyInfo(boolean proxy, String proxyHostName, String proxyHostValue, String proxyPortName, int proxyPortValue) {
		this.proxy = proxy;
		this.proxyHostName = proxyHostName;
		this.proxyHostValue = proxyHostValue;
		this.proxyPortName = proxyPortName;
		this.proxyPortValue = proxyPortValue;
	}

	/**
	 * execute download by using {@link java.util.concurrent.ExecutorService ExecutorService}
	 * @param executorService
	 * @return Download result
	 */
	public Future<DownloadResult> download(final ExecutorService executorService) {
		return executorService.submit(new Callable<DownloadResult>() {

			@Override
			public DownloadResult call() throws Exception {
				logger.debug("task start");
				return download();
			}
		});
	}
	
	/**
	 * execute download
	 * @return Download result
	 */
	public DownloadResult download() {
		
		logger.info("Start download - [{}]", pageUrl);
		
		try {
			// set proxy
			if (proxy) {
				logger.debug("setting proxy");
				System.setProperty(proxyHostName, proxyHostValue);
				System.setProperty(proxyPortName, String.valueOf(proxyPortValue));
			}
			
			// connect and get image page by jsoup HTML parser
			Document document;
			try {
				document = Jsoup.connect(pageUrl).get();
			} 
			catch (IOException e) {
				throw new DownloadException(pageUrl, "could not connect", e);
			}
			
			if (document == null) 
				throw new DownloadException(pageUrl, "document is null");

			// get page title
			String titleByDocument = document.title();
			String titleByCSS = titleCssQuery != null ? document.select(titleCssQuery).first().text() : null;
			String title = String.format("%s%s%s", 
					StringUtils.isEmpty(titlePrefix) ? "" : titlePrefix + "-", 
					pageNo == 0 ? "" : pageNo + "-",
					StringUtils.isEmpty(titleByCSS) ? titleByDocument : titleByCSS);
			
			if (StringUtils.isEmpty(title))
				throw new DownloadException(pageUrl, "title is empty");
			
			// find img tag
			Elements imgTags = document.getElementsByTag("img");
			if (imgTags.size() == 0)
				throw new DownloadException(pageUrl, "no image exist");
		
			// download
			List<ImageDownloader> tasks = new ArrayList<ImageDownloader>();
			int count = 0;
			for (Element imgTag : imgTags) {
				String imgSrc = imgTag.attr("src");
				if (!StringUtils.isEmpty(imgSrc)) {
					tasks.add(new ImageDownloader(
							imgSrc, downloadDir, String.format("%s-%s", title, ++count), minimumSize));
				}
			}
			int nThreads = imgTags.size() / 10;
			ExecutorService downloadService = Executors.newFixedThreadPool(nThreads);
			logger.debug("using {} thread pool", nThreads);
			List<Future<File>> files = downloadService.invokeAll(tasks);
			downloadService.shutdown();

			List<File> images = new ArrayList<File>();
			for (Future<File> fileFuture : files) {
				File file = fileFuture.get();
				if (file != null)
					images.add(file);
			}
			logger.info("{} image will be downloaded", images.size());
			return new DownloadResult(pageUrl, true, images.size()).setImages(images);
		}
		catch (DownloadException e) {
			logger.error("Download error", e);
			return new DownloadResult(pageUrl, false, 0, e.getMessage());
		}
		catch (Exception e) {
			logger.error("Error", e);
			return new DownloadResult(pageUrl, false, 0, e.getMessage());
		}
		finally {
			// release proxy
			if (proxy) {
				System.clearProperty(proxyHostName);
				System.clearProperty(proxyPortName);
			}
		}
	}
	
	/**
	 * result object of {@link PageImageDownloader}
	 * @author kamoru
	 *
	 */
	public class DownloadResult {
		
		public String pageUrl;
		public Boolean result;
		public Integer count;
		public String message = "";
		public List<File> images;
		
		/**
		 * constructor
		 * @param pageUrl image page url
		 * @param result download result
		 * @param count file size of downloaded
		 */
		public DownloadResult(String pageUrl, Boolean result, Integer count) {
			this.pageUrl = pageUrl;
			this.result = result;
			this.count = count;
		}
		
		/**
		 * constructor
		 * @param pageUrl image page url
		 * @param result download result
		 * @param count file size of downloaded
		 * @param message if <code>result == false</code>, error message
		 */
		public DownloadResult(String pageUrl, Boolean result, Integer count, String message) {
			this(pageUrl, result, count);
			this.message = message;
		}

		/**
		 * @return downloaded file list
		 */
		public List<File> getImages() {
			return images;
		}

		public DownloadResult setImages(List<File> images) {
			this.images = images;
			return this;
		}

		@Override
		public String toString() {
			return String
					.format("DownloadResult [pageUrl=%s, result=%s, count=%s, message=%s, images=%s]",
							pageUrl, result, count, message, images.size());
		}

	}

}
