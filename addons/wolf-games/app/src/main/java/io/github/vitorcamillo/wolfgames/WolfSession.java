package io.github.vitorcamillo.wolfgames;

/** Validated state and launch decision. Never carries Wolf keys, certificates or raw records. */
final class WolfSession {
    final boolean active;
    final String appId, appName, revision;
    enum Decision { LAUNCH, RESUME_MENU, CONFIRM_SWITCH }

    WolfSession(boolean active, String appId, String appName, String revision) {
        if (active) {
            validateId(appId);
            if (appName == null || appName.isEmpty() || appName.length() > 120 || !appName.equals(appName.trim())) throw new IllegalArgumentException("invalid_session_name");
            for (int i=0;i<appName.length();i++) if (Character.isISOControl(appName.charAt(i))) throw new IllegalArgumentException("invalid_session_name");
            if (revision == null || !revision.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("invalid_session_revision");
        } else if (appId != null || appName != null || revision != null) throw new IllegalArgumentException("invalid_inactive_session");
        this.active=active;this.appId=appId;this.appName=appName;this.revision=revision;
    }
    static WolfSession inactive() { return new WolfSession(false,null,null,null); }
    Decision forTarget(String targetId) {
        validateId(targetId);
        return !active ? Decision.LAUNCH : appId.equals(targetId) ? Decision.RESUME_MENU : Decision.CONFIRM_SWITCH;
    }
    static void validateId(String id) {
        if (id == null || !id.matches("[1-9][0-9]{0,9}") || Long.parseLong(id)>Integer.MAX_VALUE) throw new IllegalArgumentException("invalid_session_id");
    }
}
