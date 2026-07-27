package org.telegram.messenger;

import android.content.SharedPreferences;

import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.LauncherIconController;

/**
 * Конфиг внешнего вида приложения.
 *
 * Позволяет экспортировать текущую персонализацию пользователя (тема, акцент,
 * обои, шрифт, иконка, премиум-оформление) в JSON-строку и применять её обратно.
 * Всё применяется строго локально, без серверных запросов: премиум-элементы
 * оформления выставляются только в локальном объекте пользователя.
 */
public class AppearanceConfig {

    private static final String MARKER = "tg-appearance-config";
    private static final int VERSION = 1;

    /**
     * Создать конфиг на основе текущей персонализации.
     * @return JSON-строка конфига или null при ошибке.
     */
    public static String exportConfig(int currentAccount) {
        try {
            JSONObject json = new JSONObject();
            json.put("app", MARKER);
            json.put("version", VERSION);

            // Тема
            JSONObject theme = new JSONObject();
            Theme.ThemeInfo dayTheme = Theme.getCurrentTheme();
            if (dayTheme != null) {
                theme.put("day", dayTheme.getKey());
                theme.put("dayAccent", dayTheme.currentAccentId);
            }
            Theme.ThemeInfo nightTheme = Theme.getCurrentNightTheme();
            if (nightTheme != null) {
                theme.put("night", nightTheme.getKey());
                theme.put("nightAccent", nightTheme.currentAccentId);
            }
            theme.put("autoNightType", Theme.selectedAutoNightType);
            theme.put("nightNow", Theme.isCurrentThemeNight());
            json.put("theme", theme);

            // Обои чатов (переопределённые для активной темы)
            Theme.ThemeInfo activeTheme = Theme.getActiveTheme();
            if (activeTheme != null && activeTheme.overrideWallpaper != null) {
                Theme.OverrideWallpaperInfo wp = activeTheme.overrideWallpaper;
                JSONObject wallpaper = new JSONObject();
                wallpaper.put("slug", wp.slug != null ? wp.slug : "");
                wallpaper.put("color", wp.color);
                wallpaper.put("gradient1", wp.gradientColor1);
                wallpaper.put("gradient2", wp.gradientColor2);
                wallpaper.put("gradient3", wp.gradientColor3);
                wallpaper.put("rotation", wp.rotation);
                wallpaper.put("blur", wp.isBlurred);
                wallpaper.put("motion", wp.isMotion);
                wallpaper.put("intensity", wp.intensity);
                json.put("wallpaper", wallpaper);
            }

            // Настройки чатов и анимаций
            JSONObject chat = new JSONObject();
            chat.put("fontSize", SharedConfig.fontSize);
            chat.put("bubbleRadius", SharedConfig.bubbleRadius);
            chat.put("bigEmoji", SharedConfig.allowBigEmoji);
            chat.put("systemEmoji", SharedConfig.useSystemEmoji);
            chat.put("systemBoldFont", SharedConfig.useSystemBoldFont);
            chat.put("animations", SharedConfig.animationsEnabled());
            chat.put("liteMode", LiteMode.getValue(true));
            json.put("chat", chat);

            // Иконка приложения
            for (LauncherIconController.LauncherIcon icon : LauncherIconController.LauncherIcon.values()) {
                if (LauncherIconController.isEnabled(icon)) {
                    json.put("icon", icon.name());
                    break;
                }
            }

            // Премиум-оформление профиля (только если пользователь авторизован)
            UserConfig userConfig = UserConfig.getInstance(currentAccount);
            if (userConfig.isClientActivated()) {
                TLRPC.User me = userConfig.getCurrentUser();
                if (me != null) {
                    json.put("premium", me.premium);

                    JSONObject profile = new JSONObject();
                    if (me.color instanceof TLRPC.TL_peerColor && (me.color.flags & 1) != 0) {
                        profile.put("nameColor", me.color.color);
                    } else {
                        profile.put("nameColor", -1);
                    }
                    profile.put("nameEmoji", UserObject.getEmojiId(me));
                    profile.put("profileColor", UserObject.getProfileColorId(me));
                    profile.put("profileEmoji", UserObject.getOnlyProfileEmojiId(me));
                    Long status = UserObject.getEmojiStatusDocumentId(me);
                    profile.put("emojiStatus", status != null ? (long) status : 0);
                    json.put("profile", profile);
                }
            }

            return json.toString();
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    /**
     * Проверить, является ли текст корректным конфигом.
     */
    public static boolean isConfig(String text) {
        if (text == null) {
            return false;
        }
        try {
            JSONObject json = new JSONObject(text);
            return MARKER.equals(json.optString("app"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Применить конфиг. Все изменения выполняются локально.
     * @return true, если конфиг успешно применён.
     */
    public static boolean importConfig(int currentAccount, String text) {
        try {
            JSONObject json = new JSONObject(text);
            if (!MARKER.equals(json.optString("app"))) {
                return false;
            }

            applyProfile(currentAccount, json);
            applyTheme(json);
            applyWallpaper(json);
            applyChatSettings(json);
            applyIcon(json);

            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    private static void applyProfile(int currentAccount, JSONObject json) {
        try {
            UserConfig userConfig = UserConfig.getInstance(currentAccount);
            if (!userConfig.isClientActivated()) {
                return;
            }
            TLRPC.User me = userConfig.getCurrentUser();
            if (me == null) {
                return;
            }

            boolean changed = false;

            // Локальная «премиум-авторизация»: разблокирует премиум-оформление на клиенте
            if (json.optBoolean("premium", false) && !me.premium) {
                me.premium = true;
                MessagesController.getInstance(currentAccount).premiumLocked = false;
                changed = true;
            }

            JSONObject profile = json.optJSONObject("profile");
            if (profile != null) {
                if (profile.has("nameColor")) {
                    if (me.color == null) {
                        me.color = new TLRPC.TL_peerColor();
                    }
                    me.flags2 |= 256;
                    int nameColor = profile.optInt("nameColor", -1);
                    if (nameColor < 0) {
                        me.color.flags &= ~1;
                    } else {
                        me.color.flags |= 1;
                        me.color.color = nameColor;
                    }
                    long nameEmoji = profile.optLong("nameEmoji", 0);
                    if (nameEmoji != 0) {
                        me.color.flags |= 2;
                        me.color.background_emoji_id = nameEmoji;
                    } else {
                        me.color.flags &= ~2;
                        me.color.background_emoji_id = 0;
                    }
                    changed = true;
                }
                if (profile.has("profileColor")) {
                    if (me.profile_color == null) {
                        me.profile_color = new TLRPC.TL_peerColor();
                    }
                    me.flags2 |= 512;
                    int profileColor = profile.optInt("profileColor", -1);
                    if (profileColor < 0) {
                        me.profile_color.flags &= ~1;
                    } else {
                        me.profile_color.flags |= 1;
                        me.profile_color.color = profileColor;
                    }
                    long profileEmoji = profile.optLong("profileEmoji", 0);
                    if (profileEmoji != 0) {
                        me.profile_color.flags |= 2;
                        me.profile_color.background_emoji_id = profileEmoji;
                    } else {
                        me.profile_color.flags &= ~2;
                        me.profile_color.background_emoji_id = 0;
                    }
                    changed = true;
                }
                if (profile.has("emojiStatus")) {
                    long documentId = profile.optLong("emojiStatus", 0);
                    if (documentId > 0) {
                        TLRPC.TL_emojiStatus status = new TLRPC.TL_emojiStatus();
                        status.document_id = documentId;
                        me.emoji_status = status;
                    } else {
                        me.emoji_status = new TLRPC.TL_emojiStatusEmpty();
                    }
                    changed = true;
                }
            }

            if (changed) {
                userConfig.setCurrentUser(me);
                MessagesController.getInstance(currentAccount).putUser(me, false);
                userConfig.saveConfig(true);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_EMOJI_STATUS);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void applyTheme(JSONObject json) {
        try {
            JSONObject theme = json.optJSONObject("theme");
            if (theme == null) {
                return;
            }

            Theme.ThemeInfo dayTheme = null;
            if (theme.has("day")) {
                dayTheme = Theme.getTheme(theme.optString("day"));
                if (dayTheme != null && theme.has("dayAccent")) {
                    dayTheme.setCurrentAccentId(theme.optInt("dayAccent"));
                    Theme.saveThemeAccents(dayTheme, true, false, true, false);
                }
            }
            Theme.ThemeInfo nightTheme = null;
            if (theme.has("night")) {
                nightTheme = Theme.getTheme(theme.optString("night"));
                if (nightTheme != null) {
                    if (theme.has("nightAccent")) {
                        nightTheme.setCurrentAccentId(theme.optInt("nightAccent"));
                        Theme.saveThemeAccents(nightTheme, true, false, true, false);
                    }
                    Theme.setCurrentNightTheme(nightTheme);
                }
            }
            if (theme.has("autoNightType")) {
                Theme.selectedAutoNightType = theme.optInt("autoNightType");
            }
            Theme.saveAutoNightThemeConfig();

            boolean nightNow = theme.optBoolean("nightNow", false) && nightTheme != null;
            if (nightNow && dayTheme != null) {
                // Сначала сохраняем дневную тему, затем активируем ночную
                Theme.applyTheme(dayTheme, false);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, nightTheme, true, null, -1);
            } else if (nightNow) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, nightTheme, true, null, -1);
            } else if (dayTheme != null) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, dayTheme, false, null, -1);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void applyWallpaper(JSONObject json) {
        try {
            JSONObject wallpaper = json.optJSONObject("wallpaper");
            if (wallpaper == null) {
                return;
            }
            Theme.ThemeInfo activeTheme = Theme.getActiveTheme();
            if (activeTheme == null) {
                return;
            }
            Theme.OverrideWallpaperInfo info = new Theme.OverrideWallpaperInfo();
            info.slug = wallpaper.optString("slug", "");
            info.color = wallpaper.optInt("color", 0);
            info.gradientColor1 = wallpaper.optInt("gradient1", 0);
            info.gradientColor2 = wallpaper.optInt("gradient2", 0);
            info.gradientColor3 = wallpaper.optInt("gradient3", 0);
            info.rotation = wallpaper.optInt("rotation", 45);
            info.isBlurred = wallpaper.optBoolean("blur", false);
            info.isMotion = wallpaper.optBoolean("motion", false);
            info.intensity = (float) wallpaper.optDouble("intensity", 0.5f);
            activeTheme.setOverrideWallpaper(info);
            Theme.reloadWallpaper(true);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void applyChatSettings(JSONObject json) {
        try {
            JSONObject chat = json.optJSONObject("chat");
            if (chat == null) {
                return;
            }
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            SharedPreferences.Editor editor = preferences.edit();

            boolean fontChanged = false;
            if (chat.has("fontSize")) {
                int fontSize = chat.optInt("fontSize", 16);
                if (fontSize >= 12 && fontSize <= 30 && fontSize != SharedConfig.fontSize) {
                    SharedConfig.fontSize = fontSize;
                    SharedConfig.fontSizeIsDefault = false;
                    editor.putInt("fons_size", fontSize);
                    fontChanged = true;
                }
            }
            if (chat.has("bubbleRadius")) {
                int bubbleRadius = chat.optInt("bubbleRadius", 17);
                if (bubbleRadius >= 0 && bubbleRadius <= 30) {
                    SharedConfig.bubbleRadius = bubbleRadius;
                    editor.putInt("bubbleRadius", bubbleRadius);
                }
            }
            if (chat.has("bigEmoji")) {
                SharedConfig.allowBigEmoji = chat.optBoolean("bigEmoji");
                editor.putBoolean("allowBigEmoji", SharedConfig.allowBigEmoji);
            }
            if (chat.has("systemEmoji")) {
                SharedConfig.useSystemEmoji = chat.optBoolean("systemEmoji");
                editor.putBoolean("useSystemEmoji", SharedConfig.useSystemEmoji);
            }
            if (chat.has("systemBoldFont")) {
                SharedConfig.useSystemBoldFont = chat.optBoolean("systemBoldFont");
                editor.putBoolean("useSystemBoldFont", SharedConfig.useSystemBoldFont);
            }
            if (chat.has("animations")) {
                boolean animations = chat.optBoolean("animations");
                editor.putBoolean("view_animations", animations);
                SharedConfig.setAnimationsEnabled(animations);
            }
            if (chat.has("liteMode")) {
                LiteMode.setAllFlags(chat.optInt("liteMode"));
            }
            editor.apply();

            if (fontChanged) {
                Theme.createCommonMessageResources();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void applyIcon(JSONObject json) {
        try {
            if (!json.has("icon")) {
                return;
            }
            LauncherIconController.LauncherIcon icon = LauncherIconController.LauncherIcon.valueOf(json.optString("icon"));
            LauncherIconController.setIcon(icon);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
