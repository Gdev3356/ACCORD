package com.main.accord.permission;

public final class Permissions {
    private Permissions() {}

    public static final long VIEW_CHANNELS        = 1L;
    public static final long SEND_MESSAGES        = 1L << 1;
    public static final long SEND_TTS             = 1L << 2;
    public static final long MANAGE_MESSAGES      = 1L << 3;
    public static final long EMBED_LINKS          = 1L << 4;
    public static final long ATTACH_FILES         = 1L << 5;
    public static final long READ_MESSAGE_HISTORY = 1L << 6;
    public static final long MENTION_EVERYONE     = 1L << 7;
    public static final long USE_EXTERNAL_EMOJIS  = 1L << 8;
    public static final long CONNECT              = 1L << 9;
    public static final long SPEAK                = 1L << 10;
    public static final long MUTE_MEMBERS         = 1L << 11;
    public static final long DEAFEN_MEMBERS       = 1L << 12;
    public static final long MOVE_MEMBERS         = 1L << 13;
    public static final long KICK_MEMBERS         = 1L << 14;
    public static final long BAN_MEMBERS          = 1L << 15;
    public static final long MANAGE_CHANNELS      = 1L << 16;
    public static final long MANAGE_SERVER        = 1L << 17;
    public static final long ADMINISTRATOR        = 1L << 18;  // bypasses all overrides
}