#import "../image_scale.typ": big_image_scale, image_scale, small_image_scale

= 程序架构设计与技术实现方案

== 总体技术方案的确定

== 运动生理学

本项目采用了若干成熟的运动生理学指标来衡量训练强度、估算恢复时长等。为此我阅读了大量运动生理学相关论文，得到了以下的计算公式。

首先是训练强度。单次动作的训练强度一般使用RPE系数（Rating of Perceived Exertion，主观疲劳感觉评分）来衡量。

#set table(stroke: (top: 0.5pt, bottom: 0.5pt, left: none, right: none))
#table(
  columns: (auto, auto, auto),
  align: (left, left, left),

  table.header([RPE分值], [主观描述], [补充说明]),

  [10], [最大努力，无法再完成一次], [做完该组后，完全力竭，即使再加1次也绝对无法完成],
  [9], [非常困难], [做完后感觉还能严格完成1次],
  [8], [困难], [做完后感觉还能再做2次],
  [7], [有些困难], [],
  [6], [中等偏上], [],
  [5], [中等], [],
  [4], [轻到中等], [],
  [3], [轻松], [做完后感觉还能再做7次以上，但仍有明显费力感],
  [2], [很轻松], [几乎不费力，可以做很多次],
  [1], [极轻松], [如热身或技术练习，基本无疲劳感],
  [0], [无任何感受], [静止状态，无运动],
)

一次完整训练的训练强度一般使用sRPE（session RPE）来衡量。

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  //   stroke: (x, y) => (
  //     if y == 0 and x == 0 { (top: 0.5pt, left: none, right: none, bottom: 0.5pt) }
  //     else if y == 0 { (bottom: 0.5pt, left: none, right: none) }
  //     else if y == 10 { (bottom: 0.5pt, left: none, right: none) }
  //     else { (left: none, right: none) }
  //   ),
  table.header([sRPE分值], [主观描述], [对应整节训练课的疲劳/负荷说明]),
  [10], [最大努力，极度疲劳], [彻底力竭，无法再做任何一组或多加一次动作；通常只出现在比赛或极限测试后],
  [9], [非常困难], [整节课极其吃力，结束时有明显想停止的感觉；适合赛前模拟或极限周],
  [8], [困难], [很累但能完成计划所有内容，课后需要充分恢复；常用于高强度周期],
  [7], [有些困难], [强度较高但可坚持，结束时有明显疲劳感但不至于崩溃；常见于增力期主课],
  [6], [中等偏上], [有一定挑战性，但能稳定完成计划，课后感觉“练到位了”],
  [5], [中等], [普通训练课，不轻松也不特别累，能较好完成所有内容],
  [4], [轻到中等], [感觉较为轻松，仍有训练效果，但疲劳感较低；适用于恢复日或技术日],
  [3], [轻松], [低强度训练，基本无累积疲劳；例如纯热身、灵活性训练或轻技术课],
  [2], [很轻松], [几乎无负担，可以连续多天进行相同训练],
  [1], [极轻松], [活动量极小，等同于日常活动或极短时间低强度练习],
  [0], [无任何感受], [没有训练，或训练完全无感（通常不会用于实际评分）],
)

借助sRPE就可以计算一次训练的训练强度：

$ "work load" = "sRPE" times "Duration time(in minute)" $

在项目中，每个单次训练和每一次完整训练都持久化记录了上述的各类数据。

== 人体、杠铃与动作建模

为了对动作进行分析，首先需要将视频中的人物转化为数学模型。一个很自然的方法是记录下若干重要部位、关节的坐标。通过这些数据即可计算各个关节的夹角等数据。对于举重来说，比较重要的有躯干角（脊椎-地面夹角）、臀位角（脊椎-股骨夹角）、膝角（股骨-胫骨夹角）和踝角（胫骨-足弓夹角）。

只要记录下各个坐标和时间的函数关系，就对动作做出了建模。但是在实际情况中，由于模型的识别精度和准确度偏差，这样得到的数据是带有噪声的，不便于后续的数据分析。

