package jk.kamoru.tool.web.image;

import java.io.File;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * result object of {@link PageImageDownloader}
 * @author kamoru
 *
 */
@AllArgsConstructor
@Data
public class DownloadResult {

	private String pageUrl;
	private boolean result;
	private int count;
	private String message;
	private List<File> images;

	public static DownloadResult success(String url, List<File> images) {
		return new DownloadResult(url, true, images.size(), "", images);
	}

	public static DownloadResult fail(String url, Exception error) {
		return new DownloadResult(url, false, 0, error.getMessage(), null);
	}

}
