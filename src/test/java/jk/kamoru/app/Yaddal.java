package jk.kamoru.app;

import org.apache.commons.io.FileUtils;

import jk.kamoru.tool.web.image.PageImageDownloader;

public class Yaddal {

	public static void main(String[] args) {
		PageImageDownloader pageDownloader = new PageImageDownloader(
				"https://www.yaddal.tv/bbs/board.php?bo_table=ys_01&wr_id=27448", "E:\\yaddal");
		pageDownloader.setMinimumImageSize(50 * FileUtils.ONE_KB);
		pageDownloader.start();
	}

}
