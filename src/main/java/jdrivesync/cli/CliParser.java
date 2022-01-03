package jdrivesync.cli;

import jdrivesync.constants.Constants;
import jdrivesync.exception.JDriveSyncException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;

public class CliParser {
	private enum Argument {
		Help("-h", "--help", "輸出說明."),
		LocalRootDir("-l", "--local-dir", "提供應該同步的本地目錄.", "<local-dir>"),
		RemoteRootDir("-r", "--remote-dir", "提供應該同步的遠程目錄.", "<remote-dir>"),
		AuthenticationFile("-a", "--authentication-file", "使用指定的身份驗證文件而不是預設的 (~/.jdrivesync).", "<auth-file>"),
		DryRun(null, "--dry-run", "模擬所有操作 (dry run)."),
		Delete(null, "--delete", "刪除所有文件而不是將它們移至垃圾箱."),
		Checksum("-c", "--checksum", "使用 MD5 校驗和而不是文件的最後修改時間戳."),
		IgnoreFile("-i", "--ignore-file", "Provides a file with newline separated file and/or path name patterns that should be ignored.", "<ignore-file>"),
		SyncUp("-u", "--up", "從本地到遠程進行同步 (預設)."),
		SyncDown("-d", "--down", "從遠程到本地進行同步"),
		HtmlReport(null, "--html-report", "創建同步的 HTML 報告."),
		MaxFileSize("-m", "--max-file-size", "提供最大文件大小（以 MB 為單位）.", "<maxFileSize>"),
		HttpChunkSize(null, "--http-chunk-size", "用於分塊上傳的塊大小（以 MB 為單位） (預設: 10MB)."),
		NetworkNumberOfReries(null, "--network-number-of-retries", "請求退出的次數 (預設: 3)."),
		NetworkSleepBetweenRetries(null, "--network-sleep-between-retries", "重試之間間隔的秒數 (預設: 10)."),
		Verbose("-v", "--verbose", "詳細輸出"),
		LogFile(null, "--log-file", "日誌文件的位置.", "<log-file>"),
		NoDelete(null, "--no-delete", "不要刪除文件."),
		Doc(null,"--doc", "Google 文件(Doc) 導出/導入格式 (預設:Open Office doc).","application/vnd.oasis.opendocument.text"),
		Sheets(null,"--sheet","Google 試算表(Sheet) 導出/導入格式 format (預設:Open Office sheet).","application/x-vnd.oasis.opendocument.spreadsheet"),
		Slides(null,"--slides","Google 簡報(Slides) 導出/導入格式 format (預設:Open Office presentation).","application/vnd.oasis.opendocument.presentation"),
		Drowing(null,"--drowing","Google 繪圖(Drawing) 導出/導入格式 (預設:JPEG).","image/jpeg");
		//Password("-p", "--password", "The password used to encrypt/decrypt the files.", "<password>"),
		//EncryptFile("-e", "--encrypt-files", "Provides a file with newline separated file and/or path name patterns that should be encrypted.", "<encrypt-file>");
		private final String shortOption;
		private final String longOption;
		private final String description;
		private final Optional<String> argument;

		Argument(String shortOption, String longOption, String description) {
			this.shortOption = shortOption;
			this.longOption = longOption;
			this.description = description;
			this.argument = Optional.empty();
		}

		Argument(String shortOption, String longOption, String description, String argument) {
			this.shortOption = shortOption;
			this.longOption = longOption;
			this.description = description;
			this.argument = Optional.of(argument);
		}

		public boolean matches(String arg) {
			boolean matches = false;
			if (shortOption != null && shortOption.equals(arg)) {
				matches = true;
			}
			if (longOption != null && longOption.equals(arg)) {
				matches = true;
			}
			return matches;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			if (shortOption != null) {
				sb.append(shortOption);
			}
			if (longOption != null) {
				if (shortOption != null) {
					sb.append(",");
				}
				sb.append(longOption);
			}
			if (argument.isPresent()) {
				sb.append(" ");
				sb.append(argument.get());
			}
			sb.append("\n");
			sb.append("\t");
			sb.append(description);
			return sb.toString();
		}
		}

