package fansirsqi.xposed.sesame.util;

import android.annotation.SuppressLint;
import android.os.Environment;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

import fansirsqi.xposed.sesame.data.General;

public class Files {
    @SuppressLint("StaticFieldLeak")
    private static final String TAG = Files.class.getSimpleName();
    /**
     * 配置文件夹名称
     */
    public static final String CONFIG_DIR_NAME = "sesame-TK";
    /**
     * 应用配置文件夹主路径
     */
    public static final File MAIN_DIR = getMainDir();
    /**
     * 配置文件夹路径
     */
    public static final File CONFIG_DIR = getConfigDir();
    /**
     * 日志文件夹路径
     */
    public static final File LOG_DIR = getLogDir();


    /**
     * 确保指定的目录存在且不是一个文件。
     *
     * @param directory 目录
     */
    public static void ensureDir(File directory) {
        try {
            if (directory == null) {
                // 🔥 修改点 1：使用原生 Log，避免依赖循环
                android.util.Log.e(TAG, "Directory cannot be null");
                return;
            }
            if (!directory.exists()) {
                if (!directory.mkdirs()) {
                    // 🔥 修改点 2：使用原生 Log
                    android.util.Log.e(TAG, "Failed to create directory: " + directory.getAbsolutePath());
                }
            } else if (directory.isFile()) {
                if (!directory.delete() || !directory.mkdirs()) {
                    // 🔥 修改点 3：使用原生 Log
                    android.util.Log.e(TAG, "Failed to replace file with directory: " + directory.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            // 🔥 修改点 4：使用原生 Log
            android.util.Log.e(TAG, "ensureDir error", e);
        }
    }

    /**
     * 获取配置文件夹主路径
     *
     * @return mainDir 主路径
     */
    private static File getMainDir() {
        String storageDirStr =
                Environment.getExternalStorageDirectory() + File.separator + "Android" + File.separator + "media" + File.separator + General.PACKAGE_NAME;
        File storageDir = new File(storageDirStr);
        File mainDir = new File(storageDir, CONFIG_DIR_NAME);
        ensureDir(mainDir);
        return mainDir;
    }

    /**
     * 获取日志文件夹路径
     *
     * @return logDir 日志文件夹路径
     */
    private static File getLogDir() {
        File logDir = new File(MAIN_DIR, "log");
        ensureDir(logDir);
        return logDir;
    }

    /**
     * 获取配置文件夹路径
     *
     * @return configDir 配置文件夹路径
     */
    private static File getConfigDir() {
        File configDir = new File(MAIN_DIR, "config");
        ensureDir(configDir);
        return configDir;
    }

    /**
     * 获取指定用户的配置文件夹路径。
     *
     * @param userId 用户ID
     */
    public static File getUserConfigDir(String userId) {
        File configDir = new File(CONFIG_DIR, userId);
        ensureDir(configDir);
        return configDir;
    }

    /**
     * 获取默认的配置文件
     *
     * @return configFile 默认配置文件
     */
    public static File getDefaultConfigV2File() {
        return new File(CONFIG_DIR, "config_v2.json");
    }

    /**
     * 设置默认的配置文件
     *
     * @param json 新的配置文件内容
     */
    public static synchronized boolean setDefaultConfigV2File(String json) {
        return write2File(json, new File(CONFIG_DIR, "config_v2.json"));
    }

    /**
     * 获取指定用户的配置文件
     *
     * @param userId 用户ID
     * @return 指定用户的配置文件
     */
    public static synchronized File getConfigV2File(String userId) {
        File confV2File = new File(CONFIG_DIR + File.separator + userId, "config_v2.json");
        // 如果新配置文件不存在，则尝试从旧配置文件迁移
        if (!confV2File.exists()) {
            File oldFile = new File(CONFIG_DIR, "config_v2-" + userId + ".json");
            if (oldFile.exists()) {
                String content = readFromFile(oldFile);
                if (write2File(content, confV2File)) {
                    if (!oldFile.delete()) {
                        Log.error(TAG, "Failed to delete old config file: " + oldFile.getAbsolutePath());
                    }
                } else {
                    confV2File = oldFile;
                    Log.error(TAG, "Failed to migrate config file for user: " + userId);
                }
            }
        }
        return confV2File;
    }

    public static synchronized boolean setConfigV2File(String userId, String json) {
        return write2File(json, new File(CONFIG_DIR + File.separator + userId, "config_v2.json"));
    }

    public static synchronized File getTargetFileofUser(String userId, String fullTargetFileName) {
        if (userId == null || userId.isEmpty()) {
            Log.error(TAG, "Invalid userId for target file: " + fullTargetFileName);
            // 返回一个默认文件或null
            return null;
        }
        // 先确保用户目录存在
        File userDir = new File(CONFIG_DIR, userId);
        ensureDir(userDir);

        File targetFile = new File(userDir, fullTargetFileName);
        // 如果文件不存在且不是目录，尝试创建
        if (!targetFile.exists()) {
            try {
                if (targetFile.createNewFile()) {
                    Log.record(TAG, targetFile.getName() + " created successfully");
                } else {
                    Log.record(TAG, targetFile.getName() + " creation failed");
                }
            } catch (IOException e) {
                Log.error(TAG, "Failed to create file: " + targetFile.getName());
                Log.printStackTrace(TAG, e);
            }
        } else {
            // 检查文件权限
            boolean canRead = targetFile.canRead();
            boolean canWrite = targetFile.canWrite();
            Log.record(TAG, fullTargetFileName + " permissions: r=" + canRead + "; w=" + canWrite);

            // 如果文件存在但没有写入权限，尝试设置权限
            if (!canWrite) {
                if (targetFile.setWritable(true)) {
                    Log.record(TAG, targetFile.getName() + " write permission set successfully");
                } else {
                    Log.record(TAG, targetFile.getName() + " write permission set failed");
                }
            }
        }

        return targetFile;
    }

    public static synchronized File getTargetFileofDir(File dir, String fullTargetFileName) {
        // 先确保目录存在
        ensureDir(dir);

        // 创建目标文件对象
        File targetFile = new File(dir, fullTargetFileName);

        // 如果文件不存在，尝试创建
        if (!targetFile.exists()) {
            try {
                if (targetFile.createNewFile()) {
                    Log.record(TAG, "File created successfully: " + targetFile.getAbsolutePath());
                } else {
                    Log.record(TAG, "File creation failed: " + targetFile.getAbsolutePath());
                }
            } catch (IOException e) {
                Log.error(TAG, "Failed to create file: " + targetFile.getAbsolutePath());
                Log.printStackTrace(TAG, e);
            }
        } else {
            // 如果文件存在，检查权限
            boolean canRead = targetFile.canRead();
            boolean canWrite = targetFile.canWrite();
            Log.record(TAG, "File permissions for " + targetFile.getAbsolutePath() + ": r=" + canRead + "; w=" + canWrite);

            // 如果文件没有写入权限，尝试设置权限
            if (!canWrite) {
                if (targetFile.setWritable(true)) {
                    Log.record(TAG, "Write permission set successfully for file: " + targetFile.getAbsolutePath());
                } else {
                    Log.record(TAG, "Write permission set failed for file: " + targetFile.getAbsolutePath());
                }
            }
        }

        return targetFile;
    }

    public static synchronized boolean setTargetFileofDir(String content, File TargetFileName) {
        return write2File(content, TargetFileName);
    }

    public static File getSelfIdFile(String userId) {
        return getTargetFileofUser(userId, "self.json");
    }

    public static File getFriendIdMapFile(String userId) {
        return getTargetFileofUser(userId, "friend.json");
    }

    public static File runtimeInfoFile(String userId) {
        return getTargetFileofUser(userId, "runtime.json");
    }

    /**
     * 获取用户状态文件
     *
     * @param userId 用户ID
     * @return 用户状态文件
     */
    public static File getStatusFile(String userId) {
        return getTargetFileofUser(userId, "status.json");
    }

    /**
     * 获取统计文件
     */
    public static File getStatisticsFile() {
        return getTargetFileofDir(MAIN_DIR, "statistics.json");
    }

    /**
     * 获取已经导出的统计文件在载目录中
     *
     * @return 导出的统计文件
     */

    public static File getFriendWatchFile(String userId) {
        return getTargetFileofUser(userId, "friendWatch.json");
    }

    public static File getappConfigFile() {
        return getTargetFileofDir(CONFIG_DIR, "appConfig.json");
    }

    /**
     * 导出文件到下载目录
     *
     * @param file 要导出的文件
     * @return 导出后的文件
     */
    public static File exportFile(File file, boolean hasTime) {
        File exportDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), CONFIG_DIR_NAME);
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            Log.error(TAG, "Failed to create export directory: " + exportDir.getAbsolutePath());
            return null;
        }

        // 获取文件的原始文件名和扩展名
        String fileNameWithoutExtension = file.getName().substring(0, file.getName().lastIndexOf('.'));
        String fileExtension = file.getName().substring(file.getName().lastIndexOf('.'));
        String newFileName;
        if (hasTime) {
            // 获取当前日期和时间，并格式化为字符串
            @SuppressLint("SimpleDateFormat") SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");
            String dateTimeString = simpleDateFormat.format(new Date());
            // 生成新的文件名，包含日期和时间
            newFileName = fileNameWithoutExtension + "_" + dateTimeString + fileExtension;
        } else {
            newFileName = fileNameWithoutExtension + fileExtension;
        }
        File exportFile = new File(exportDir, newFileName);
        if (exportFile.exists() && exportFile.isDirectory()) {
            if (!exportFile.delete()) {
                Log.error(TAG, "Failed to delete existing directory: " + exportFile.getAbsolutePath());
                return null;
            }
        }
        if (!copy(file, exportFile)) {
            Log.error(TAG, "Failed to copy file: " + file.getAbsolutePath() + " to " + exportFile.getAbsolutePath());
            return null;
        }
        return exportFile;
    }

