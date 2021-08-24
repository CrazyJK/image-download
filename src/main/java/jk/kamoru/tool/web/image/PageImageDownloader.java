package jk.kamoru.tool.web.image;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import lombok.extern.slf4j.Slf4j;

/**
 * Web page image downloader
 * <pre>Usage
 *  PageImageDownloader pageDownloader = new PageImageDownloader("page url string", "dest string");
 *  pageDownloader.setMinimumDownloadSize(50 * FileUtils.ONE_KB);
 *  DownloadResult result = pageDownloader.start();
 * or
 *  ExecutorService service = Executors.newFixedThreadPool(1);
 *  Future&lt;DownloadResult&gt; resultFuture = pageDownloader.start(service);
 *  service.shutdown();
 *  DownloadResult result = resultFuture.get();
 * </pre>
 * @author kamoru
 */
@Slf4j
public class PageImageDownloader {

	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36";

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

	private boolean proxyActive;
	private String proxyHostName;
	private String proxyHostValue;
	private String proxyPortName;
	private int proxyPortValue;

	private int timeout = 60 * 1000;

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
	 * set connection timeout. millesecond unit
	 * @param timeout
	 */
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	/**
	 * proxy config<br>
	 * names<br>
	 *   - http(s).proxyHost, http(s).proxyPort<br>
	 *   - socksProxyHost, socksProxyPort<br>
	 * ref. http://docs.oracle.com/javase/6/docs/technotes/guides/net/proxies.html<br>
	 * @param proxyActive if proxy use, true
	 * @param proxyHostName
	 * @param proxyHostValue
	 * @param proxyPortName
	 * @param proxyPortValue
	 */
	public void setProxyInfo(boolean proxyActive, String proxyHostName, String proxyHostValue, String proxyPortName, int proxyPortValue) {
		this.proxyActive = proxyActive;
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
	public Future<DownloadResult> start(final ExecutorService executorService) {
		return executorService.submit(new Callable<DownloadResult>() {

			@Override
			public DownloadResult call() throws Exception {
				log.debug("task start");
				return download();
			}
		});
	}

	public DownloadResult start() {
		// set proxy
		if (proxyActive) {
			log.debug("setting proxy");
			System.setProperty(proxyHostName, proxyHostValue);
			System.setProperty(proxyPortName, String.valueOf(proxyPortValue));
		}
		try {
			return download();
		} catch (Exception e) {
			return DownloadResult.fail(pageUrl, e);
		} finally {
			// release proxy
			if (proxyActive) {
				System.clearProperty(proxyHostName);
				System.clearProperty(proxyPortName);
			}
		}
	}

	/**
	 * execute download
	 * @return Download result
	 * @throws IOException
	 * @throws DownloadException
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	private DownloadResult download() throws IOException, DownloadException, InterruptedException, ExecutionException {
		log.info("Start download - [{}]", pageUrl);

		// connect and get image page by jsoup HTML parser
		Document document = Jsoup.connect(pageUrl).timeout(timeout).userAgent(USER_AGENT).get();
		if (document == null) {
			throw new DownloadException(pageUrl, "document is null");
		}

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

		int imgTagSize = imgTags.size();
		int nThreads = imgTagSize / 10;

		HttpClient httpClient = createHttpClient(imgTagSize, nThreads);

		// collect tasks
		List<ImageDownloader> tasks = new ArrayList<>();
		int count = 0;
		for (Element imgTag : imgTags) {
			String imgSrc = imgTag.attr("src");
			if (!StringUtils.isEmpty(imgSrc)) {
				ImageDownloader imageDownloader = new ImageDownloader(imgSrc, downloadDir, String.format("%s-%s", title, ++count), minimumSize);
				imageDownloader.setHttpClient(httpClient);
				tasks.add(imageDownloader);
			}
		}

		// execute thread
		ExecutorService downloadService = Executors.newFixedThreadPool(nThreads);
		log.debug("using {} thread pool", nThreads);
		List<Future<File>> files = downloadService.invokeAll(tasks);
		downloadService.shutdown();

		// get image files
		List<File> images = new ArrayList<>();
		for (Future<File> fileFuture : files) {
			File file = fileFuture.get();
			if (file != null)
				images.add(file);
		}

		log.info("{} image will be downloaded", images.size());
		return DownloadResult.success(pageUrl, images);
	}

	/**
	 * create HttpClient
	 * @param maxTotal
	 * @param maxPerRoute
	 * @return
	 */
	private HttpClient createHttpClient(int maxTotal, int maxPerRoute) {
		// pool setting
		PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
		cm.setMaxTotal(maxTotal);
		cm.setDefaultMaxPerRoute(maxPerRoute);
		// socket setting
		SocketConfig sc = SocketConfig.custom()
			.setSoTimeout(timeout)
			.setSoKeepAlive(true)
			.setTcpNoDelay(true)
			.setSoReuseAddress(true)
			.build();

		return HttpClients.custom().setConnectionManager(cm).setDefaultSocketConfig(sc).build();
	}

}
