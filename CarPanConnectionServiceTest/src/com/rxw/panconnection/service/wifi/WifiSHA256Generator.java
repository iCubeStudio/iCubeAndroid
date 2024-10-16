package com.rxw.panconnection.service.wifi;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class WifiSHA256Generator {

    /*=======================================================*
     * generates a SHA-256 hash from the provided passphrase *
     *=======================================================*/
    private String generateSHA256Hash(String Passphrase) {
        try {
            // Get a MessageDigest instance for SHA-256
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Compute the hash of the passphrase bytes
            byte[] hash = digest.digest(Passphrase.getBytes("UTF-8"));
            // Convert the byte array into a hexadecimal string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                // Convert each byte to a two-digit hexadecimal number
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            // Return the resulting hexadecimal string
            return hexString.toString();
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            // Throw a runtime exception if an error occurs
            throw new RuntimeException(e);
        }
    }
}