    /**
     * 获取城市代码文件
     *
     * @return 城市代码文件
     */
    public static File getCityCodeFile() {
        File cityCodeFile = new File(MAIN_DIR, "cityCode.json");
        if (cityCodeFile.exists() && cityCodeFile.isDirectory()) {
            if (!cityCodeFile.delete()) {
                Log.error(TAG, "Failed to delete directory: " + cityCodeFile.getAbsolutePath());
            }
        }
        return cityCodeFile;
    }

    /**
     * 确保日志文件存在，如果文件是一个目录则删除并创建新文件。 如果文件不存在，则创建新文件。
     *
     * @param logFileName 日志文件的名称
     * @return 日志文件的File对象
     */
    private static File ensureLogFile(String logFileName) {
        File logFile = new File(Files.LOG_DIR, logFileName);
        if (logFile.exists() && logFile.isDirectory()) {
            if (logFile.delete()) {
                Log.record(TAG, "日志" + logFile.getName() + "目录存在，删除成功！");
            } else {
                Log.error(TAG, "日志" + logFile.getName() + "目录存在，删除失败！");
            }
        }
        if (!logFile.exists()) {
            try {
                if (logFile.createNewFile()) {
                    Log.record(TAG, "日志" + logFile.getName() + "文件不存在，创建成功！");
                } else {
                    Log.error(TAG, "日志" + logFile.getName() + "文件不存在，创建失败！");
                }
            } catch (IOException ignored) {
                // 忽略创建文件时可能出现的异常
            }
        }
        return logFile;
    }

