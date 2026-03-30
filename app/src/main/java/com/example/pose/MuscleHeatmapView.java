package com.example.pose;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class MuscleHeatmapView extends View {

    public enum MuscleGroup {
        BICEPS, QUADS, GLUTES, HAMSTRINGS, ABS, CHEST, BACK, SHOULDERS, TRICEPS, CALVES
    }

    private final Map<MuscleGroup, Float> intensities = new HashMap<>();
    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint musclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint muscleOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public MuscleHeatmapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bodyPaint.setColor(Color.parseColor("#1AFFFFFF"));
        bodyPaint.setStyle(Paint.Style.FILL);

        strokePaint.setColor(Color.parseColor("#33FFFFFF"));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2f);

        musclePaint.setStyle(Paint.Style.FILL);

        muscleOutlinePaint.setStyle(Paint.Style.STROKE);
        muscleOutlinePaint.setStrokeWidth(1f);
        muscleOutlinePaint.setColor(Color.parseColor("#1AFFFFFF"));

        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(36f);
        labelPaint.setLetterSpacing(0.1f);
        labelPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        labelPaint.setTextAlign(Paint.Align.CENTER);

        for (MuscleGroup group : MuscleGroup.values()) {
            intensities.put(group, 0f);
        }
    }

    public void setIntensity(MuscleGroup group, float intensity) {
        intensities.put(group, Math.min(1f, intensity));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        float centerX = width / 2f;
        float bodyY = height * 0.55f;
        float bodyScale = height * 0.40f;

        // Draw Section Labels
        labelPaint.setAlpha(150);
        canvas.drawText("FRONT", centerX - width * 0.25f, 50, labelPaint);
        canvas.drawText("BACK", centerX + width * 0.25f, 50, labelPaint);

        // Draw Views
        drawAnatomicBody(canvas, centerX - width * 0.25f, bodyY, bodyScale, true);
        drawAnatomicBody(canvas, centerX + width * 0.25f, bodyY, bodyScale, false);
    }

    private void drawAnatomicBody(Canvas canvas, float x, float y, float scale, boolean isFront) {
        Path bodyPath = createDetailedBodyPath(x, y, scale);
        canvas.drawPath(bodyPath, bodyPaint);
        canvas.drawPath(bodyPath, strokePaint);

        if (isFront) {
            drawMuscleGroup(canvas, x, y, scale, MuscleGroup.CHEST, true);
            drawMuscleGroup(canvas, x, y, scale, MuscleGroup.ABS, true);
            drawMuscleGroup(canvas, x, y, scale, MuscleGroup.BICEPS, true);
            drawMuscleGroup(canvas, x, y, scale, MuscleGroup.SHOULDERS, true);
            drawMuscleGroup(canvas, x, y, scale, MuscleGroup.QUADS, true);
        } else {
            drawMuscleGroup(canvas, x, y, scale, MuscleGroup.BACK, false);
            drawMuscleGroup(canvas, x, y, scale, MuscleGroup.TRICEPS, false);
            drawMuscleGroup(canvas, x, y, scale, MuscleGroup.GLUTES, false);
            drawMuscleGroup(canvas, x, y, scale, MuscleGroup.HAMSTRINGS, false);
            drawMuscleGroup(canvas, x, y, scale, MuscleGroup.CALVES, false);
        }
    }

    private void drawMuscleGroup(Canvas canvas, float x, float y, float scale, MuscleGroup group, boolean isFront) {
        float intensity = intensities.getOrDefault(group, 0f);
        Path path = getMusclePath(x, y, scale, group, isFront);

        // Draw outline
        canvas.drawPath(path, muscleOutlinePaint);

        if (intensity > 0.01f) {
            int color = getHeatColor(intensity);
            musclePaint.setColor(color);
            musclePaint.setAlpha((int) (200 * intensity));
            canvas.drawPath(path, musclePaint);
        }
    }

    private Path getMusclePath(float x, float y, float scale, MuscleGroup group, boolean isFront) {
        Path p = new Path();
        switch (group) {
            case CHEST:
                // Left Chest
                p.addOval(x - scale * 0.18f, y - scale * 0.65f, x - scale * 0.02f, y - scale * 0.50f, Path.Direction.CW);
                // Right Chest
                p.addOval(x + scale * 0.02f, y - scale * 0.65f, x + scale * 0.18f, y - scale * 0.50f, Path.Direction.CW);
                break;
            case ABS:
                p.addRoundRect(x - scale * 0.12f, y - scale * 0.48f, x + scale * 0.12f, y - scale * 0.15f, scale * 0.05f, scale * 0.05f, Path.Direction.CW);
                break;
            case BICEPS:
                // Left
                p.addOval(x - scale * 0.38f, y - scale * 0.55f, x - scale * 0.28f, y - scale * 0.35f, Path.Direction.CW);
                // Right
                p.addOval(x + scale * 0.28f, y - scale * 0.55f, x + scale * 0.38f, y - scale * 0.35f, Path.Direction.CW);
                break;
            case SHOULDERS:
                // Left
                p.addOval(x - scale * 0.35f, y - scale * 0.72f, x - scale * 0.20f, y - scale * 0.58f, Path.Direction.CW);
                // Right
                p.addOval(x + scale * 0.20f, y - scale * 0.72f, x + scale * 0.35f, y - scale * 0.58f, Path.Direction.CW);
                break;
            case QUADS:
                // Left
                p.addOval(x - scale * 0.22f, y + scale * 0.15f, x - scale * 0.05f, y + scale * 0.55f, Path.Direction.CW);
                // Right
                p.addOval(x + scale * 0.05f, y + scale * 0.15f, x + scale * 0.22f, y + scale * 0.55f, Path.Direction.CW);
                break;
            case BACK:
                p.moveTo(x, y - scale * 0.70f);
                p.lineTo(x - scale * 0.25f, y - scale * 0.65f);
                p.lineTo(x - scale * 0.20f, y - scale * 0.30f);
                p.lineTo(x + scale * 0.20f, y - scale * 0.30f);
                p.lineTo(x + scale * 0.25f, y - scale * 0.65f);
                p.close();
                break;
            case TRICEPS:
                // Back view outer arms
                p.addOval(x - scale * 0.42f, y - scale * 0.55f, x - scale * 0.32f, y - scale * 0.35f, Path.Direction.CW);
                p.addOval(x + scale * 0.32f, y - scale * 0.55f, x + scale * 0.42f, y - scale * 0.35f, Path.Direction.CW);
                break;
            case GLUTES:
                p.addOval(x - scale * 0.22f, y - scale * 0.10f, x - scale * 0.02f, y + scale * 0.15f, Path.Direction.CW);
                p.addOval(x + scale * 0.02f, y - scale * 0.10f, x + scale * 0.22f, y + scale * 0.15f, Path.Direction.CW);
                break;
            case HAMSTRINGS:
                p.addOval(x - scale * 0.20f, y + scale * 0.20f, x - scale * 0.05f, y + scale * 0.55f, Path.Direction.CW);
                p.addOval(x + scale * 0.05f, y + scale * 0.20f, x + scale * 0.20f, y + scale * 0.55f, Path.Direction.CW);
                break;
            case CALVES:
                p.addOval(x - scale * 0.18f, y + scale * 0.65f, x - scale * 0.06f, y + scale * 0.88f, Path.Direction.CW);
                p.addOval(x + scale * 0.06f, y + scale * 0.65f, x + scale * 0.18f, y + scale * 0.88f, Path.Direction.CW);
                break;
        }
        return p;
    }

    private Path createDetailedBodyPath(float x, float y, float scale) {
        Path path = new Path();

        // ── Head ──────────────────────────────────────────────────────────────
        path.addCircle(x, y - scale * 0.88f, scale * 0.12f, Path.Direction.CW);

        // ── Neck ──────────────────────────────────────────────────────────────
        // Neck base: narrow column connecting head to shoulders
        // (incorporated into the torso outline below via cubics)

        // ── Torso + Arms + Legs (single closed silhouette) ────────────────────
        // Start at left base of neck
        path.moveTo(x - scale * 0.07f, y - scale * 0.75f);

        // === LEFT SIDE ===

        // Neck → left shoulder (trapezius slope, cubic)
        path.cubicTo(
                x - scale * 0.10f, y - scale * 0.76f,   // ctrl1: neck-shoulder transition
                x - scale * 0.22f, y - scale * 0.76f,   // ctrl2: shoulder rise
                x - scale * 0.30f, y - scale * 0.72f    // left shoulder top
        );

        // Shoulder cap → upper arm outer (deltoid bulge)
        path.cubicTo(
                x - scale * 0.36f, y - scale * 0.70f,   // ctrl1: shoulder outer
                x - scale * 0.40f, y - scale * 0.65f,   // ctrl2: deltoid peak
                x - scale * 0.38f, y - scale * 0.58f    // upper arm start
        );

        // Upper arm outer (slight taper toward elbow)
        path.cubicTo(
                x - scale * 0.39f, y - scale * 0.50f,   // ctrl1
                x - scale * 0.37f, y - scale * 0.42f,   // ctrl2
                x - scale * 0.35f, y - scale * 0.35f    // elbow
        );

        // Elbow → forearm outer (narrowing)
        path.cubicTo(
                x - scale * 0.34f, y - scale * 0.28f,   // ctrl1
                x - scale * 0.32f, y - scale * 0.20f,   // ctrl2
                x - scale * 0.30f, y - scale * 0.12f    // wrist outer
        );

        // Wrist → hand (small stub)
        path.cubicTo(
                x - scale * 0.30f, y - scale * 0.08f,   // ctrl1
                x - scale * 0.29f, y - scale * 0.05f,   // ctrl2
                x - scale * 0.28f, y - scale * 0.03f    // hand bottom-outer
        );

        // Hand bottom → hand inner (rounded fingertip area)
        path.cubicTo(
                x - scale * 0.29f, y - scale * 0.01f,   // ctrl1
                x - scale * 0.27f, y + scale * 0.01f,   // ctrl2
                x - scale * 0.26f, y - scale * 0.00f    // hand inner
        );

        // Wrist inner → forearm inner
        path.cubicTo(
                x - scale * 0.25f, y - scale * 0.04f,   // ctrl1
                x - scale * 0.26f, y - scale * 0.12f,   // ctrl2
                x - scale * 0.27f, y - scale * 0.20f    // forearm inner mid
        );

        // Forearm inner → elbow inner
        path.cubicTo(
                x - scale * 0.27f, y - scale * 0.28f,   // ctrl1
                x - scale * 0.27f, y - scale * 0.33f,   // ctrl2
                x - scale * 0.27f, y - scale * 0.35f    // elbow inner
        );

        // Elbow inner → armpit (arm rejoins torso)
        path.cubicTo(
                x - scale * 0.27f, y - scale * 0.42f,   // ctrl1
                x - scale * 0.26f, y - scale * 0.52f,   // ctrl2
                x - scale * 0.24f, y - scale * 0.58f    // armpit
        );

        // Armpit → left torso side (lat flare then waist taper)
        path.cubicTo(
                x - scale * 0.26f, y - scale * 0.52f,   // ctrl1: lat width
                x - scale * 0.27f, y - scale * 0.40f,   // ctrl2: lat lower
                x - scale * 0.25f, y - scale * 0.28f    // lower lat
        );

        // Waist indent (left)
        path.cubicTo(
                x - scale * 0.24f, y - scale * 0.18f,   // ctrl1
                x - scale * 0.20f, y - scale * 0.10f,   // ctrl2: waist pinch
                x - scale * 0.22f, y - scale * 0.00f    // hip flare
        );

        // Hip flare → left thigh outer
        path.cubicTo(
                x - scale * 0.24f, y + scale * 0.08f,   // ctrl1: hip width
                x - scale * 0.24f, y + scale * 0.12f,   // ctrl2
                x - scale * 0.22f, y + scale * 0.15f    // upper thigh outer
        );

        // Left thigh outer (quad sweep)
        path.cubicTo(
                x - scale * 0.23f, y + scale * 0.28f,   // ctrl1
                x - scale * 0.22f, y + scale * 0.42f,   // ctrl2
                x - scale * 0.20f, y + scale * 0.55f    // knee outer
        );

        // Knee outer → calf outer
        path.cubicTo(
                x - scale * 0.20f, y + scale * 0.60f,   // ctrl1
                x - scale * 0.19f, y + scale * 0.63f,   // ctrl2
                x - scale * 0.18f, y + scale * 0.65f    // calf outer top
        );

        // Calf bulge (left outer)
        path.cubicTo(
                x - scale * 0.19f, y + scale * 0.72f,   // ctrl1: calf peak
                x - scale * 0.18f, y + scale * 0.80f,   // ctrl2
                x - scale * 0.15f, y + scale * 0.88f    // ankle outer
        );

        // Ankle outer → foot outer
        path.cubicTo(
                x - scale * 0.14f, y + scale * 0.92f,   // ctrl1
                x - scale * 0.14f, y + scale * 0.95f,   // ctrl2
                x - scale * 0.13f, y + scale * 0.96f    // foot outer
        );

        // Foot sole (left)
        path.cubicTo(
                x - scale * 0.12f, y + scale * 0.97f,
                x - scale * 0.08f, y + scale * 0.97f,
                x - scale * 0.05f, y + scale * 0.96f    // foot inner
        );

        // Ankle inner → calf inner
        path.cubicTo(
                x - scale * 0.05f, y + scale * 0.92f,   // ctrl1
                x - scale * 0.06f, y + scale * 0.84f,   // ctrl2
                x - scale * 0.07f, y + scale * 0.76f    // calf inner mid
        );

        // Calf inner → back of knee
        path.cubicTo(
                x - scale * 0.07f, y + scale * 0.68f,   // ctrl1
                x - scale * 0.07f, y + scale * 0.62f,   // ctrl2
                x - scale * 0.07f, y + scale * 0.58f    // knee inner
        );

        // Knee inner → inner thigh
        path.cubicTo(
                x - scale * 0.07f, y + scale * 0.48f,   // ctrl1
                x - scale * 0.06f, y + scale * 0.30f,   // ctrl2
                x - scale * 0.05f, y + scale * 0.15f    // upper inner thigh L
        );

        // Inner thigh → crotch (left)
        path.cubicTo(
                x - scale * 0.05f, y + scale * 0.10f,
                x - scale * 0.03f, y + scale * 0.06f,
                x,                  y + scale * 0.05f    // crotch centre
        );

        // === RIGHT SIDE (mirror) ===

        // Crotch → right inner thigh
        path.cubicTo(
                x + scale * 0.03f, y + scale * 0.06f,
                x + scale * 0.05f, y + scale * 0.10f,
                x + scale * 0.05f, y + scale * 0.15f    // upper inner thigh R
        );

        // Inner thigh → knee inner
        path.cubicTo(
                x + scale * 0.06f, y + scale * 0.30f,
                x + scale * 0.07f, y + scale * 0.48f,
                x + scale * 0.07f, y + scale * 0.58f    // knee inner R
        );

        // Knee inner → calf inner
        path.cubicTo(
                x + scale * 0.07f, y + scale * 0.62f,
                x + scale * 0.07f, y + scale * 0.68f,
                x + scale * 0.07f, y + scale * 0.76f    // calf inner mid R
        );

        // Calf inner → ankle inner
        path.cubicTo(
                x + scale * 0.06f, y + scale * 0.84f,
                x + scale * 0.05f, y + scale * 0.92f,
                x + scale * 0.05f, y + scale * 0.96f    // foot inner R
        );

        // Foot sole (right)
        path.cubicTo(
                x + scale * 0.08f, y + scale * 0.97f,
                x + scale * 0.12f, y + scale * 0.97f,
                x + scale * 0.13f, y + scale * 0.96f    // foot outer R
        );

        // Ankle outer
        path.cubicTo(
                x + scale * 0.14f, y + scale * 0.95f,
                x + scale * 0.14f, y + scale * 0.92f,
                x + scale * 0.15f, y + scale * 0.88f    // ankle outer R
        );

        // Calf bulge (right outer)
        path.cubicTo(
                x + scale * 0.18f, y + scale * 0.80f,
                x + scale * 0.19f, y + scale * 0.72f,
                x + scale * 0.18f, y + scale * 0.65f    // calf outer top R
        );

        // Calf top → knee outer
        path.cubicTo(
                x + scale * 0.19f, y + scale * 0.63f,
                x + scale * 0.20f, y + scale * 0.60f,
                x + scale * 0.20f, y + scale * 0.55f    // knee outer R
        );

        // Knee → thigh outer
        path.cubicTo(
                x + scale * 0.22f, y + scale * 0.42f,
                x + scale * 0.23f, y + scale * 0.28f,
                x + scale * 0.22f, y + scale * 0.15f    // upper thigh outer R
        );

        // Thigh → hip flare R
        path.cubicTo(
                x + scale * 0.24f, y + scale * 0.12f,
                x + scale * 0.24f, y + scale * 0.08f,
                x + scale * 0.22f, y - scale * 0.00f    // hip R
        );

        // Hip → waist R
        path.cubicTo(
                x + scale * 0.20f, y - scale * 0.10f,
                x + scale * 0.24f, y - scale * 0.18f,
                x + scale * 0.25f, y - scale * 0.28f    // lower lat R
        );

        // Lat → armpit R
        path.cubicTo(
                x + scale * 0.27f, y - scale * 0.40f,
                x + scale * 0.26f, y - scale * 0.52f,
                x + scale * 0.24f, y - scale * 0.58f    // armpit R
        );

        // Armpit → elbow inner R
        path.cubicTo(
                x + scale * 0.26f, y - scale * 0.52f,
                x + scale * 0.27f, y - scale * 0.42f,
                x + scale * 0.27f, y - scale * 0.35f    // elbow inner R
        );

        // Elbow inner → forearm inner R
        path.cubicTo(
                x + scale * 0.27f, y - scale * 0.33f,
                x + scale * 0.27f, y - scale * 0.28f,
                x + scale * 0.27f, y - scale * 0.20f    // forearm inner R
        );

        // Forearm inner → wrist inner R
        path.cubicTo(
                x + scale * 0.26f, y - scale * 0.12f,
                x + scale * 0.25f, y - scale * 0.04f,
                x + scale * 0.26f, y - scale * 0.00f    // hand inner R
        );

        // Hand inner → hand outer R
        path.cubicTo(
                x + scale * 0.27f, y + scale * 0.01f,
                x + scale * 0.29f, y - scale * 0.01f,
                x + scale * 0.28f, y - scale * 0.03f    // hand outer R
        );

        // Hand → wrist outer R
        path.cubicTo(
                x + scale * 0.29f, y - scale * 0.05f,
                x + scale * 0.30f, y - scale * 0.08f,
                x + scale * 0.30f, y - scale * 0.12f    // wrist outer R
        );

        // Forearm outer → elbow R
        path.cubicTo(
                x + scale * 0.32f, y - scale * 0.20f,
                x + scale * 0.34f, y - scale * 0.28f,
                x + scale * 0.35f, y - scale * 0.35f    // elbow R
        );

        // Elbow → upper arm outer R
        path.cubicTo(
                x + scale * 0.37f, y - scale * 0.42f,
                x + scale * 0.39f, y - scale * 0.50f,
                x + scale * 0.38f, y - scale * 0.58f    // upper arm R
        );

        // Upper arm → shoulder cap R
        path.cubicTo(
                x + scale * 0.40f, y - scale * 0.65f,
                x + scale * 0.36f, y - scale * 0.70f,
                x + scale * 0.30f, y - scale * 0.72f    // shoulder top R
        );

        // Shoulder → neck R
        path.cubicTo(
                x + scale * 0.22f, y - scale * 0.76f,
                x + scale * 0.10f, y - scale * 0.76f,
                x + scale * 0.07f, y - scale * 0.75f    // right base of neck
        );

        // Neck right → neck left (top of torso, slight curve for neck column)
        path.cubicTo(
                x + scale * 0.05f, y - scale * 0.78f,
                x - scale * 0.05f, y - scale * 0.78f,
                x - scale * 0.07f, y - scale * 0.75f    // back to start
        );

        path.close();
        return path;
    }

    private int getHeatColor(float intensity) {
        // Professional "Burn" palette: Yellow -> Orange -> Red
        if (intensity < 0.3f) {
            return Color.rgb(255, 235, 59); // Yellow
        } else if (intensity < 0.7f) {
            return Color.rgb(255, 152, 0); // Orange
        } else {
            return Color.rgb(244, 67, 54); // Red
        }
    }
}