package io.github.nickld.frigateglance;

import java.util.List;

/** Everything a card shows, apart from the picture. */
public final class CardModel {
    public final String reviewId, camera, title, detail;
    /** Normalised boxes, x1, y1, x2, y2. */
    public final List<float[]> boxes;
    /** 0 while the event goes on; once it has ended, the time left before the card leaves (Peek's timer bar). */
    public final long countdownMs;

    CardModel(String reviewId, String camera, String title, String detail, List<float[]> boxes, long countdownMs) {
        this.reviewId = reviewId;
        this.camera = camera;
        this.title = title;
        this.detail = detail;
        this.boxes = boxes;
        this.countdownMs = countdownMs;
    }
}
