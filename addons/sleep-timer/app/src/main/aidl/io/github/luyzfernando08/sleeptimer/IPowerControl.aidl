package io.github.luyzfernando08.sleeptimer;
interface IPowerControl {
    boolean setAwake(boolean awake) = 0;
    int uid() = 1;
    void destroy() = 16777114;
}