因此，我引入了Ramer–Douglas–Peucker算法来减少曲线上的数据点，从而避免出现过多特征干扰数据分析算法的正常工作。

```c
int DouglasPeucker(Point points[], int start, int end, double epsilon, Point result[]) {
    // Find point with maximum distance
    double dmax = 0;
    int index = start;

    for (int i = start + 1; i < end; i++) {
        double d = PerpendicularDistance(points[i], points[start], points[end]);
        if (d > dmax) {
            index = i;
            dmax = d;
        }
    }

    int resultCount = 0;

    // If max distance exceeds epsilon, recursively simplify
    if (dmax > epsilon) {
        int leftCount = DouglasPeucker(points, start, index, epsilon, result);
        int rightCount = DouglasPeucker(points, index, end, epsilon, result + leftCount);
        resultCount = leftCount + rightCount - 1;  // Remove duplicate point
    } else {
        result[resultCount++] = points[start];
        result[resultCount++] = points[end];
    }

    return resultCount;
}
```

其效果如图（背景浅色的是带噪声的原始数据）。

#figure(
  image("../assets/pcode.png", width: big_image_scale),
)

在实际代码中，我采用了Google的Pose Landmark模型#footnote[https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker]来识别人体。Pose Landmark有Lite、Heavy和Full三个模型。经过实际测试，我采用Full模型作为默认分析的模型，它同时保证了足够的精度和处理速度。

Pose Landmark提供了33个可用的节点，每个节点提供了$x$、$y$和$z$坐标（$z$是推算得到的，手机相机本身不具备深度感知能力），以及每个节点的confidence。

```text
0 - nose
...
11 - left shoulder
12 - right shoulder
13 - left elbow
14 - right elbow
15 - left wrist
16 - right wrist
...
23 - left hip
24 - right hip
25 - left knee
26 - right knee
27 - left ankle
28 - right ankle
29 - left heel
30 - right heel
31 - left foot index
32 - right foot index
```

#figure(
  image("../assets/poselandmark.jpg", width: small_image_scale),
  caption: "Pose Landmark识别实例",
)

== 持久化数据储存

本项目借助Room框架，使用Sqlite进行数据的持久化存储，包括运动动作、训练计划、训练记录，以及应用内信息的存储。数据表都是由我设计、ChatGPT优化改进得到的。各个表的结构如下所示。

