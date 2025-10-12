package common;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Zip implements AutoCloseable {
	File output;
	FileOutputStream fos;
	ZipOutputStream zos;

	public Zip(String path) {
		output = new File(path);
		try {
			fos = new FileOutputStream(output);
			zos = new ZipOutputStream(fos);
		} catch (Exception e) {
			throw new RuntimeException("Could not create zip", e);
		}
	}

	public static void unzip(Path source, Path dest) {
		try (var zis = new ZipInputStream(new FileInputStream(source.toString()))) {
			ZipEntry entry;
			byte[] buffer = new byte[4096];
			while ((entry = zis.getNextEntry()) != null) {
				File newFile = dest.resolve(entry.getName()).toFile();
				if (entry.isDirectory()) {
					newFile.mkdirs();
				} else {
					new File(newFile.getParent()).mkdirs();
					try (FileOutputStream fos = new FileOutputStream(newFile)) {
						int length;
						while ((length = zis.read(buffer)) > 0) {
							fos.write(buffer, 0, length);
						}
					}
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void concat(Path zipPath) {
		var buffer = new byte[4096];

		try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				try {
					if (entry.getName().endsWith(".SF")
						|| entry.getName().endsWith(".RSA")
						|| entry.getName().endsWith("DSA")) {
						continue;
					}
					ZipEntry newEntry = new ZipEntry(entry.getName());
					zos.putNextEntry(newEntry);

					int len;
					while ((len = zis.read(buffer)) > 0) {
						zos.write(buffer, 0, len);
					}

					zos.closeEntry();
					zis.closeEntry();
				} catch (ZipException e) {
					if (!e.getMessage().contains("duplicate entry")) {
						throw new RuntimeException(e);
					}
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to add zip file: " + zipPath, e);
		}
	}

	public void add(String path, File file) {
		try {
			zos.putNextEntry(new ZipEntry(path));
			try (FileInputStream fis = new FileInputStream(file)) {
				byte[] buffer = new byte[4096];
				int bytesRead;
				while ((bytesRead = fis.read(buffer)) != -1) {
					zos.write(buffer, 0, bytesRead);
				}
			}
			zos.closeEntry();
		} catch (Exception e) {
			throw new RuntimeException("Failed to add zip file: " + path, e);
		}
	}

	public void add(String path, String fileContent) {
		try {
			zos.putNextEntry(new ZipEntry(path));
			zos.write(fileContent.getBytes());
			zos.closeEntry();
		} catch (ZipException e) {
			if (!e.getMessage().contains("duplicate entry")) {
				throw new RuntimeException(e);
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to add zip file: " + path, e);
		}
	}

	@Override
	public void close() {
		try {
			this.zos.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
