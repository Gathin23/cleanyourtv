package io.github.vitorcamillo.wolfgames;

/** The only operations the session menu may perform. */
interface WolfControl {
    WolfSession getSession() throws WolfControlClient.ControlException;
    WolfSession stop(WolfSession expected) throws WolfControlClient.ControlException;
}
