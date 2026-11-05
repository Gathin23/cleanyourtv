package io.github.cleanyourtv.flock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FlockModelTest {
    private static final float WIDTH = 6400f;
    private static final float HEIGHT = 1600f;
    private static final float EPSILON = 0.001f;

    @Test
    public void countStaysWithinSupportedLimits() {
        FlockModel model = new FlockModel(84);

        model.setCount(0);
        assertEquals(24, model.size());
        model.setCount(999);
        assertEquals(180, model.size());
        model.setCount(96);
        assertEquals(96, model.size());
    }

    @Test
    public void cohesionSteersAnAgentTowardNearbyFlockmates() {
        FlockModel model = separatedModel();
        place(model, 0, 200f, 200f, 50f, 0f);
        place(model, 1, 300f, 200f, 50f, 0f);

        model.step(0.04f);

        assertTrue("cohesion should increase the agent's velocity toward its neighbor",
                model.boidAt(0).vx > 50f);
    }

    @Test
    public void separationMovesAgentsApartAndAlignmentSharesHeading() {
        FlockModel model = separatedModel();
        place(model, 0, 200f, 200f, 50f, 0f);
        place(model, 1, 210f, 200f, 0f, 50f);

        model.step(0.04f);

        assertTrue("separation should steer away from the close neighbor", model.boidAt(0).vx < 50f);
        assertTrue("alignment should turn toward the neighbor's upward heading", model.boidAt(0).vy > 0f);
    }

    @Test
    public void crossingAnEdgeWrapsToTheOppositeSide() {
        FlockModel model = separatedModel();
        place(model, 0, WIDTH - 1f, 200f, 100f, 0f);

        model.step(0.04f);

        assertTrue(model.boidAt(0).x >= 0f);
        assertTrue("the agent should wrap near the left edge", model.boidAt(0).x < 10f);
    }

    @Test
    public void agentsRemainFiniteBoundedAndWithinSpeedLimits() {
        FlockModel model = new FlockModel(84);
        model.resize(WIDTH, HEIGHT);

        for (int frame = 0; frame < 250; frame++) model.step(1f / 60f);

        for (int i = 0; i < model.size(); i++) {
            FlockModel.Boid boid = model.boidAt(i);
            assertTrue(Float.isFinite(boid.x));
            assertTrue(Float.isFinite(boid.y));
            assertTrue(Float.isFinite(boid.vx));
            assertTrue(Float.isFinite(boid.vy));
            assertTrue(boid.x >= 0f && boid.x < WIDTH);
            assertTrue(boid.y >= 0f && boid.y < HEIGHT);
            float speed = (float) Math.hypot(boid.vx, boid.vy);
            assertTrue(speed >= 48f - EPSILON);
            assertTrue(speed <= 112f + EPSILON);
        }
    }

    private static FlockModel separatedModel() {
        FlockModel model = new FlockModel(24);
        model.resize(WIDTH, HEIGHT);
        for (int i = 0; i < model.size(); i++) {
            place(model, i, 700f + (i - 2) * 250f, 1000f, 50f, 0f);
        }
        return model;
    }

    private static void place(FlockModel model, int index, float x, float y, float vx, float vy) {
        FlockModel.Boid boid = model.boidAt(index);
        boid.x = x;
        boid.y = y;
        boid.vx = vx;
        boid.vy = vy;
    }
}
