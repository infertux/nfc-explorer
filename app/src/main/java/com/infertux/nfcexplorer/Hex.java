package com.infertux.nfcexplorer;

final class Hex {
    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public final static String bytesToHex(byte[] bytes) {
        if (bytes == null) return null;

        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return "0x" + new String(hexChars);
    }

    public final static String bytesToHexAndString(byte[] bytes) {
        if (bytes == null) return null;

        return bytesToHex(bytes) + " (" + new String(bytes) + ")";
    }
}
