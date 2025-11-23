package tsubota1991tech.github.io.aws_game_manager.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import tsubota1991tech.github.io.aws_game_manager.domain.SystemSetting;
import tsubota1991tech.github.io.aws_game_manager.repository.SystemSettingRepository;
import tsubota1991tech.github.io.aws_game_manager.security.EncryptionService;

@Service
public class SystemSettingService {

    public static final String DISCORD_BOT_TOKEN_KEY = "DISCORD_BOT_TOKEN";

    private final SystemSettingRepository systemSettingRepository;
    private final EncryptionService encryptionService;

    public SystemSettingService(SystemSettingRepository systemSettingRepository,
                               EncryptionService encryptionService) {
        this.systemSettingRepository = systemSettingRepository;
        this.encryptionService = encryptionService;
    }

    @Transactional(readOnly = true)
    public String getDiscordBotToken() {
        return getSettingValue(DISCORD_BOT_TOKEN_KEY);
    }

    @Transactional
    public void updateDiscordBotToken(String token) {
        upsertSetting(DISCORD_BOT_TOKEN_KEY, encryptIfNeeded(token));
    }

    @Transactional
    public void ensureSettingExists(String key, String defaultValue) {
        systemSettingRepository.findBySettingKey(key)
                .orElseGet(() -> {
                    SystemSetting setting = new SystemSetting();
                    setting.setSettingKey(key);
                    setting.setSettingValue(defaultValue);
                    return systemSettingRepository.save(setting);
                });
    }

    private String getSettingValue(String key) {
        Optional<SystemSetting> setting = systemSettingRepository.findBySettingKey(key);
        return setting.map(value -> decryptIfNeeded(value.getSettingValue())).orElse(null);
    }

    private void upsertSetting(String key, String value) {
        SystemSetting setting = systemSettingRepository.findBySettingKey(key)
                .orElseGet(SystemSetting::new);

        setting.setSettingKey(key);
        if (StringUtils.hasText(value)) {
            setting.setSettingValue(value.trim());
        } else {
            setting.setSettingValue("");
        }

        systemSettingRepository.save(setting);
    }

    private String encryptIfNeeded(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return encryptionService.encrypt(value.trim());
    }

    private String decryptIfNeeded(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        try {
            return encryptionService.decrypt(value);
        } catch (IllegalStateException ex) {
            return value;
        }
    }
}
