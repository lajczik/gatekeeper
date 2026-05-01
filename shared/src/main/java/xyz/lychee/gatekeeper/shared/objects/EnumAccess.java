package xyz.lychee.gatekeeper.shared.objects;

import lombok.Getter;

@Getter
public enum EnumAccess {
    BLACKLIST(-1),
    NULL(0),
    WHITELIST(1);

    private final byte type;

    EnumAccess(int type) {
        this.type = (byte) type;
    }

    public static EnumAccess getByType(byte type) {
        for (EnumAccess access : EnumAccess.values()) {
            if (access.type == type) {
                return access;
            }
        }
        return NULL;
    }

    public boolean isEquals(byte type) {
        return this.type == type;
    }
}