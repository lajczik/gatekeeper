package xyz.lychee.gatekeeper.shared.objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public abstract class CommandPlayer<T> {
    private final Object player;

    public void sendMessage(AbstractLang<T> lang, String key, String... placeholders) {
        T message;
        if (placeholders != null && placeholders.length > 0) {
            String str = lang.getString(key);
            if (str == null) return;

            for (String placeholder : placeholders) {
                str = str.replaceFirst("\\{}", placeholder);
            }

            message = lang.color(str, true);
        } else {
            message = lang.getComponents().get(key);
        }

        if (message == null) {
            throw new NullPointerException("Unknown language key: \"messages.no_permission");
        }

        this.sendMessage(message);
    }

    public abstract boolean hasPermission(String permission);

    public abstract void sendMessage(T message);

    public abstract void applyChange(String target, EnumAccess newAccess);
}
