package jk.kamoru.tool.web.image;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;

import lombok.extern.slf4j.Slf4j;

/**
 * Web image downloader
 * <pre>Usage
 *
 *   ImageDownloader downloader = new ImageDownloader("url string", "dest string");
 *   File file = downloader.start();
 *
 * or using ExecutorService
 *
 *   ExecutorService service = Executors.newFixedThreadPool(1);
 *   Future<File> fileFuture = service.submit(downloader);
 *   service.shutdown();
 *   File file = fileFuture.get();
 * </pre>
 *
 * @author kamoru
 */
@Slf4j
public class ImageDownloader implements Callable<File> {

	/** default image suffix */
	public static final String DEFAULT_IMAGE_SUFFIX = "jpg";

	/** image suffix list. "png", "jpg", "jpeg", "gif", "webp", "bmp" */
	private static final List<String> IMAGE_SUFFIX_LIST = Arrays.asList("png", "jpg", "jpeg", "gif", "webp", "bmp");

	private String imgSrc;
	private String destDir;
	private String title;
	private long   minimumSize;

	private HttpClient httpClient;

	/**
	 * Constructs a new <code>ImageDownloader</code> using image source<br>
	 * execute {@link #download()} or using {@link java.util.concurrent.ExecutorService ExecutorService}
	 *
	 * @param imageSrc image source url
	 */
	public ImageDownloader(String imageSrc) {
		this(imageSrc, null, null, 0);
	}


	/**
	 * Constructs a new <code>ImageDownloader</code> using image source, destination directory<br>
	 * execute {@link #download()} or using {@link java.util.concurrent.ExecutorService ExecutorService}
	 *
	 * @param imageSrc image source url
	 * @param destDir destination directory
	 */
	public ImageDownloader(String imageSrc, String destDir) {
		this(imageSrc, destDir, null, 0);
	}

	/**
	 * Constructs a new <code>ImageDownloader</code> using image source, destination directory, title<br>
	 * execute {@link #download()} or using {@link java.util.concurrent.ExecutorService ExecutorService}
	 *
	 * @param imageSrc image source url
	 * @param destDir destination directory
	 * @param title image title
	 */
	public ImageDownloader(String imageSrc, String destDir, String title) {
		this(imageSrc, destDir, title, 0);
	}

	/**
	 * Constructs a new <code>ImageDownloader</code> using image source, destination directory, title<br>
	 * execute {@link #download()} or using {@link java.util.concurrent.ExecutorService ExecutorService}
	 *
	 * @param imgSrc image source url
	 * @param destDir destination directory
	 * @param title image title
	 * @param minimunSize minimum image size(bytes)
	 */
	public ImageDownloader(String imgSrc, String destDir, String title, long minimunSize) {
		super();
		this.imgSrc 	 = imgSrc;
		this.destDir 	 = destDir;
		this.title 		 = title;
		this.minimumSize = minimunSize;
	}

	/**
	 * if image size is smaller than minimumSize, do not download
	 * @param minimumSize bytes
	 */
	public void setMinimumSize(long minimumSize) {
		this.minimumSize = minimumSize;
	}

	/**
	 * set httpClient. if not set, use inner minimal client
	 * @param httpClient
	 */
	public void setHttpClient(HttpClient httpClient) {
		this.httpClient = httpClient;
	}

	@Override
	public File call() throws Exception {
		return start();
	}

	/**
	 * execute image download
	 * @return image file
	 */
	public File start() {
		try {
			return download();
		} catch (ClientProtocolException e) {
			log.error("connect fail " + imgSrc, e);
		} catch (IOException e) {
			log.error("download fail {} : {}",e.getMessage(), imgSrc);
		} catch (DownloadException e) {
			log.error("illegal download state : {}", e.getMessage());
		} catch (Exception e) {
			log.error("fail " + imgSrc, e);
		}
		return null;
	}

	/**
	 * execute download
	 * @return image file. if error, <code>null</code>
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws DownloadException
	 */
	private File download() throws ClientProtocolException, IOException, DownloadException {
		log.debug("Start downloading - [{}]", imgSrc);

		// if not injected, create
		if (httpClient == null) {
			httpClient = HttpClients.createMinimal();
		}

		// Execute a request of image
		HttpGet httpGet = new HttpGet(imgSrc);
		HttpResponse httpResponse = httpClient.execute(httpGet);

		/* Test Code : All Header info
		Header[] headers = httpResponse.getAllHeaders();
		for (Header header : headers) {
			logger.debug("Header info : {}={}", header.getName(), header.getValue());
		}*/

		// size check
		HttpEntity entity = httpResponse.getEntity();
		if (entity == null)
			throw new DownloadException(imgSrc, "Entity is null");
		if (entity.getContentLength() < minimumSize) {
			log.debug("Entity is small < " + minimumSize);
			return null;
		}

		// type check whether is image file
		Header contentTypeHeader = httpResponse.getLastHeader("Content-Type");
		String contentType = contentTypeHeader.getValue();
		if (contentType == null)
			throw new DownloadException(imgSrc, "contentType is null");
		if (!contentType.startsWith("image"))
			throw new DownloadException(imgSrc, "it is not a image. " + contentType);

		// make title
		if (title == null) {
			title = StringUtils.substringAfterLast(imgSrc, "/");
		}
		String suffix = StringUtils.substringAfterLast(title, ".");
		if (!IMAGE_SUFFIX_LIST.contains(suffix.toLowerCase())) {
			// find suffix in header
			suffix = StringUtils.substringAfterLast(contentType, "/");
			if (StringUtils.isEmpty(suffix))
				title += "." + DEFAULT_IMAGE_SUFFIX;
			else
				title += "." + suffix;
		}

		// destination path
		File destPath = destDir == null ? FileUtils.getTempDirectory() : new File(destDir);
		if (!destPath.isDirectory())
			throw new DownloadException(imgSrc, "destination is not a directory. " + destDir);

		// save image file
		File imageFile = new File(destPath, title);
		FileUtils.copyInputStreamToFile(entity.getContent(), imageFile);
		log.debug("save as {} - [{}]", imageFile.getAbsolutePath(), imgSrc);

		return imageFile;
	}

}
