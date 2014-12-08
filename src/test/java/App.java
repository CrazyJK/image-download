

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.io.FileUtils;

import jk.kamoru.tool.web.image.ImageDownloader;
import jk.kamoru.tool.web.image.PageImageDownloader;
import jk.kamoru.tool.web.image.PageImageDownloader.DownloadResult;

/**
 * Test
 *
 */
public class App {
	
	public static void main(String[] args) throws InterruptedException, ExecutionException {
		System.out.println("Test start");

		// direct download
		ImageDownloader downloader = new ImageDownloader("http://gw.handysoft.co.kr/img/main/smartoffice2.png", "/home/kamoru/etc");
		System.out.println("[ImageDownloader] direct download");
		downloader.download();
		// using ExcutorService
		ExecutorService singleDownloadService = Executors.newFixedThreadPool(1);
		Future<File> fileFuture = singleDownloadService.submit(downloader);
		singleDownloadService.shutdown();
		System.out.println("[ImageDownloader] using ExcutorService " + fileFuture.get().getName());
		
		
		PageImageDownloader pageDownloader = new PageImageDownloader("http://blog.naver.com/PostList.nhn?blogId=comingsook", "/home/kamoru/etc");
		pageDownloader.setMinimumImageSize(500 * FileUtils.ONE_KB);

		DownloadResult result = pageDownloader.download();
		System.out.println(result);
		
		ExecutorService service = Executors.newFixedThreadPool(1);
		Future<DownloadResult> resultFuture = pageDownloader.download(service);
		service.shutdown();
		System.out.println("[PageImageDownloader] using ExcutorService " + resultFuture.get());
		
		
	}
}