```sql
-- 1. motion
CREATE TABLE motion (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    name TEXT NOT NULL
);
CREATE UNIQUE INDEX index_motion_name ON motion (name);
-- 2. plan
CREATE TABLE plan (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    name TEXT NOT NULL,
    cycle_period INTEGER NOT NULL,
    current_index INTEGER NOT NULL DEFAULT 0,
    last_applied_at INTEGER NOT NULL DEFAULT 0
);
-- 3. plan_selection
CREATE TABLE plan_selection (
    id INTEGER NOT NULL,
    current_plan_id INTEGER,
    current_day_epoch INTEGER,
    PRIMARY KEY(id),
    FOREIGN KEY(current_plan_id) REFERENCES plan(id) ON DELETE SET NULL
);
CREATE INDEX index_plan_selection_current_plan_id ON plan_selection (current_plan_id);
-- 4. workout_session
CREATE TABLE workout_session (
    id INTEGER NOT NULL,
    is_workout_going INTEGER NOT NULL DEFAULT 0,
    is_paused INTEGER NOT NULL DEFAULT 0,
    started_at INTEGER NOT NULL DEFAULT 0,
    last_resumed_at INTEGER NOT NULL DEFAULT 0,
    elapsed_before_pause_ms INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY(id)
);
-- 5. workout_progress
CREATE TABLE workout_progress (
    id INTEGER NOT NULL,
    plan_id INTEGER NOT NULL,
    plan_day_index INTEGER NOT NULL,
    next_set_index INTEGER NOT NULL DEFAULT 0,
    active_set_index INTEGER,
    total_set_count INTEGER NOT NULL,
    break_ends_at INTEGER NOT NULL DEFAULT 0,
    is_finished INTEGER NOT NULL DEFAULT 0,
    completed_elapsed_time_ms INTEGER NOT NULL DEFAULT 0,
    active_history_id INTEGER,
    workout_intensity INTEGER,
    PRIMARY KEY(id)
);
-- 6. metaplan
CREATE TABLE metaplan (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    plan_id INTEGER NOT NULL,
    motion_id INTEGER NOT NULL,
    day_index INTEGER NOT NULL,
    sets INTEGER NOT NULL,
    reps INTEGER NOT NULL,
    intensity REAL NOT NULL DEFAULT 0.0,
    weight REAL NOT NULL,
    order_index INTEGER NOT NULL,
    FOREIGN KEY(plan_id) REFERENCES plan(id) ON DELETE CASCADE,
    FOREIGN KEY(motion_id) REFERENCES motion(id) ON DELETE RESTRICT
);
CREATE INDEX index_metaplan_plan_id ON metaplan (plan_id);
CREATE INDEX index_metaplan_motion_id ON metaplan (motion_id);
CREATE UNIQUE INDEX index_metaplan_plan_id_day_index_order_index ON metaplan (plan_id, day_index, order_index);
-- 7. history
CREATE TABLE history (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    plan_id INTEGER NOT NULL,
    start_time INTEGER NOT NULL,
    end_time INTEGER NOT NULL,
    intensity INTEGER NOT NULL DEFAULT 0,
    day_index INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY(plan_id) REFERENCES plan(id) ON DELETE RESTRICT
);
CREATE INDEX index_history_plan_id ON history (plan_id);
-- 8. metahistory
CREATE TABLE metahistory (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    date TEXT NOT NULL,
    rep INTEGER NOT NULL,
    rpe INTEGER NOT NULL,
    weight REAL NOT NULL,
    motion_id INTEGER NOT NULL,
    video_name TEXT,
    video_source TEXT NOT NULL DEFAULT 'CAMERA_CAPTURE',
    imported_video_analysis_mode TEXT NOT NULL DEFAULT 'ESTIMATED',
    imported_reference_label TEXT NOT NULL DEFAULT '',
    imported_reference_pixel_distance REAL,
    imported_reference_distance_meters REAL,
    history_id INTEGER,
    FOREIGN KEY(motion_id) REFERENCES motion(id) ON DELETE RESTRICT,
    FOREIGN KEY(history_id) REFERENCES history(id) ON DELETE SET NULL
);
CREATE INDEX index_metahistory_motion_id ON metahistory (motion_id);
CREATE INDEX index_metahistory_history_id ON metahistory (history_id);
-- 9. video_process_state
CREATE TABLE video_process_state (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    video_name TEXT NOT NULL,
    state TEXT NOT NULL,
    progress INTEGER NOT NULL,
    processed_video_name TEXT
);
CREATE UNIQUE INDEX index_video_process_state_video_name ON video_process_state (video_name);
-- 10. metahistory_bin
CREATE TABLE metahistory_bin (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    date TEXT NOT NULL,
    rep INTEGER NOT NULL,
    rpe INTEGER NOT NULL,
    weight REAL NOT NULL,
    motion_id INTEGER NOT NULL,
    motion_name TEXT NOT NULL,
    video_name TEXT,
    video_source TEXT NOT NULL DEFAULT 'CAMERA_CAPTURE',
    imported_video_analysis_mode TEXT NOT NULL DEFAULT 'ESTIMATED',
    imported_reference_label TEXT NOT NULL DEFAULT '',
    imported_reference_pixel_distance REAL,
    imported_reference_distance_meters REAL,
    history_id INTEGER
);
```

本项目还设计了配套的DAO和Entity模型，以实现对数据增删查改的接口。

```text
├── training
│   └── data
│       ├── BodyMetricDao.kt
│       ├── HistoryDao.kt
│       ├── ImportedVideoRecords.kt
│       ├── LiftInsightDatabase.kt
│       ├── MotionDao.kt
│       ├── MotionStore.kt
│       ├── PlanDao.kt
│       ├── PlanStore.kt
│       ├── TrainingEntities.kt
│       └── TrainingRecords.kt
```

== Multi-Agent Workflow

