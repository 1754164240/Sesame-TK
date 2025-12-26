package fansirsqi.xposed.sesame.entity;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * 表示支付宝版本的实体类，可进行版本比较。
 */
@Getter
public class AlipayVersion implements Comparable<AlipayVersion> {
    // 原始版本字符串
    private final String versionString;
    // 版本号数组，用于比较
    private final Integer[] versionArray;
    /**
     * 构造方法，将版本字符串解析为整数数组。
     * @param versionString 版本号字符串（以点号分隔，例如 "10.1.1"）
     */
    public AlipayVersion(String versionString) {
        this.versionString = versionString;
        String[] split = versionString.split("\\.");
        int length = split.length;
        versionArray = new Integer[length];
        for (int i = 0; i < length; i++) {
            try {
                getVersionArray()[i] = Integer.parseInt(split[i]);
            } catch (NumberFormatException e) {
                getVersionArray()[i] = Integer.MAX_VALUE; // 如果解析失败，使用 Integer.MAX_VALUE 表示
            }
        }
    }
    /**
     * 实现版本比较逻辑。
     * @param alipayVersion 需要比较的另一个 AlipayVersion 实例
     * @return -1 表示当前版本小于对比版本，1 表示大于，0 表示相等
     */
    @Override
    public int compareTo(AlipayVersion alipayVersion) {
        int thisLength = getVersionArray().length;
        int thatLength = alipayVersion.getVersionArray().length;
        int compareResult = Integer.compare(thisLength, thatLength); // 比较长度
        int minLength = Math.min(thisLength, thatLength); // 取最小长度
        // 按版本号逐段比较
        for (int i = 0; i < minLength; i++) {
            int thisVer = getVersionArray()[i];
            int thatVer = alipayVersion.getVersionArray()[i];
            if (thisVer != thatVer) {
                return Integer.compare(thisVer, thatVer);
            }
        }
        // 如果所有对应段都相等，返回长度比较结果
        return compareResult;
    }

    @Override
    public @NotNull String toString() {
        return getVersionString();
    }
}
