package tsubota1991tech.github.io.aws_game_manager.service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import tsubota1991tech.github.io.aws_game_manager.domain.SystemSetting;
import tsubota1991tech.github.io.aws_game_manager.repository.SystemSettingRepository;
import tsubota1991tech.github.io.aws_game_manager.security.EncryptionService;

@Service
public class SystemSettingService {

    public static final String DISCORD_BOT_TOKEN_KEY = "DISCORD_BOT_TOKEN";
    public static final String SPOT_OPERATION_ENABLED_KEY = "SPOT_OPERATION_ENABLED";
    public static final String AUTO_SCALING_INSTANCE_TYPES_KEY = "AUTO_SCALING_INSTANCE_TYPES";

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

    @Transactional(readOnly = true)
    public boolean isSpotOperationEnabled() {
        return Boolean.parseBoolean(getSettingValue(SPOT_OPERATION_ENABLED_KEY));
    }

    @Transactional(readOnly = true)
    public List<String> getAutoScalingInstanceTypes() {
        String raw = getSettingValue(AUTO_SCALING_INSTANCE_TYPES_KEY);
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }

        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    @Transactional
    public void updateDiscordBotToken(String token) {
        upsertSetting(DISCORD_BOT_TOKEN_KEY, encryptIfNeeded(token));
    }

    @Transactional
    public void updateSpotOperationEnabled(boolean enabled) {
        upsertSetting(SPOT_OPERATION_ENABLED_KEY, String.valueOf(enabled));
    }

    @Transactional
    public void updateAutoScalingInstanceTypes(List<String> instanceTypes) {
        if (instanceTypes == null || instanceTypes.isEmpty()) {
            upsertSetting(AUTO_SCALING_INSTANCE_TYPES_KEY, "");
            return;
        }

        String joined = instanceTypes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(","));
        upsertSetting(AUTO_SCALING_INSTANCE_TYPES_KEY, joined);
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
