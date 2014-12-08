package jk.kamoru.tool.web.image;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Web image downloader
 * <pre>Usage
 * 
 *   ImageDownloader downloader = new ImageDownloader("url string", "dest string");
 *   File file = downloader.download();
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
public class ImageDownloader implements Callable<File> {
	
	private static final Logger logger = LoggerFactory.getLogger(ImageDownloader.class);

	/** default image suffix */
	public static final String DEFAULT_IMAGE_SUFFIX = "jpg";

	/** image suffix list. "png", "jpg", "jpeg", "gif", "webp", "bmp" */
	private static final List<String> IMAGE_SUFFIX_LIST = Arrays.asList("png", "jpg", "jpeg", "gif", "webp", "bmp");
	
	private String imgSrc;
	private String destDir;
	private String title;
	private long   minimumSize;

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
	public void setMinimumSize(int minimumSize) {
		this.minimumSize = minimumSize;
	}
	
	@Override
	public File call() throws Exception {
		return download();
	}

	/**
	 * execute download
	 * @return image file. if error, <code>null</code> 
	 */
	public File download() {
		
		logger.debug("Start downloading - [{}]", imgSrc);
		
		File imageFile = null;
		
		try {
		
			// Execute a request of image
			DefaultHttpClient httpClient = new DefaultHttpClient();
			HttpGet httpGet = new HttpGet(imgSrc);
			HttpResponse httpResponse = httpClient.execute(httpGet);
		
			/* Test Code : All Header info
			Header[] headers = httpResponse.getAllHeaders();
			for (Header header : headers) {
				logger.debug("Header info : {}={}", header.getName(), header.getValue());
			}*/
		
			HttpEntity entity = httpResponse.getEntity();
			if (entity == null)
				throw new DownloadException(imgSrc, "Entity is null");
			if (entity.getContentLength() < minimumSize) {
				logger.debug("Entity is small < " + minimumSize);
				return null;
			}

			// is image file
			Header contentTypeHeader = httpResponse.getLastHeader("Content-Type");
			String contentType = contentTypeHeader.getValue();
			if (contentType == null)
				throw new DownloadException(imgSrc, "contentType is null");
			if (!contentType.startsWith("image"))
				throw new DownloadException(imgSrc, "it is not a image. " + contentType);
			
			// make title
			if (title == null) {
				title = StringUtils.substringAfterLast(imgSrc, "/");
				String suffix = StringUtils.substringAfterLast(title, ".");
				if (!IMAGE_SUFFIX_LIST.contains(suffix.toLowerCase())) {
					// find suffix in header
					suffix = StringUtils.substringAfterLast(contentType, "/");
					if (StringUtils.isEmpty(suffix))
						title += "." + DEFAULT_IMAGE_SUFFIX;
					else
						title += "." + suffix;
				}
			}
			
			// destination path
			File destPath = null;
			if (destDir == null)
				destPath = FileUtils.getTempDirectory();
			else
				destPath = new File(destDir);
			if (!destPath.isDirectory())
				throw new DownloadException(imgSrc, "destination is not a directory. " + destDir);

			// save image file
			imageFile = new File(destPath, title);
			
			InputStream inputStream = null;
			OutputStream outputStream = null;
			try {
				inputStream = entity.getContent();
				outputStream = new FileOutputStream(imageFile);
				byte[] buffer = new byte[1024];
				int length = 0;
				while ((length = inputStream.read(buffer)) > 0) {
					outputStream.write(buffer, 0, length);
				}
				logger.debug("save as {} - [{}]", imageFile.getAbsolutePath(), imgSrc);
			} 
			catch (IOException e) {
				FileUtils.deleteQuietly(imageFile);
				throw e;
			} 
			finally {
				if (outputStream != null)
					try {
						outputStream.close();
					} catch (IOException e) {
						logger.error("outputStream close error", e);
					}
				if (inputStream != null)
					try {
						inputStream.close();
					} catch (IOException e) {
						logger.error("inputStream close error", e);
					}
			}
		} 
		catch (ClientProtocolException e) { // httpClient.execute(httpGet);
			logger.error("connect fail", e);
		} 
		catch (IOException e) { // httpClient.execute(httpGet); outputstream error
			logger.error("download fail", e);
		} 
		catch (DownloadException e) {
			logger.error("illegal download state : {}", e.getMessage());
		} 
			
		return imageFile;
	}

}
