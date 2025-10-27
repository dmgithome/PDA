package com.pda.hcw.rfid;

/**
 * EPC 格式化工具（接收即格式化，后续不再二次处理）。
 * 规则（按你方确认）：
 * 1) 合规一：以"000000"开头且总长为24的字符串，取后18位作为标准值；
 * 2) 合规二：以"00"结尾且总长为20的字符串，取前18位作为标准值；
 * 3) 其他形态：保持原样；
 * 同时为兼容设备返回HEX字符串的场景，额外支持两种“等价HEX形态”的识别：
 *  - 形态A：HEX以"000000"开头且总长为42（3字节0x00 + 18个ASCII字符的HEX编码=36位），
 *           取后36位按ASCII解码为18字符后返回；
 *  - 形态B：HEX总长为40，按ASCII解码为20字符且以"00"结尾，则返回解码后前18字符；
 * 其余均原样返回，保证“未命中规则不改变原值”。
 */
public final class EpcFormatter {
    private EpcFormatter() {}

    /**
     * 接收即格式化：仅按约定两条规则转换；未匹配则返回原值（去除首尾空白）。
     * 不抛异常，任何异常场景安全回退为原值。
     */
    public static String normalizeOnReceive(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty()) return s;

        // 直接字符形态（非HEX）
        if (s.length() == 24 && s.startsWith("000000")) {
            return s.substring(6);
        }
        if (s.length() == 20 && s.endsWith("00")) {
            return s.substring(0, 18);
        }

        // 尝试识别等价HEX形态（仅在明显命中长度与模式时解码，不命中绝不改变原值）
        if (isHexLike(s)) {
            // 形态A：000000 + 18个ASCII字符（HEX编码共36位）= 总长42
            if (s.length() == 42 && s.startsWith("000000")) {
                String tail = s.substring(6);
                String decoded = tryAsciiFromHex(tail);
                if (decoded != null && decoded.length() == 18) {
                    return decoded;
                }
            }
            // 形态B：HEX总长40，ASCII解码后长度20且以"00"结尾 → 取前18
            if (s.length() == 40) {
                String decoded = tryAsciiFromHex(s);
                if (decoded != null && decoded.length() == 20 && decoded.endsWith("00")) {
                    return decoded.substring(0, 18);
                }
            }
        }

        // 未命中任何规则，保持原样
        return s;
    }

    // ====== 辅助 ======
    private static boolean isHexLike(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
            if (!hex) return false;
        }
        return (s.length() % 2) == 0; // HEX必须偶数长度
    }

    /** 将HEX按ASCII解码；若遇到非可见ASCII范围，仍按字符拼接，不抛错；失败时返回null。 */
    private static String tryAsciiFromHex(String hex) {
        try {
            if (hex == null || (hex.length() % 2) != 0) return null;
            StringBuilder out = new StringBuilder(hex.length() / 2);
            for (int i = 0; i < hex.length(); i += 2) {
                int b = Integer.parseInt(hex.substring(i, i + 2), 16);
                out.append((char) b);
            }
            return out.toString();
        } catch (Exception ignore) {
            return null;
        }
    }
}

