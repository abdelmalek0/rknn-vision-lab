package com.smartprintsksa.rknn_sdk;

import android.os.Build;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Encryption {
    private static final String TAG = "ENCRYPTION";
    private static String jargon = "5c44f2467766";
    private static boolean trimmed = false;
    private static char number = '0';
    private static char letter = 'A';
    private static boolean validated = false;

    protected static byte[] d =
            "N5Iq1stLlFZZXlYXw".getBytes(StandardCharsets.UTF_8);
    protected static byte[] lai = "q1strj6P".getBytes(StandardCharsets.UTF_8);

    public static byte[] pw = "rj6PyRNPUBgOPQv".getBytes(StandardCharsets.UTF_8);
    public static byte[] em = "gOPQvN5I".getBytes(StandardCharsets.UTF_8);
    private static String byteArrayToHex(byte[] byteArray) {
        StringBuilder sb = new StringBuilder(byteArray.length * 2);
        for(byte b: byteArray)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static byte[] hexStringToByteArray(String str) {
        int len = str.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(str.charAt(i), 16) << 4)
                    + Character.digit(str.charAt(i+1), 16));
        }
        return data;
    }

    private static String hexToBase8(String hexString) {
        StringBuilder binaryString = new StringBuilder();
        StringBuilder octalString = new StringBuilder();

        // Convert the hexadecimal string to binary string
        for (int i = 0; i < hexString.length(); i++) {
            StringBuilder binary = new StringBuilder(Integer.toBinaryString(Character.digit(hexString.charAt(i), 16)));
            while (binary.length() < 4) {
                binary.insert(0, "0");
            }
            binaryString.append(binary);
        }

        // Convert the binary string to octal string
        int length = binaryString.length();
        int padding = length % 3;
        if (padding > 0) {
            for (int i = 0; i < 3 - padding; i++) {
                binaryString.insert(0, "0");
            }
        }
        length = binaryString.length();
        for (int i = 0; i < length; i += 3) {
            String triplet = binaryString.substring(i, i + 3);
            int octal = Integer.parseInt(triplet, 2);
            octalString.append(octal);
        }

        return octalString.toString();
    }

    private static String base8ToHex(String octalString) {
        StringBuilder binaryString = new StringBuilder();
        StringBuilder hexString = new StringBuilder();

        // Convert the octal string to binary string
        for (int i = 0; i < octalString.length(); i++) {
            StringBuilder binary = new StringBuilder(Integer.toBinaryString(Character.digit(octalString.charAt(i), 8)));
            while (binary.length() < 3) {
                binary.insert(0, "0");
            }
            binaryString.append(binary);
        }

        // Convert the binary string to hexadecimal string
        int length = binaryString.length();
        int padding = length % 4;
        if (padding > 0) {
            for (int i = 0; i < 4 - padding; i++) {
                binaryString.insert(0, "0");
            }
        }
        length = binaryString.length();
        for (int i = 0; i < length; i += 4) {
            String quadruplet = binaryString.substring(i, i + 4);
            int decimal = Integer.parseInt(quadruplet, 2);
            hexString.append(Integer.toHexString(decimal));
        }

        if (hexString.charAt(0) == '0') return hexString.substring(1);
        return hexString.toString();
    }

    private static byte[] trim(byte[] firstArray, byte[] secondArray) {
        byte[] mergedArray = new byte[firstArray.length + secondArray.length];
        System.arraycopy(firstArray, 0, mergedArray, 0, firstArray.length);
        System.arraycopy(secondArray, 0, mergedArray, firstArray.length, secondArray.length);
        return mergedArray;
    }

    private static byte[] encrypt(byte[] plaintext, byte[] key, byte[] iv) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
        AlgorithmParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance("AES/CBC/ISO10126Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);

        return cipher.doFinal(plaintext);
    }

    protected static byte[] decrypt(byte[] ciphertext, byte[] key, byte[] iv)
            throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
        AlgorithmParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance("AES/CBC/ISO10126Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);

        return cipher.doFinal(ciphertext);
    }

    private static boolean validateActivationCode(String validation, String device) {
        if (validation.isEmpty()) return false;
        try {
            String[] decoded = new String(
                    decrypt(hexStringToByteArray(validation), pw, em),
                    StandardCharsets.UTF_8
            ).split(" ");
            StringBuilder output = new StringBuilder();
            for (int i = 0; i < jargon.length() / 2; i += 1) {
                output
                        .append(jargon.charAt(jargon.length() - 1 - i))
                        .append(jargon.charAt(i));
            }
            return (
                    device.equals(decoded[0]) &&
                            output.toString().equals(base8ToHex(decoded[1]))
            );
        } catch (Exception e) {
            Logger.error(TAG, "Failure: " + e.getMessage());
        }
        return false;
    }
    public static String getUniqueID() {
        String serialNumber;

        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class);

            serialNumber = (String) get.invoke(c, "gsm.sn1");
            if (serialNumber.equals("")) serialNumber = (String) get.invoke(
                    c,
                    "ril.serialnumber"
            );
            if (serialNumber.equals("")) serialNumber = (String) get.invoke(
                    c,
                    "ro.serialno"
            );
            if (serialNumber.equals("")) serialNumber = (String) get.invoke(
                    c,
                    "sys.serialnumber"
            );
            if (serialNumber.equals("")) serialNumber = Build.SERIAL;

            // If none of the methods above worked
            if (serialNumber.equals("")) serialNumber = "SMARTPRINTS-EMPTY";
        } catch (Exception e) {
            Logger.error(TAG, "Failure: " + e.getMessage());
            serialNumber = "SMARTPRINTS-EXCEPTION";
        }

        return serialNumber;
    }

}
