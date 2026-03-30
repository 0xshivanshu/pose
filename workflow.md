# Application Workflow

```mermaid
graph TD
    A[HomeActivity] -->|Start Workout| B[MainActivity]
    A -->|View Details| C[DailyReportActivity]
    A -->|Select Date| A
    
    B -->|Pose Estimation| D[PoseLandmarkerHelper]
    D -->|Landmarks| E[RepCounter]
    E -->|Visual Overlay| F[OverlayView]
    E -->|Count Reps| B
    E -->|Rest Gesture Detected| G[Finish Set]
    
    B -->|End Session| H[SummaryActivity]
    H -->|Save Data| I[WorkoutManager]
    I -->|Persist to Storage| J[(Local Files)]
    
    H -->|Return Home| A
    
    C -->|Load History| I
    A -->|Check Streak| I
```

### Key Components:
- **HomeActivity**: Entry point with streak tracking and historical reports.
- **MainActivity**: Real-time workout tracking using MediaPipe.
- **RepCounter**: Core logic for exercise detection (Bicep Curls, Squats) and rest gestures.
- **SummaryActivity**: Post-workout breakdown and heat-map visualization.
- **WorkoutManager**: Data persistence layer using file serialization.
- **MuscleHeatmapView**: Custom view for rendering anatomical muscle intensity.
