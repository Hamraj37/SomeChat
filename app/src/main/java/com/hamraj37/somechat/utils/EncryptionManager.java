package com.hamraj37.somechat.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class EncryptionManager {

    private static final String PREF_NAME = "encryption_prefs";
    private static final String KEY_PRIVATE = "private_key";
    private static final String KEY_PUBLIC = "public_key";

    private static String cachedPrivateKey = null;
    private static String cachedPublicKey = null;

    public static void loadKeys(Context context, String publicKey, String privateKey) {
        cachedPublicKey = publicKey;
        cachedPrivateKey = privateKey;
        
        if (context != null) {
            SharedPreferences.Editor editor = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
            editor.putString(KEY_PUBLIC, publicKey);
            editor.putString(KEY_PRIVATE, privateKey);
            editor.apply();
        }
    }

    public static void loadKeysFromPrefs(Context context) {
        if (cachedPublicKey != null && cachedPrivateKey != null) return;
        
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        cachedPublicKey = prefs.getString(KEY_PUBLIC, null);
        cachedPrivateKey = prefs.getString(KEY_PRIVATE, null);
    }

    public static boolean hasKeys() {
        return cachedPrivateKey != null && cachedPublicKey != null;
    }

    public static void clearKeys(Context context) {
        cachedPrivateKey = null;
        cachedPublicKey = null;
        if (context != null) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply();
        }
    }

    public static String initKeys(Context context) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            
            String publicKeyStr = Base64.encodeToString(kp.getPublic().getEncoded(), Base64.NO_WRAP);
            String privateKeyStr = Base64.encodeToString(kp.getPrivate().getEncoded(), Base64.NO_WRAP);

            loadKeys(context, publicKeyStr, privateKeyStr);
            return publicKeyStr;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String getMyPublicKey(Context context) {
        return cachedPublicKey;
    }

    public static String getMyPrivateKey(Context context) {
        return cachedPrivateKey;
    }

    public static void saveKeys(Context context, String publicKey, String privateKey) {
        loadKeys(context, publicKey, privateKey);
    }

    public static byte[] encryptRaw(byte[] data, SecretKey aesKey) throws Exception {
        Cipher aesCipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey);
        return aesCipher.doFinal(data);
    }

    public static byte[] decryptRaw(byte[] encryptedData, SecretKey aesKey) throws Exception {
        Cipher aesCipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        aesCipher.init(Cipher.DECRYPT_MODE, aesKey);
        return aesCipher.doFinal(encryptedData);
    }

    public static SecretKey generateAESKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        return keyGen.generateKey();
    }

    public static String encodeKey(SecretKey key) {
        return Base64.encodeToString(key.getEncoded(), Base64.NO_WRAP);
    }

    public static SecretKey decodeKey(String encodedKey) {
        byte[] decodedKey = Base64.decode(encodedKey, Base64.NO_WRAP);
        return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
    }

    public static String encrypt(String content, String recipientPublicKeyStr, String senderPublicKeyStr) {
        try {
            // 1. Generate AES Key
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            SecretKey aesKey = keyGen.generateKey();

            // 2. Encrypt Content with AES
            Cipher aesCipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            aesCipher.init(Cipher.ENCRYPT_MODE, aesKey);
            byte[] encryptedContent = aesCipher.doFinal(content.getBytes());

            // 3. Encrypt AES Key with Recipient RSA Public Key
            byte[] recipientPubKeyBytes = Base64.decode(recipientPublicKeyStr, Base64.NO_WRAP);
            PublicKey recipientPublicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(recipientPubKeyBytes));
            Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            rsaCipher.init(Cipher.ENCRYPT_MODE, recipientPublicKey);
            byte[] recipientEncryptedAesKey = rsaCipher.doFinal(aesKey.getEncoded());

            // 4. Encrypt AES Key with Sender RSA Public Key
            byte[] senderPubKeyBytes = Base64.decode(senderPublicKeyStr, Base64.NO_WRAP);
            PublicKey senderPublicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(senderPubKeyBytes));
            rsaCipher.init(Cipher.ENCRYPT_MODE, senderPublicKey);
            byte[] senderEncryptedAesKey = rsaCipher.doFinal(aesKey.getEncoded());

            // Format: [RecipientEncAESKey] : [SenderEncAESKey] : [EncryptedContent]
            return Base64.encodeToString(recipientEncryptedAesKey, Base64.NO_WRAP) + ":" +
                   Base64.encodeToString(senderEncryptedAesKey, Base64.NO_WRAP) + ":" +
                   Base64.encodeToString(encryptedContent, Base64.NO_WRAP);
        } catch (Exception e) {
            e.printStackTrace();
            return content;
        }
    }

    public static String decrypt(String encryptedData, Context context, boolean isSender) {
        if (encryptedData == null || !encryptedData.contains(":")) return encryptedData;
        
        // If it starts with { it's likely a JSON object (already decrypted or not encrypted)
        // If it starts with local: it's a local file URI placeholder
        if (encryptedData.startsWith("{") || encryptedData.startsWith("local:")) return encryptedData;

        try {
            String[] parts = encryptedData.split(":");
            if (parts.length < 3) return encryptedData;

            byte[] encryptedAesKey = Base64.decode(isSender ? parts[1] : parts[0], Base64.NO_WRAP);
            byte[] encryptedContent = Base64.decode(parts[2], Base64.NO_WRAP);

            // 1. Get Private Key (From Memory)
            if (cachedPrivateKey == null) return encryptedData;

            byte[] privateKeyBytes = Base64.decode(cachedPrivateKey, Base64.NO_WRAP);
            PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));

            // 2. Decrypt AES Key with RSA Private Key
            Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] aesKeyBytes = rsaCipher.doFinal(encryptedAesKey);
            SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");

            // 3. Decrypt Content with AES
            Cipher aesCipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            aesCipher.init(Cipher.DECRYPT_MODE, aesKey);
            return new String(aesCipher.doFinal(encryptedContent));
        } catch (Exception e) {
            return encryptedData;
        }
    }
}
