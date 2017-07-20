package com.zendesk.maxwell.row;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;

public class RowEncrypt {

	static final Logger LOGGER = LoggerFactory.getLogger(RowEncrypt.class);

	public static String encrypt(String value, String secretKey, byte[] initVector) {
		try {
			IvParameterSpec iv = new IvParameterSpec(initVector);
			SecretKeySpec skeySpec = new SecretKeySpec(secretKey.getBytes("ASCII"), "AES");

			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
			cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);

			byte[] encrypted = cipher.doFinal(value.getBytes("UTF-8"));

			return Base64.encodeBase64String(encrypted);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		return null;
	}

	public static String decrypt(String value, String secretKey, String initVector) {
		try {
			IvParameterSpec ivSpec = new IvParameterSpec(Base64.decodeBase64(initVector.getBytes("ASCII")));
			SecretKeySpec skeySpec = new SecretKeySpec(secretKey.getBytes("ASCII"), "AES");

			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
			cipher.init(Cipher.DECRYPT_MODE, skeySpec, ivSpec);

			return new String(cipher.doFinal(Base64.decodeBase64(value.getBytes("ASCII"))), Charset.forName("ASCII"));
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		return null;
	}
}
