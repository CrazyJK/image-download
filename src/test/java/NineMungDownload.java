import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.io.FileUtils;

import jk.kamoru.tool.web.image.PageImageDownloader;
import jk.kamoru.tool.web.image.PageImageDownloader.DownloadResult;

public class NineMungDownload {

	public static void main(String[] args) throws InterruptedException, ExecutionException {
		
		// 13 ~ 20, 45~ 47, 918, 922
		for (int i = 922; i < 923; i++) {
			
			PageImageDownloader pageDownloader = new PageImageDownloader(
					"http://9mung.cc/bbs/board.php?bo_table=ggulbam&page=1&wr_id=" + i, 
					"E:\\NineMung", 
					i, 
					"NineMung",
					"article > h1");
			pageDownloader.setMinimumImageSize(50 * FileUtils.ONE_KB);

//			DownloadResult result = pageDownloader.download();
//			System.out.println(result);
			
			ExecutorService service = Executors.newFixedThreadPool(1);
			Future<DownloadResult> resultFuture = pageDownloader.download(service);
			service.shutdown();
			System.out.println("[PageImageDownloader] using ExcutorService " + resultFuture.get());

		}
		


	}

}