本项目的代码是全部由AI完成的。项目的前期使用的是简单的单一agent工作模式。但是这就引入了需求描述不清导致最后生成的代码有误、反复测试或调整导致token消耗过多等问题。并且由于生成代码量太大，我难以全部人工审查，因此留下了很多不良或不规范的代码设计。

因此，在项目的中后期，我在opencode中引入了multi-agent workflow以提升工作效率、减少因描述不够详细造成AI的误解、降低token用量，我称之为devloop。

#figure(
  block(width: 100%, inset: 10pt, {
    set text(font: "Inter", size: 9pt)
    set align(center)
    text(size: 12pt, weight: "bold")[Devloop Multi-Agent Workflow]
    v(8pt)
    let node(label, color, body) = box(stroke: 1.2pt + color, radius: 6pt, inset: 6pt, fill: color.lighten(90%))[
      #text(weight: "bold")[#label] #h(4pt) #text(size: 8pt)[#body]
    ]
    node("User", rgb("#2563eb"), "Requirement")
    v(2pt)
    text(size: 11pt)[↓]
    v(2pt)
    node("Devloop", rgb("#0d9488"), "Orchestrator · Routes to agents")
    v(2pt)
    text(size: 11pt)[↓]
    v(2pt)
    node("1 · Planner", rgb("#16a34a"), "Questions · Constraints · Tasks")
    v(1pt)
    text(size: 7pt, style: "italic")[clarify with user if needed]
    v(1pt)
    text(size: 11pt)[↓]
    v(2pt)
    node("2 · Engineer", rgb("#9333ea"), "Implement · Verify · Test")
    v(2pt)
    text(size: 11pt)[↓]
    v(2pt)
    node("3 · Reviewer", rgb("#dc2626"), "Quality · Architecture · Fit")
    v(2pt)
    text(size: 11pt)[↓]
    v(4pt)
    grid(
      columns: (1fr, 1fr),
      column-gutter: 16pt,
      align(center, {
        box(stroke: 1pt + rgb("#ca8a04"), radius: 4pt, inset: 5pt, fill: rgb("#fefce8"))[
          #text(size: 8pt)[✗ Not approved]
        ]
        v(2pt)
        text(size: 7pt)[→ Devloop sends feedback \
          back to Engineer (max 3×)]
      }),
      align(center, {
        box(stroke: 1pt + rgb("#16a34a"), radius: 4pt, inset: 5pt, fill: rgb("#f0fdf4"))[
          #text(size: 8pt)[✓ Approved]
        ]
        v(2pt)
        text(size: 11pt)[↓]
        v(2pt)
        node("4 · Summarizer", rgb("#ea580c"), "Summary · Tradeoffs")
        v(2pt)
        text(size: 11pt)[↓]
        v(2pt)
        box(stroke: 1pt + gray, radius: 4pt, inset: 5pt)[
          #text(size: 8pt)[Final Response → User]
        ]
      }),
    )
  }),
  caption: [Devloop工作原理],
) <fig-workflow>

需要注意的是，除了图中的几个模型，实际上还有一个用于指挥的agent，我也称之为devloop。

agent的模型选择经历了多次变化。在最开始，我可用的模型是来自GitHub copilot的GPT5.4和deepseek v4，于是选择了GPT作为devloop、planner和engineer，deepseek pro作为reviewer和summarizer。

后来GPT的额度耗尽，同时我发现reviewer和summarizer的工作较为简单，于是为了降低成本、加快速度，改成了deepseek pro作为devloop、planner和engineer，deepseek flash作为reviewer和summarizer。

但是deepseek速度实在太慢，并且它作为planner对模糊需求的猜测和计划制定的能力并不足，因此我又购入了Xiaomi mimo 2.5，使用mimo2.5作为devloop和planner，deepseek pro作为engineer，deepseek flash作为reviewer和summarizer。这样效率和工作正确度就高了很多。之所以不用更聪明和便宜的mimo作为engineer，是因为engineer是token用量最大的地方，而当planner的计划描述足够清晰时，不同模型在代码质量上的差异就会很小，因此选用了deepseek进行这项工作。
