package tsubota1991tech.github.io.aws_game_manager.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

@Service
public class EncryptionService {

    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int AUTH_TAG_LENGTH = 128;

    private static EncryptionService instance;

    private final String secret;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptionService(@Value("${app.encryption.secret:}") String secret) {
        this.secret = secret;
    }

    @PostConstruct
    void initialize() {
        instance = this;
        if (!StringUtils.hasText(secret)) {
            log.warn("app.encryption.secret が設定されていません。機密情報を保存する前に設定してください。");
        }
    }

    public static EncryptionService getInstance() {
        return instance;
    }

    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return plainText;
        }

        ensureSecretConfigured();

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            cipher.init(Cipher.ENCRYPT_MODE, buildSecretKey(), new GCMParameterSpec(AUTH_TAG_LENGTH, iv));
            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherBytes.length);
            buffer.put(iv);
            buffer.put(cipherBytes);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception ex) {
            throw new IllegalStateException("機密情報の暗号化に失敗しました。設定を確認してください。", ex);
        }
    }

    public String decrypt(String cipherText) {
        if (!StringUtils.hasText(cipherText)) {
            return cipherText;
        }

        ensureSecretConfigured();

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(cipherText);
        } catch (IllegalArgumentException ex) {
            log.warn("暗号化フォーマットではない値を復号しようとしました。平文として扱います。");
            return cipherText;
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);

            byte[] cipherBytes = new byte[buffer.remaining()];
            buffer.get(cipherBytes);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, buildSecretKey(), new GCMParameterSpec(AUTH_TAG_LENGTH, iv));
            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.error("暗号化データの復号に失敗しました。暗号鍵を確認してください。", ex);
            throw new IllegalStateException("暗号化データの復号に失敗しました。", ex);
        }
    }

    private SecretKeySpec buildSecretKey() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            byte[] truncatedKey = new byte[16];
            System.arraycopy(keyBytes, 0, truncatedKey, 0, truncatedKey.length);
            return new SecretKeySpec(truncatedKey, "AES");
        } catch (Exception ex) {
            throw new IllegalStateException("暗号鍵の生成に失敗しました。", ex);
        }
    }

    private void ensureSecretConfigured() {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("app.encryption.secret が設定されていません。環境変数 APP_ENCRYPTION_SECRET などで設定してください。");
        }
    }
}