    /**
     * 根据日志名称生成带有日期的日志文件名。
     *
     * @param logName 日志名称
     * @return 对应文件
     */
    public static String getLogFile(String logName) {
        return logName + ".log";
    }

    public static File getRuntimeLogFile() {
        return ensureLogFile(getLogFile("runtime"));
    }

    public static File getRecordLogFile() {
        return ensureLogFile(getLogFile("record"));
    }

    public static File getDebugLogFile() {
        return ensureLogFile(getLogFile("debug"));
    }

    public static File getCaptureLogFile() {
        return ensureLogFile(getLogFile("capture"));
    }

    public static File getForestLogFile() {
        return ensureLogFile(getLogFile("forest"));
    }

    public static File getFarmLogFile() {
        return ensureLogFile(getLogFile("farm"));
    }

    public static File getOtherLogFile() {
        return ensureLogFile(getLogFile("other"));
    }

    public static File getErrorLogFile() {
        return ensureLogFile(getLogFile("error"));
    }

    /**
     * 关闭流对象
     *
     * @param c 要关闭的流对象
     */
    public static void close(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException e) {
                Log.printStackTrace(TAG, e); // 捕获并记录关闭流时的 IO 异常
            }
        }
    }

    /**
     * 从文件中读取内容
     *
     * @param f 要读取的文件
     * @return 文件内容，如果读取失败或没有权限，返回空字符串
     */
    public static String readFromFile(File f) {
        // 检查文件是否存在
        if (!f.exists()) {
            return "";
        }
        // 检查文件是否可读
        if (!f.canRead()) {
            //      Toast.show(f.getName() + "没有读取权限！", true);
            ToastUtil.INSTANCE.showToast(f.getName() + "没有读取权限！");
            return "";
        }
        StringBuilder result = new StringBuilder();
        FileReader fr = null;
        try {
            // 使用 FileReader 读取文件内容
            fr = new FileReader(f);
            char[] chs = new char[1024];
            int len;
            // 按块读取文件内容
            while ((len = fr.read(chs)) >= 0) {
                result.append(chs, 0, len);
            }
        } catch (Throwable t) {
            // 捕获并记录异常
            Log.printStackTrace(TAG, t);
        } finally {
            // 关闭文件流
            close(fr);
        }
        return result.toString();
    }

    public static boolean beforWrite(File f) {
        // 检查文件权限和目录结构
        if (f.exists()) {
            if (!f.canWrite()) {
                ToastUtil.INSTANCE.showToast(f.getAbsoluteFile() + "没有写入权限！");
                return true;
            }
            if (f.isDirectory()) {
                // 删除目录并重新创建文件
                if (!f.delete()) {
                    ToastUtil.INSTANCE.showToast(f.getAbsoluteFile() + "无法删除目录！");
                    return true;
                }
            }
        } else {
            if (!Objects.requireNonNull(f.getParentFile()).mkdirs() && !f.getParentFile().exists()) {
                ToastUtil.INSTANCE.showToast(f.getAbsoluteFile() + "无法创建目录！");
                return true;
            }
        }
        return false;
    }

    /**
     * 将字符串写入文件
     *
     * @param s 要写入的字符串
     * @param f 目标文件
     * @return 写入是否成功
     */
    public static synchronized boolean write2File(String s, File f) {
        if (beforWrite(f)) return false;
        FileWriter fw = null;
        try {
            fw = new FileWriter(f, false);
            fw.write(s);
            fw.flush();
            return true;
        } catch (IOException e) {
            Log.printStackTrace(TAG, e);
            return false;
        } finally {
            // 安全关闭流，忽略 close 时的权限异常
            if (fw != null) {
                try {
                    fw.close();
                } catch (IOException e) {
                    // 捕获 close 时的异常（包括 EPERM）
                    // 数据已经 flush，close 失败不影响写入结果
                    Log.debug(TAG, "文件关闭异常（数据已写入）: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 将源文件的内容复制到目标文件
     *
     * @param source 源文件
     * @param dest   目标文件
     * @return 如果复制成功返回 true，否则返回 false
     */
    public static boolean copy(File source, File dest) {
        try (FileInputStream fileInputStream = new FileInputStream(source);
             FileOutputStream fileOutputStream = new FileOutputStream(createFile(dest), false);
             FileChannel inputChannel = fileInputStream.getChannel();
             FileChannel outputChannel = fileOutputStream.getChannel()) {
            outputChannel.transferFrom(inputChannel, 0, inputChannel.size());
            return true; // 复制成功
        } catch (IOException e) {
            Log.printStackTrace(e);
        }
        return false; // 复制失败
    }

    /**
     * 将输入流（source）中的数据拷贝到输出流（dest）中。 会循环读取输入流的数据并写入输出流，直到读取完毕。 最终关闭输入输出流。
     *
     * @param source 输入流
     * @param dest   输出流
     * @return 如果数据拷贝成功，返回 true；如果发生 IO 异常或拷贝失败，返回 false
     */
    public static boolean streamTo(InputStream source, OutputStream dest) {
        byte[] buffer = new byte[1024]; // 创建一个缓冲区，每次读取 1024 字节
        int length;
        try {
            // 循环读取输入流中的数据并写入输出流
            while ((length = source.read(buffer)) > 0) {
                dest.write(buffer, 0, length); // 写入数据到输出流
                dest.flush(); // 强制将数据从输出流刷新到目的地
            }
            return true; // 成功拷贝数据
        } catch (IOException e) {
            // 捕获 IO 异常并打印堆栈信息
            Log.printStackTrace(e);
        } finally {
            // 关闭输入流和输出流
            closeStream(source);
            closeStream(dest);
        }
        return false; // 拷贝失败或发生异常
    }

    /**
     * 关闭流并处理可能发生的异常
     *
     * @param stream 需要关闭的流对象
     */
    private static void closeStream(AutoCloseable stream) {
        if (stream != null) {
            try {
                stream.close(); // 关闭流
            } catch (Exception e) {
                // 捕获并打印关闭流时的异常
                Log.printStackTrace(e);
            }
        }
    }

    /**
     * 创建一个文件，如果文件已存在且是目录，
     * 则先删除该目录再创建文件。
     * 如果文件不存在，则会先创建父目录，再创建该文件。
     *
     * @param file 需要创建的文件对象
     * @return 创建成功返回文件对象；如果创建失败或发生异常，返回 null
     */
    public static File createFile(File file) {
        // 如果文件已存在且是目录，则先删除该目录
        if (file.exists() && file.isDirectory()) {
            // 如果删除目录失败，返回 null
            if (!file.delete()) return null;
        }
        // 如果文件不存在，则尝试创建文件
        if (!file.exists()) {
            try {
                // 获取父目录文件对象
                File parentFile = file.getParentFile();
                if (parentFile != null) {
                    // 如果父目录不存在，则创建父目录
                    boolean ignore = parentFile.mkdirs();
                }
                // 创建新的文件
                // 如果文件创建失败，返回 null
                if (!file.createNewFile()) return null;
            } catch (Exception e) {
                // 捕获异常并打印堆栈信息
                Log.printStackTrace(e);
                return null;
            }
        }
        // 文件已存在或成功创建，返回文件对象
        return file;
    }

    /**
     * 清空文件内容, 并返回是否清空成功
     *
     * @param file 文件
     * @return 是否清空成功
     */
    public static Boolean clearFile(File file) {
        if (!file.exists()) {
            return false; // 如果文件不存在，则返回 false
        }

        try (FileWriter fileWriter = new FileWriter(file)) {
            // 使用 FileWriter 清空文件内容
            fileWriter.write(""); // 写入空字符串，清空文件内容
            fileWriter.flush(); // 刷新缓存，确保内容写入文件
            return true; // 返回清空成功
        } catch (IOException e) {
            Log.printStackTrace(e);
            return false;
        }
        // 安全关闭流，忽略 close 时的权限异常
        // 捕获 close 时的异常（包括 EPERM）
        // 数据已经 flush，close 失败不影响清空结果
    }

    /**
     * 删除文件或目录（包括子文件和子目录）。如果是目录，则递归删除其中的所有文件和目录。
     *
     * @param file 要删除的文件或目录
     * @return 如果删除成功返回 true，失败返回 false
     */
    public static boolean delFile(File file) {
        if (!file.exists()) {
            ToastUtil.INSTANCE.showToast(file.getAbsoluteFile() + "不存在！别勾把删了");
            Log.record(TAG, "delFile: " + file.getAbsoluteFile() + "不存在！,无须删除");
            return false;
        }

        if (file.isFile()) {
            return deleteFileWithRetry(file); // 处理文件删除
        }

        File[] files = file.listFiles();
        if (files == null) {
            return deleteFileWithRetry(file); // 处理空文件夹删除
        }

        // 递归删除子文件和子文件夹
        boolean allSuccess = true;
        for (File innerFile : files) {
            if (!delFile(innerFile)) {
                allSuccess = false;
            }
        }

        return allSuccess && deleteFileWithRetry(file);
    }

    private static boolean deleteFileWithRetry(File file) {
        int retryCount = 3; // 重试次数
        while (retryCount > 0) {
            if (file.delete()) {
                return true;
            }
            retryCount--;
            Log.record(TAG, "删除失败，重试中: " + file.getAbsolutePath());
            CoroutineUtils.sleepCompat(500); // 等待 500ms 后重试
        }
        Log.error(TAG, "删除失败: " + file.getAbsolutePath());
        return false;
    }
}