	public Options parse(String[] args) throws IllegalArgumentException {
		Options options = new Options();
		StringArrayEnumeration sae = new StringArrayEnumeration(args);
		while (sae.hasMoreElements()) {
			String arg = sae.nextElement();
			Argument argument = toArgument(arg);
			if (argument == Argument.Help) {
				printHelp();
			} else if (argument == Argument.LocalRootDir) {
				String localRootDir = getOptionWithArgument(arg, sae);
				File file = validateLocalRootDirArg(localRootDir);
				options.setLocalRootDir(Optional.of(file));
			} else if (argument == Argument.RemoteRootDir) {
				String remoteRootDir = getOptionWithArgument(arg, sae);
				options.setRemoteRootDir(Optional.of(remoteRootDir));
			} else if (argument == Argument.AuthenticationFile) {
				String authenticationFile = getOptionWithArgument(arg, sae);
				options.setAuthenticationFile(Optional.of(authenticationFile));
			} else if (argument == Argument.DryRun) {
				options.setDryRun(true);
			} else if (argument == Argument.Delete) {
				options.setDeleteFiles(true);
			} else if (argument == Argument.Checksum) {
				options.setUseChecksum(true);
			} else if (argument == Argument.IgnoreFile) {
				String patternArg = getOptionWithArgument(arg, sae);
				List<String> lines = readFile(patternArg);
				FileNamePatterns ignoreFiles = FileNamePatterns.create(lines);
				options.setIgnoreFiles(ignoreFiles);
			} else if (argument == Argument.HtmlReport) {
				options.setHtmlReport(true);
			} else if (argument == Argument.SyncUp) {
				options.setSyncDirection(SyncDirection.Up);
			} else if (argument == Argument.SyncDown) {
				options.setSyncDirection(SyncDirection.Down);
			} else if (argument == Argument.MaxFileSize) {
				String option = getOptionWithArgument(arg, sae);
				Long maxFileSizeInteger;
				try {
					maxFileSizeInteger = Long.valueOf(option);
				} catch (NumberFormatException e) {
					throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "Argument for option '" + arg + "' 不是整數。");
				}
				options.setMaxFileSize(Optional.of(maxFileSizeInteger * Constants.MB));
			} else if (argument == Argument.HttpChunkSize) {
				String option = getOptionWithArgument(arg, sae);
				long httpChunkSizeMB;
				try {
					httpChunkSizeMB = Long.valueOf(option);
				} catch (NumberFormatException e) {
					throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "Argument for option '" + arg + "' 不是整數。");
				}
				long httpChunkSizeBytes = httpChunkSizeMB * Constants.MB;
				httpChunkSizeBytes = (httpChunkSizeBytes / 256) * 256; // chunk size must be multiple of 256
				if (httpChunkSizeMB <= 0) {
					throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "Argument for option '" + arg + "' 是一個負數或零。");
				}
				options.setHttpChunkSizeInBytes(httpChunkSizeBytes);
			} else if (argument == Argument.NetworkNumberOfReries) {
				String option = getOptionWithArgument(arg, sae);
				int networkNumberOfRetries;
				try {
					networkNumberOfRetries = Integer.valueOf(option);
				} catch (NumberFormatException e) {
					throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "Argument for option '" + arg + "' 不是整數。");
				}
				if (networkNumberOfRetries < 0) {
					throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "Argument for option '" + arg + "' 是一個負數或零。");
				}
				options.setNetworkNumberOfAttempts(networkNumberOfRetries);
			} else if (argument == Argument.NetworkSleepBetweenRetries) {
				String option = getOptionWithArgument(arg, sae);
				int optionAsInteger;
				try {
					optionAsInteger = Integer.valueOf(option);
				} catch (NumberFormatException e) {
					throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "Argument for option '" + arg + "' 不是整數。");
				}
				if (optionAsInteger <= 0) {
					throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "Argument for option '" + arg + "' 是一個負數或零。");
				}
				options.setNetworkSleepBetweenAttempts(optionAsInteger * 1000);
			} else if (argument == Argument.Verbose) {
				options.setVerbose(true);
			} else if (argument == Argument.LogFile) {
				String option = getOptionWithArgument(arg, sae);
				Path path = Paths.get(option);
				if (Files.isDirectory(path)) {
					throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "Argument for option '" + arg + "' 是目錄而不是文件.");
				}
				if (!Files.exists(path)) {
					try {
						path = Files.createFile(path);
					} catch (IOException e) {
						throw new JDriveSyncException(JDriveSyncException.Reason.IOException, String.format("未能創建日誌文件 '%s': %s", path.toString(), e.getClass().getSimpleName() + ": " + e.getMessage()), e);
					}
				}
				if (!Files.isWritable(path)) {
					throw new JDriveSyncException(JDriveSyncException.Reason.IOException, String.format("日誌文件 '%s' 無法寫入.", path.toString()));
				}
				options.setLogFile(Optional.of(path));
			} else if (argument == Argument.NoDelete) {
				options.setNoDelete(true);
			} else if (argument == Argument.Doc) {
				String docExportMimeType = getOptionWithArgument(arg, sae);
				options.setDocMimeType(Optional.of(docExportMimeType));
			} else if (argument == Argument.Sheets) {
				String sheetsExportMimeType = getOptionWithArgument(arg, sae);
				options.setDocMimeType(Optional.of(sheetsExportMimeType));
			} else if (argument == Argument.Slides) {
				String slidesExportMimeType = getOptionWithArgument(arg, sae);
				options.setDocMimeType(Optional.of(slidesExportMimeType));
			} else if (argument == Argument.Drowing) {
				String drowingExportMimeType = getOptionWithArgument(arg, sae);
				options.setDrowingMimeType(Optional.of(drowingExportMimeType));
			} else {
				throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "參數 '" + arg + "' 不可用.");
			}
		}
		checkForMandatoryOptions(options);
		normalizeRemoteRootDir(options);
		return options;
	}

	private Argument toArgument(String arg) {
		for (Argument currentArgument : Argument.values()) {
			if (currentArgument.matches(arg)) {
				return currentArgument;
			}
		}
		return null;
	}

	private File validateLocalRootDirArg(String localRootDir) {
		File file = new File(localRootDir);
		if (!file.exists()) {
			throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, String.format("'%s' 不存在.", localRootDir));
		}
		if (!file.canRead()) {
			throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, String.format("目錄 '%s' 無法讀取.", localRootDir));
		}
		if (!file.isDirectory()) {
			throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, String.format("'%s' 不是目錄.", localRootDir));
		}
		return file;
	}

	public void normalizeRemoteRootDir(Options options) {
		if (options.getRemoteRootDir().isPresent()) {
			String remoteRootDir = options.getRemoteRootDir().get();
			remoteRootDir = remoteRootDir.trim();
			remoteRootDir = remoteRootDir.replace("\\", "/");
			if (remoteRootDir.startsWith("/")) {
				remoteRootDir = remoteRootDir.substring(1, remoteRootDir.length());
			}
			options.setRemoteRootDir(Optional.of(remoteRootDir));
		}
	}

	public static void printHelp() {
		System.out.println("可用參數:");
		for (Argument currentArg : Argument.values()) {
			System.out.println(currentArg.toString());
		}
		throw new JDriveSyncException(JDriveSyncException.Reason.NormalTermination);
	}

	private void checkForMandatoryOptions(Options options) {
		boolean valid = true;
		String message = null;
		if (!options.getLocalRootDir().isPresent()) {
			message = "請指定要同步的本地目錄.";
			valid = false;
		}
		if (!valid) {
			throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, message);
		}
	}

	private String getOptionWithArgument(String option, StringArrayEnumeration sae) {
		if (sae.hasMoreElements()) {
			String value = sae.nextElement();
			if (toArgument(value) != null) {
				throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, String.format("缺少選項的參數 %s.", option));
			}
			return value;
		} else {
			throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, String.format("缺少選項的參數 %s.", option));
		}
	}

	private List<String> readFile(String filename) {
		Path path;
		try {
			path = Paths.get(filename);
		} catch (Exception e) {
			throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "'" + filename + "' 不是有效路徑: " + e.getMessage(), e);
		}
		if (!Files.exists(path)) {
			throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "'" + filename + "' 不存在.");
		}
		try {
			return Files.readAllLines(path, Charset.defaultCharset());
		} catch (IOException e) {
			throw new JDriveSyncException(JDriveSyncException.Reason.InvalidCliParameter, "無法讀取檔案 '" + path + "':" + e.getMessage(), e);
		}
	}
}
