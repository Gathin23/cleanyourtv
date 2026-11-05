package io.github.nickld.frigateglance;

/** What the card on screen can be told to do; OverlayCard draws it, tests record it. */
public interface CardView {
    void show(CardModel m);

    void update(CardModel m);

    void hide();
}
