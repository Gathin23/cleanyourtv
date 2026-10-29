package io.github.cleanyourtv.flock;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Lightweight Reynolds-style boids simulation with wraparound edges. */
final class FlockModel {
    private static final float MIN_SPEED = 48f;
    private static final float MAX_SPEED = 112f;
    private static final float SEPARATION_WEIGHT = 1.65f;
    private static final float ALIGNMENT_WEIGHT = 0.95f;
    private static final float COHESION_WEIGHT = 0.8f;

    private final Random random = new Random();
    private final List<Boid> boids = new ArrayList<>();
    private float width;
    private float height;

    FlockModel(int count) {
        setCount(count);
    }

    int size() {
        return boids.size();
    }

    Boid boidAt(int index) {
        return boids.get(index);
    }

    void resize(float width, float height) {
        boolean firstLayout = this.width <= 1f && this.height <= 1f;
        this.width = Math.max(1f, width);
        this.height = Math.max(1f, height);
        for (Boid boid : boids) {
            if (firstLayout) {
                boid.x = random.nextFloat() * this.width;
                boid.y = random.nextFloat() * this.height;
            } else {
                boid.x = wrap(boid.x, this.width);
                boid.y = wrap(boid.y, this.height);
            }
        }
    }

    void setCount(int count) {
        count = Math.max(24, Math.min(180, count));
        while (boids.size() > count) boids.remove(boids.size() - 1);
        while (boids.size() < count) {
            float x = random.nextFloat() * Math.max(1f, width);
            float y = random.nextFloat() * Math.max(1f, height);
            float angle = random.nextFloat() * (float) (Math.PI * 2);
            float speed = MIN_SPEED + random.nextFloat() * (MAX_SPEED - MIN_SPEED);
            boids.add(new Boid(x, y, (float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed));
        }
    }

    void step(float elapsedSeconds) {
        if (boids.isEmpty() || width <= 1f || height <= 1f) return;
        float dt = Math.min(0.04f, Math.max(0f, elapsedSeconds));
        if (dt == 0f) return;

        int count = boids.size();
        float[] x = new float[count], y = new float[count], vx = new float[count], vy = new float[count];
        for (int i = 0; i < count; i++) {
            Boid boid = boids.get(i);
            x[i] = boid.x;
            y[i] = boid.y;
            vx[i] = boid.vx;
            vy[i] = boid.vy;
        }

        float neighborRadius = Math.min(width, height) * 0.15f;
        float neighborRadiusSq = neighborRadius * neighborRadius;
        float separationRadius = neighborRadius * 0.34f;
        float separationRadiusSq = separationRadius * separationRadius;
        float[] acceleration = new float[2];
        for (int i = 0; i < count; i++) {
            float centerX = 0f, centerY = 0f, headingX = 0f, headingY = 0f;
            float separateX = 0f, separateY = 0f;
            int neighbors = 0, closeNeighbors = 0;
            for (int j = 0; j < count; j++) {
                if (i == j) continue;
                float dx = shortestDelta(x[j] - x[i], width);
                float dy = shortestDelta(y[j] - y[i], height);
                float distanceSq = dx * dx + dy * dy;
                if (distanceSq > neighborRadiusSq || distanceSq < 0.0001f) continue;
                centerX += dx;
                centerY += dy;
                headingX += vx[j];
                headingY += vy[j];
                neighbors++;
                if (distanceSq < separationRadiusSq) {
                    float distance = (float) Math.sqrt(distanceSq);
                    separateX -= dx / distance;
                    separateY -= dy / distance;
                    closeNeighbors++;
                }
            }

            acceleration[0] = 0f;
            acceleration[1] = 0f;
            if (neighbors > 0) {
                // Cohesion steers toward the local centre; alignment matches nearby heading.
                addSteering(centerX / neighbors, centerY / neighbors, vx[i], vy[i], COHESION_WEIGHT, acceleration);
                addSteering(headingX / neighbors - vx[i], headingY / neighbors - vy[i], vx[i], vy[i],
                        ALIGNMENT_WEIGHT, acceleration);
            }
            if (closeNeighbors > 0) {
                addSteering(separateX / closeNeighbors, separateY / closeNeighbors, vx[i], vy[i],
                        SEPARATION_WEIGHT, acceleration);
            }

            Boid boid = boids.get(i);
            float nextVx = vx[i] + acceleration[0] * dt;
            float nextVy = vy[i] + acceleration[1] * dt;
            float speed = length(nextVx, nextVy);
            if (speed < 0.001f) {
                nextVx = vx[i];
                nextVy = vy[i];
                speed = length(nextVx, nextVy);
            }
            float targetSpeed = Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
            boid.vx = nextVx / speed * targetSpeed;
            boid.vy = nextVy / speed * targetSpeed;
            boid.x = wrap(x[i] + boid.vx * dt, width);
            boid.y = wrap(y[i] + boid.vy * dt, height);
        }
    }

    private static void addSteering(float desiredX, float desiredY, float vx, float vy,
                                    float weight, float[] acceleration) {
        float magnitude = length(desiredX, desiredY);
        if (magnitude < 0.001f) return;
        float targetX = desiredX / magnitude * MAX_SPEED;
        float targetY = desiredY / magnitude * MAX_SPEED;
        float steerX = targetX - vx;
        float steerY = targetY - vy;
        float steerLength = length(steerX, steerY);
        float maxForce = MAX_SPEED * 0.8f;
        if (steerLength > maxForce) {
            steerX = steerX / steerLength * maxForce;
            steerY = steerY / steerLength * maxForce;
        }
        acceleration[0] += steerX * weight;
        acceleration[1] += steerY * weight;
    }

    private static float length(float x, float y) {
        return (float) Math.sqrt(x * x + y * y);
    }

    private static float shortestDelta(float delta, float extent) {
        if (delta > extent / 2f) return delta - extent;
        if (delta < -extent / 2f) return delta + extent;
        return delta;
    }

    private static float wrap(float value, float extent) {
        value %= extent;
        return value < 0f ? value + extent : value;
    }

    static final class Boid {
        float x, y, vx, vy;

        Boid(float x, float y, float vx, float vy) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
        }
    }
}
